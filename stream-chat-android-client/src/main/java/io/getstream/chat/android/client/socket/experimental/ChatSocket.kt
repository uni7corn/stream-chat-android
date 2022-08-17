/*
 * Copyright (c) 2014-2022 Stream.io Inc. All rights reserved.
 *
 * Licensed under the Stream License;
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://github.com/GetStream/stream-chat-android/blob/main/LICENSE
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.getstream.chat.android.client.socket.experimental

import io.getstream.chat.android.client.LifecycleHandler
import io.getstream.chat.android.client.StreamLifecycleObserver
import io.getstream.chat.android.client.clientstate.DisconnectCause
import io.getstream.chat.android.client.errors.ChatError
import io.getstream.chat.android.client.errors.ChatErrorCode
import io.getstream.chat.android.client.errors.ChatNetworkError
import io.getstream.chat.android.client.events.ChatEvent
import io.getstream.chat.android.client.events.ConnectedEvent
import io.getstream.chat.android.client.events.HealthEvent
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.client.network.NetworkStateProvider
import io.getstream.chat.android.client.socket.HealthMonitor
import io.getstream.chat.android.client.socket.SocketFactory
import io.getstream.chat.android.client.socket.SocketListener
import io.getstream.chat.android.client.socket.experimental.ChatSocketStateService.State
import io.getstream.chat.android.client.socket.experimental.ws.StreamWebSocket
import io.getstream.chat.android.client.socket.experimental.ws.StreamWebSocketEvent
import io.getstream.chat.android.client.token.TokenManager
import io.getstream.chat.android.client.utils.stringify
import io.getstream.chat.android.core.internal.coroutines.DispatcherProvider
import io.getstream.logging.StreamLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions", "LongParameterList")
internal class ChatSocket private constructor(
    private val apiKey: String,
    private val wssUrl: String,
    private val tokenManager: TokenManager,
    private val socketFactory: SocketFactory,
    private val coroutineScope: CoroutineScope,
    private val lifecycleObserver: StreamLifecycleObserver,
    private val networkStateProvider: NetworkStateProvider,
) {
    private var streamWebSocket: StreamWebSocket? = null
    private val logger = StreamLog.getLogger("Chat:Socket")
    private var connectionConf: SocketFactory.ConnectionConf? = null
    private val listeners = mutableSetOf<SocketListener>()
    private val chatSocketStateService = ChatSocketStateService()
    private var socketStateObserverJob: Job? = null
    private val healthMonitor = HealthMonitor(
        coroutineScope = coroutineScope,
        checkCallback = { (chatSocketStateService.currentState as? State.Connected)?.event?.let(::sendEvent) },
        reconnectCallback = { chatSocketStateService.onWebSocketEventLost() }
    )
    private val lifecycleHandler = object : LifecycleHandler {
        override fun resume() { chatSocketStateService.onResume() }
        override fun stopped() { chatSocketStateService.onStop() }
    }
    private val networkStateListener = object : NetworkStateProvider.NetworkStateListener {
        override fun onConnected() { chatSocketStateService.onNetworkAvailable() }
        override fun onDisconnected() { chatSocketStateService.onNetworkNotAvailable() }
    }

    @Suppress("ComplexMethod")
    private fun observeSocketStateService(): Job {
        var socketListenerJob: Job? = null

        fun connectUser(connectionConf: SocketFactory.ConnectionConf) {
            coroutineScope.launch { startObservers() }
            this.connectionConf = connectionConf
            socketListenerJob?.cancel()
            when (networkStateProvider.isConnected()) {
                true -> {
                    streamWebSocket = socketFactory.createSocket(connectionConf).apply {
                        socketListenerJob = listen().onEach {
                            when (it) {
                                is StreamWebSocketEvent.Error -> handleError(it.chatError)
                                is StreamWebSocketEvent.Message -> handleEvent(it.chatEvent)
                            }
                        }.launchIn(coroutineScope)
                    }
                }
                false -> chatSocketStateService.onNetworkNotAvailable()
            }
        }

        fun reconnect(connectionConf: SocketFactory.ConnectionConf) {
            connectUser(connectionConf.asReconnectionConf())
        }

        return coroutineScope.launch {
            chatSocketStateService.observer { state ->
                when (state) {
                    is State.RestartConnection -> {
                        connectionConf?.let { chatSocketStateService.onReconnect(it) }
                    }
                    is State.Connected -> {
                        healthMonitor.ack()
                        callListeners { listener -> listener.onConnected(state.event) }
                    }
                    is State.Connecting -> {
                        callListeners { listener -> listener.onConnecting() }
                        when (state.isReconnection) {
                            true -> reconnect(state.connectionConf.asReconnectionConf())
                            false -> connectUser(state.connectionConf)
                        }
                    }
                    is State.Disconnected -> {
                        val disconnectCause: DisconnectCause = when (state) {
                            is State.Disconnected.DisconnectedByRequest -> {
                                healthMonitor.stop()
                                coroutineScope.launch { disposeObservers() }
                                streamWebSocket?.close()
                                DisconnectCause.ConnectionReleased
                            }
                            is State.Disconnected.NetworkDisconnected -> {
                                healthMonitor.stop()
                                DisconnectCause.NetworkNotAvailable
                            }
                            is State.Disconnected.Stopped -> {
                                healthMonitor.stop()
                                disposeNetworkStateObserver()
                                DisconnectCause.ConnectionReleased
                            }
                            is State.Disconnected.DisconnectedPermanently -> {
                                healthMonitor.stop()
                                coroutineScope.launch { disposeObservers() }
                                DisconnectCause.UnrecoverableError(state.error)
                            }
                            is State.Disconnected.DisconnectedTemporarily -> {
                                healthMonitor.onDisconnected()
                                DisconnectCause.Error(state.error)
                            }
                            is State.Disconnected.WebSocketEventLost -> {
                                connectionConf?.let { chatSocketStateService.onReconnect(it) }
                                DisconnectCause.WebSocketNotAvailable
                            }
                        }
                        callListeners { listener -> listener.onDisconnected(cause = disconnectCause) }
                    }
                }
            }
        }
    }

    fun connectUser(user: User, isAnonymous: Boolean) {
        socketStateObserverJob?.cancel()
        socketStateObserverJob = observeSocketStateService()
        chatSocketStateService.onConnect(
            when (isAnonymous) {
                true -> SocketFactory.ConnectionConf.AnonymousConnectionConf(wssUrl, apiKey, user)
                false -> SocketFactory.ConnectionConf.UserConnectionConf(wssUrl, apiKey, user)
            }
        )
    }

    fun disconnect() {
        connectionConf = null
        chatSocketStateService.onRequiredDisconnect()
    }

    private fun handleEvent(chatEvent: ChatEvent) {
        when (chatEvent) {
            is ConnectedEvent -> chatSocketStateService.onConnectionEstablished(chatEvent)
            is HealthEvent -> healthMonitor.ack()
            else -> callListeners { listener -> listener.onEvent(chatEvent) }
        }
    }

    private suspend fun startObservers() {
        lifecycleObserver.observe(lifecycleHandler)
        networkStateProvider.subscribe(networkStateListener)
    }

    private suspend fun disposeObservers() {
        lifecycleObserver.dispose(lifecycleHandler)
        disposeNetworkStateObserver()
    }

    private fun disposeNetworkStateObserver() {
        networkStateProvider.unsubscribe(networkStateListener)
    }

    private fun handleError(error: ChatError) {
        logger.e { error.stringify() }
        when (error) {
            is ChatNetworkError -> onChatNetworkError(error)
            else -> callListeners { it.onError(error) }
        }
    }

    private fun onChatNetworkError(error: ChatNetworkError) {
        if (ChatErrorCode.isAuthenticationError(error.streamCode)) {
            tokenManager.expireToken()
        }

        when (error.streamCode) {
            ChatErrorCode.UNDEFINED_TOKEN.code,
            ChatErrorCode.INVALID_TOKEN.code,
            ChatErrorCode.API_KEY_NOT_FOUND.code,
            ChatErrorCode.VALIDATION_ERROR.code,
            -> {
                logger.d {
                    "One unrecoverable error happened. Error: ${error.stringify()}. Error code: ${error.streamCode}"
                }
                chatSocketStateService.onUnrecoverableError(error)
            }
            else -> chatSocketStateService.onNetworkError(error)
        }
    }

    fun removeListener(listener: SocketListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun addListener(listener: SocketListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    /**
     * Attempt to send [event] to the web socket connection.
     * Returns true only if socket is connected and [okhttp3.WebSocket.send] returns true, otherwise false
     *
     * @see [okhttp3.WebSocket.send]
     */
    internal fun sendEvent(event: ChatEvent): Boolean = streamWebSocket?.send(event) ?: false

    internal fun isConnected(): Boolean = chatSocketStateService.currentState is State.Connected

    /**
     * Get connection id of this connection.
     */
    internal fun connectionIdOrError(): String = when (val state = chatSocketStateService.currentState) {
        is State.Connected -> state.event.connectionId
        else -> error("This state doesn't contain connectionId")
    }

    fun reconnectUser(user: User, isAnonymous: Boolean) {
        chatSocketStateService.onReconnect(
            when (isAnonymous) {
                true -> SocketFactory.ConnectionConf.AnonymousConnectionConf(wssUrl, apiKey, user)
                false -> SocketFactory.ConnectionConf.UserConnectionConf(wssUrl, apiKey, user)
            }
        )
    }

    private fun callListeners(call: (SocketListener) -> Unit) {
        synchronized(listeners) {
            listeners.forEach { listener ->
                coroutineScope.launch(DispatcherProvider.Main) { call(listener) }
            }
        }
    }

    companion object {

        fun create(
            apiKey: String,
            wssUrl: String,
            tokenManager: TokenManager,
            socketFactory: SocketFactory,
            coroutineScope: CoroutineScope,
            lifecycleObserver: StreamLifecycleObserver,
            networkStateProvider: NetworkStateProvider,
        ): ChatSocket =
            ChatSocket(
                apiKey,
                wssUrl,
                tokenManager,
                socketFactory,
                coroutineScope,
                lifecycleObserver,
                networkStateProvider,
            )
    }
}
