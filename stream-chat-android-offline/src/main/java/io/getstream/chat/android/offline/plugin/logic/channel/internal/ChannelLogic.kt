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

package io.getstream.chat.android.offline.plugin.logic.channel.internal

import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.api.models.Pagination
import io.getstream.chat.android.client.api.models.QueryChannelRequest
import io.getstream.chat.android.client.api.models.WatchChannelRequest
import io.getstream.chat.android.client.call.await
import io.getstream.chat.android.client.events.ChannelDeletedEvent
import io.getstream.chat.android.client.events.ChannelHiddenEvent
import io.getstream.chat.android.client.events.ChannelTruncatedEvent
import io.getstream.chat.android.client.events.ChannelUpdatedByUserEvent
import io.getstream.chat.android.client.events.ChannelUpdatedEvent
import io.getstream.chat.android.client.events.ChannelUserBannedEvent
import io.getstream.chat.android.client.events.ChannelUserUnbannedEvent
import io.getstream.chat.android.client.events.ChannelVisibleEvent
import io.getstream.chat.android.client.events.ChatEvent
import io.getstream.chat.android.client.events.ConnectedEvent
import io.getstream.chat.android.client.events.ConnectingEvent
import io.getstream.chat.android.client.events.DisconnectedEvent
import io.getstream.chat.android.client.events.ErrorEvent
import io.getstream.chat.android.client.events.GlobalUserBannedEvent
import io.getstream.chat.android.client.events.GlobalUserUnbannedEvent
import io.getstream.chat.android.client.events.HealthEvent
import io.getstream.chat.android.client.events.MarkAllReadEvent
import io.getstream.chat.android.client.events.MemberAddedEvent
import io.getstream.chat.android.client.events.MemberRemovedEvent
import io.getstream.chat.android.client.events.MemberUpdatedEvent
import io.getstream.chat.android.client.events.MessageDeletedEvent
import io.getstream.chat.android.client.events.MessageReadEvent
import io.getstream.chat.android.client.events.MessageUpdatedEvent
import io.getstream.chat.android.client.events.NewMessageEvent
import io.getstream.chat.android.client.events.NotificationAddedToChannelEvent
import io.getstream.chat.android.client.events.NotificationChannelDeletedEvent
import io.getstream.chat.android.client.events.NotificationChannelMutesUpdatedEvent
import io.getstream.chat.android.client.events.NotificationChannelTruncatedEvent
import io.getstream.chat.android.client.events.NotificationInviteAcceptedEvent
import io.getstream.chat.android.client.events.NotificationInviteRejectedEvent
import io.getstream.chat.android.client.events.NotificationInvitedEvent
import io.getstream.chat.android.client.events.NotificationMarkReadEvent
import io.getstream.chat.android.client.events.NotificationMessageNewEvent
import io.getstream.chat.android.client.events.NotificationMutesUpdatedEvent
import io.getstream.chat.android.client.events.NotificationRemovedFromChannelEvent
import io.getstream.chat.android.client.events.ReactionDeletedEvent
import io.getstream.chat.android.client.events.ReactionNewEvent
import io.getstream.chat.android.client.events.ReactionUpdateEvent
import io.getstream.chat.android.client.events.TypingStartEvent
import io.getstream.chat.android.client.events.TypingStopEvent
import io.getstream.chat.android.client.events.UnknownEvent
import io.getstream.chat.android.client.events.UserDeletedEvent
import io.getstream.chat.android.client.events.UserPresenceChangedEvent
import io.getstream.chat.android.client.events.UserStartWatchingEvent
import io.getstream.chat.android.client.events.UserStopWatchingEvent
import io.getstream.chat.android.client.events.UserUpdatedEvent
import io.getstream.chat.android.client.extensions.enrichWithCid
import io.getstream.chat.android.client.extensions.internal.NEVER
import io.getstream.chat.android.client.extensions.internal.applyPagination
import io.getstream.chat.android.client.extensions.internal.shouldIncrementUnreadCount
import io.getstream.chat.android.client.extensions.internal.users
import io.getstream.chat.android.client.extensions.internal.wasCreatedAfter
import io.getstream.chat.android.client.extensions.internal.wasCreatedBeforeOrAt
import io.getstream.chat.android.client.extensions.isPermanent
import io.getstream.chat.android.client.logger.ChatLogger
import io.getstream.chat.android.client.models.Channel
import io.getstream.chat.android.client.models.ChannelConfig
import io.getstream.chat.android.client.models.ChannelUserRead
import io.getstream.chat.android.client.models.Member
import io.getstream.chat.android.client.models.Message
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.client.plugin.listeners.QueryChannelListener
import io.getstream.chat.android.client.query.pagination.AnyChannelPaginationRequest
import io.getstream.chat.android.client.utils.Result
import io.getstream.chat.android.client.utils.SyncStatus
import io.getstream.chat.android.client.utils.onError
import io.getstream.chat.android.client.utils.onSuccess
import io.getstream.chat.android.client.utils.onSuccessSuspend
import io.getstream.chat.android.core.utils.date.inOffsetWith
import io.getstream.chat.android.offline.message.attachments.internal.AttachmentUrlValidator
import io.getstream.chat.android.offline.model.channel.ChannelData
import io.getstream.chat.android.offline.model.querychannels.pagination.internal.QueryChannelPaginationRequest
import io.getstream.chat.android.offline.model.querychannels.pagination.internal.toAnyChannelPaginationRequest
import io.getstream.chat.android.offline.plugin.state.channel.ChannelState
import io.getstream.chat.android.offline.plugin.state.channel.internal.ChannelMutableState
import io.getstream.chat.android.offline.plugin.state.global.internal.GlobalMutableState
import io.getstream.chat.android.offline.plugin.state.global.internal.MutableGlobalState
import io.getstream.chat.android.offline.repository.builder.internal.RepositoryFacade
import io.getstream.chat.android.offline.utils.Event
import io.getstream.chat.android.offline.utils.internal.isChannelMutedForCurrentUser
import io.getstream.logging.StreamLog
import java.util.Date
import kotlin.math.max

/**
 * This class contains all the logic to manipulate and modify the state of the corresponding channel.
 *
 * @property mutableState [ChannelMutableState] Mutable state instance of the channel.
 * @property globalMutableState [GlobalMutableState] Global state of the SDK.
 * @property repos [RepositoryFacade] that interact with data sources.
 * @property userPresence [Boolean] true if user presence is enabled, false otherwise.
 * @property attachmentUrlValidator [AttachmentUrlValidator] A validator to validate attachments' url.
 */
@Suppress("TooManyFunctions", "LargeClass")
internal class ChannelLogic(
    private val mutableState: ChannelMutableState,
    private val globalMutableState: MutableGlobalState,
    private val repos: RepositoryFacade,
    private val userPresence: Boolean,
    private val attachmentUrlValidator: AttachmentUrlValidator = AttachmentUrlValidator(),
    private val searchLogic: SearchLogic = SearchLogic(mutableState),
) : QueryChannelListener {

    private val logger = ChatLogger.get("Query channel request")

    val cid: String
        get() = mutableState.cid

    private fun loadingStateByRequest(request: QueryChannelRequest) = when {
        request.isFilteringNewerMessages() -> mutableState._loadingNewerMessages
        request.filteringOlderMessages() -> mutableState._loadingOlderMessages
        else -> mutableState._loading
    }

    override suspend fun onQueryChannelPrecondition(
        channelType: String,
        channelId: String,
        request: QueryChannelRequest,
    ): Result<Unit> {
        return Result(Unit)
    }

    override suspend fun onQueryChannelRequest(channelType: String, channelId: String, request: QueryChannelRequest) {
        val isChannelMuted = globalMutableState.channelMutes.value.any { it.channel.cid == cid }
        StreamLog.d(TAG) { "[onQueryChannelRequest] isChannelMuted: $isChannelMuted, cid: $cid" }
        mutableState._muted.value = isChannelMuted

        /* It is not possible to guarantee that the next page of newer messages is the same of backend,
         * so we force the backend usage */
        if (!request.isFilteringNewerMessages()) {
            runChannelQueryOffline(request)
        }
    }

    override suspend fun onQueryChannelResult(
        result: Result<Channel>,
        channelType: String,
        channelId: String,
        request: QueryChannelRequest,
    ) {
        result.onSuccessSuspend { channel ->
            StreamLog.v(TAG) { "[onQueryChannelResult] isSuccess: ${result.isSuccess}" }
            // first thing here needs to be updating configs otherwise we have a race with receiving events
            repos.insertChannelConfig(ChannelConfig(channel.type, channel.config))
            storeStateForChannel(channel)
        }
            .onSuccess { channel ->
                val noMoreMessages = request.messagesLimit() > channel.messages.size

                searchLogic.handleMessageBounds(request, noMoreMessages)
                mutableState.recoveryNeeded = false

                if (noMoreMessages) {
                    if (request.isFilteringNewerMessages()) {
                        mutableState._endOfNewerMessages.value = true
                    } else {
                        mutableState._endOfOlderMessages.value = true
                    }
                }

                updateDataFromChannel(
                    channel,
                    shouldRefreshMessages = request.isFilteringAroundIdMessages(),
                    scrollUpdate = true
                )
                loadingStateByRequest(request).value = false
            }
            .onError { error ->
                if (error.isPermanent()) {
                    logger.logW("Permanent failure calling channel.watch for channel ${mutableState.cid}, with error $error")
                } else {
                    logger.logW("Temporary failure calling channel.watch for channel ${mutableState.cid}. Marking the channel as needing recovery. Error was $error")
                    mutableState.recoveryNeeded = true
                }
                globalMutableState.setErrorEvent(Event(error))
            }
    }

    private suspend fun storeStateForChannel(channel: Channel) {
        val users = channel.users().associateBy { it.id }.toMutableMap()
        val configs: MutableCollection<ChannelConfig> = mutableSetOf(ChannelConfig(channel.type, channel.config))
        channel.messages.forEach { message ->
            message.enrichWithCid(channel.cid)
            users.putAll(message.users().associateBy { it.id })
        }
        repos.storeStateForChannels(
            configs = configs,
            users = users.values.toList(),
            channels = listOf(channel),
            messages = channel.messages
        )
    }

    /**
     * Returns the state of Channel. Useful to check how it the state of the channel of the [ChannelLogic]
     *
     * @return [ChannelState]
     */
    internal fun state(): ChannelState = mutableState

    /**
     * Starts to watch this channel.
     *
     * @param messagesLimit The limit of messages inside the channel that should be requested.
     * @param userPresence Flag to determine if the SDK is going to receive UserPresenceChanged events. Used by the SDK to indicate if the user is online or not.
     */
    internal suspend fun watch(messagesLimit: Int = 30, userPresence: Boolean) {
        // Otherwise it's too easy for devs to create UI bugs which DDOS our API
        if (mutableState._loading.value) {
            logger.logI("Another request to watch this channel is in progress. Ignoring this request.")
            return
        }
        runChannelQuery(QueryChannelPaginationRequest(messagesLimit).toWatchChannelRequest(userPresence))
    }

    /**
     * Loads a list of messages after the newest message in the current list.
     *
     * @param messageId Id of message after which to fetch messages.
     * @param limit Number of messages to fetch after this message.
     *
     * @return [Result] of [Channel] with fetched messages.
     */
    internal suspend fun loadNewerMessages(messageId: String, limit: Int): Result<Channel> {
        return runChannelQuery(newerWatchChannelRequest(limit = limit, baseMessageId = messageId))
    }

    /**
     * Loads a list of messages before the message with particular message id.
     *
     * @param messageLimit Number of messages to fetch before this message.
     * @param baseMessageId Id of message before which to fetch messages. Last available message will be calculated if the parameter is null.
     *
     * @return [Result] of [Channel] with fetched messages.
     */
    internal suspend fun loadOlderMessages(messageLimit: Int, baseMessageId: String? = null): Result<Channel> {
        return runChannelQuery(olderWatchChannelRequest(limit = messageLimit, baseMessageId = baseMessageId))
    }

    internal suspend fun loadMessagesAroundId(aroundMessageId: String): Result<Channel> {
        return runChannelQuery(aroundIdWatchChannelRequest(aroundMessageId))
    }

    private suspend fun runChannelQuery(request: WatchChannelRequest): Result<Channel> {
        val offlineChannel = runChannelQueryOffline(request)

        val onlineResult =
            ChatClient.instance().queryChannelInternal(mutableState.channelType, mutableState.channelId, request)
                .await().also { result ->
                    onQueryChannelResult(result, mutableState.channelType, mutableState.channelId, request)
                }

        return when {
            onlineResult.isSuccess -> onlineResult
            offlineChannel != null -> Result.success(offlineChannel)
            else -> onlineResult
        }
    }

    private suspend fun runChannelQueryOffline(request: QueryChannelRequest): Channel? {
        val loader = loadingStateByRequest(request)
        loader.value = true
        return selectAndEnrichChannel(mutableState.cid, request)?.also { channel ->
            logger.logI("Loaded channel ${channel.cid} from offline storage with ${channel.messages.size} messages")
            if (request.filteringOlderMessages()) {
                updateOldMessagesFromLocalChannel(channel)
            } else {
                updateDataFromLocalChannel(channel)
            }
            loader.value = false
        }
    }

    private fun updateDataFromLocalChannel(localChannel: Channel) {
        localChannel.hidden?.let(::setHidden)
        mutableState.hideMessagesBefore = localChannel.hiddenMessagesBefore
        updateDataFromChannel(localChannel, scrollUpdate = true)
    }

    private fun updateOldMessagesFromLocalChannel(localChannel: Channel) {
        localChannel.hidden?.let(::setHidden)
        mutableState.hideMessagesBefore = localChannel.hiddenMessagesBefore
        updateOldMessagesFromChannel(localChannel)
    }

    private suspend fun selectAndEnrichChannel(
        channelId: String,
        pagination: QueryChannelRequest,
    ): Channel? = selectAndEnrichChannels(listOf(channelId), pagination.toAnyChannelPaginationRequest()).getOrNull(0)

    private suspend fun selectAndEnrichChannels(
        channelIds: List<String>,
        pagination: AnyChannelPaginationRequest,
    ): List<Channel> = repos.selectChannels(channelIds, pagination).applyPagination(pagination)

    internal fun setHidden(hidden: Boolean) {
        mutableState._hidden.value = hidden
    }

    private fun updateOldMessagesFromChannel(c: Channel) {
        // Update all the flow objects based on the channel
        updateChannelData(c)
        setWatcherCount(c.watcherCount)
        updateReads(c.read)
        mutableState._membersCount.value = c.memberCount

        // there are some edge cases here, this code adds to the members, watchers and messages
        // this means that if the offline sync went out of sync things go wrong
        setMembers(c.members)
        setWatchers(c.watchers)
        upsertOldMessages(c.messages)
    }

    private fun upsertOldMessages(messages: List<Message>) {
        mutableState._oldMessages.value = parseMessages(messages)
    }

    internal fun updateDataFromChannel(
        c: Channel,
        shouldRefreshMessages: Boolean = false,
        scrollUpdate: Boolean = false,
    ) {
        // Update all the flow objects based on the channel
        updateChannelData(c)
        setWatcherCount(c.watcherCount)

        mutableState._read.value?.lastMessageSeenDate = c.lastMessageAt
        mutableState._membersCount.value = c.memberCount

        updateReads(c.read)

        // there are some edge cases here, this code adds to the members, watchers and messages
        // this means that if the offline sync went out of sync things go wrong
        setMembers(c.members)
        setWatchers(c.watchers)

        if (!mutableState.insideSearch.value || scrollUpdate) {
            upsertMessages(c.messages, shouldRefreshMessages)
        }
        mutableState.lastMessageAt.value = c.lastMessageAt
        mutableState._channelConfig.value = c.config
    }

    internal fun upsertMessages(messages: List<Message>, shouldRefreshMessages: Boolean = false) {
        val newMessages = parseMessages(messages, shouldRefreshMessages)
        updateLastMessageAtByNewMessages(newMessages.values)
        mutableState._messages.value = newMessages
    }

    /**
     * Updates the messages locally and saves it at database.
     *
     * @param messages The list of messages to be updated in the SDK and to be saved in database.
     */
    internal suspend fun updateAndSaveMessages(messages: List<Message>) {
        upsertMessages(messages)
        storeMessageLocally(messages)
    }

    /**
     * Store the messages in the local cache.
     *
     * @param messages The messages to be stored. Check [Message].
     */
    internal suspend fun storeMessageLocally(messages: List<Message>) {
        repos.insertMessages(messages)
    }

    internal fun upsertMessage(message: Message) = upsertMessages(listOf(message))

    private fun setWatcherCount(watcherCount: Int) {
        if (watcherCount != mutableState._watcherCount.value) {
            mutableState._watcherCount.value = watcherCount
        }
    }

    private fun setMembers(members: List<Member>) {
        mutableState._members.value = (mutableState._members.value + members.associateBy(Member::getUserId))
    }

    private fun updateChannelData(channel: Channel) {
        val currentOwnCapabilities = mutableState._channelData.value?.ownCapabilities ?: emptySet()
        mutableState._channelData.value = ChannelData(channel, currentOwnCapabilities)
    }

    private fun setWatchers(watchers: List<User>) {
        mutableState._watchers.value = (mutableState._watchers.value + watchers.associateBy { it.id })
    }

    /**
     * Increments the unread count of the Channel if necessary.
     *
     * @param message [Message].
     */
    private fun incrementUnreadCountIfNecessary(message: Message) {
        val user = globalMutableState.user.value ?: return
        val currentUserId = user.id

        /* Only one thread can access this logic per time. If two messages pass the shouldIncrementUnreadCount at the
         * same time, one increment can be lost.
         */
        synchronized(this) {
            val readState = mutableState._read.value?.copy() ?: ChannelUserRead(user)
            val unreadCount: Int = readState.unreadMessages
            val lastMessageSeenDate = readState.lastMessageSeenDate

            val shouldIncrementUnreadCount =
                message.shouldIncrementUnreadCount(
                    currentUserId = currentUserId,
                    lastMessageAtDate = lastMessageSeenDate,
                    isChannelMuted = isChannelMutedForCurrentUser(mutableState.cid)
                )

            if (shouldIncrementUnreadCount) {
                logger.logD(
                    "It is necessary to increment the unread count for channel: " +
                        "${mutableState._channelData.value?.channelId}. The last seen message was " +
                        "at: $lastMessageSeenDate. " +
                        "New unread count: ${unreadCount + 1}"
                )

                mutableState._read.value = readState.apply {
                    this.unreadMessages = unreadCount + 1
                    this.lastMessageSeenDate = message.createdAt
                }
                mutableState._reads.value = mutableState._reads.value.apply {
                    this[currentUserId]?.lastMessageSeenDate = message.createdAt
                    this[currentUserId]?.unreadMessages = unreadCount + 1
                }
                mutableState._unreadCount.value = unreadCount + 1
            }
        }
    }

    internal fun updateReads(reads: List<ChannelUserRead>) {
        globalMutableState.user.value?.let { currentUser ->
            val currentUserId = currentUser.id
            val previousUserIdToReadMap = mutableState._reads.value
            val incomingUserIdToReadMap = reads.associateBy(ChannelUserRead::getUserId).toMutableMap()

            /**
             * It's possible that the data coming back from the online channel query has a last read date that's
             * before what we've last pushed to the UI. We want to ignore this, as it will cause an unread state
             * to show in the channel list.
             */
            incomingUserIdToReadMap[currentUserId]?.let { incomingUserRead ->
                incomingUserRead.lastMessageSeenDate = mutableState._read.value?.lastMessageSeenDate

                // the previous last Read date that is most current
                val previousLastRead =
                    mutableState._read.value?.lastRead ?: previousUserIdToReadMap[currentUserId]?.lastRead

                // Use AFTER to determine if the incoming read is more current.
                // This prevents updates if it's BEFORE or EQUAL TO the previous Read.
                val shouldUpdateByIncoming = previousLastRead == null || incomingUserRead.lastRead?.inOffsetWith(
                    previousLastRead,
                    OFFSET_EVENT_TIME
                ) == true

                if (shouldUpdateByIncoming) {
                    mutableState._read.value = incomingUserRead
                    mutableState._unreadCount.value = incomingUserRead.unreadMessages
                } else {
                    // if the previous Read was more current, replace the item in the update map
                    incomingUserIdToReadMap[currentUserId] = ChannelUserRead(currentUser, previousLastRead)
                }
            }

            // always post the newly updated map
            mutableState._reads.value = (previousUserIdToReadMap + incomingUserIdToReadMap)
        }
    }

    private fun updateRead(read: ChannelUserRead) = updateReads(listOf(read))

    /**
     * Updates [ChannelMutableState._messages] with new messages.
     * The message will by only updated if its creation/update date is newer than the one stored in the StateFlow.
     *
     * @param messages The list of messages to update.
     */
    private fun parseMessages(messages: List<Message>, shouldRefresh: Boolean = false): Map<String, Message> {
        val currentMessages = if (shouldRefresh) emptyMap() else mutableState._messages.value
        return currentMessages + attachmentUrlValidator.updateValidAttachmentsUrl(messages, currentMessages)
            .filter { newMessage -> isMessageNewerThanCurrent(currentMessages[newMessage.id], newMessage) }
            .associateBy(Message::id)
    }

    private fun isMessageNewerThanCurrent(currentMessage: Message?, newMessage: Message): Boolean {
        return if (newMessage.syncStatus == SyncStatus.COMPLETED) {
            currentMessage?.lastUpdateTime() ?: NEVER.time <= newMessage.lastUpdateTime()
        } else {
            currentMessage?.lastLocalUpdateTime() ?: NEVER.time <= newMessage.lastLocalUpdateTime()
        }
    }

    private fun updateLastMessageAtByNewMessages(newMessages: Collection<Message>) {
        if (newMessages.isEmpty()) {
            return
        }
        val newLastMessageAt =
            newMessages.mapNotNull { it.createdAt ?: it.createdLocallyAt }.maxOfOrNull(Date::getTime) ?: return
        mutableState.lastMessageAt.value = when (val currentLastMessageAt = mutableState.lastMessageAt.value) {
            null -> Date(newLastMessageAt)
            else -> max(currentLastMessageAt.time, newLastMessageAt).let(::Date)
        }
    }

    private fun Message.lastUpdateTime(): Long = listOfNotNull(
        createdAt,
        updatedAt,
        deletedAt,
    ).map { it.time }
        .maxOrNull()
        ?: NEVER.time

    private fun Message.lastLocalUpdateTime(): Long = listOfNotNull(
        createdLocallyAt,
        updatedLocallyAt,
        deletedAt,
    ).map { it.time }
        .maxOrNull()
        ?: NEVER.time

    /**
     * Returns instance of [WatchChannelRequest] to obtain older messages of a channel.
     *
     * @param limit Message limit in this request.
     * @param baseMessageId Message id of the last available message. Request will fetch messages older than this.
     */
    private fun olderWatchChannelRequest(limit: Int, baseMessageId: String?): WatchChannelRequest =
        watchChannelRequest(Pagination.LESS_THAN, limit, baseMessageId)

    /**
     * Returns instance of [WatchChannelRequest] to obtain newer messages of a channel.
     *
     * @param limit Message limit in this request.
     * @param baseMessageId Message id of the last available message. Request will fetch messages newer than this.
     */
    private fun newerWatchChannelRequest(limit: Int, baseMessageId: String?): WatchChannelRequest =
        watchChannelRequest(Pagination.GREATER_THAN, limit, baseMessageId)

    private fun aroundIdWatchChannelRequest(aroundMessageId: String): WatchChannelRequest {
        return QueryChannelPaginationRequest().apply {
            messageFilterDirection = Pagination.AROUND_ID
            messageFilterValue = aroundMessageId
        }.toWatchChannelRequest(userPresence)
    }

    /**
     * Creates instance of [WatchChannelRequest] according to [Pagination].
     *
     * @param pagination Pagination parameter which defines should we request older/newer messages.
     * @param limit Message limit in this request.
     * @param baseMessageId Message id of the last available. Can be null then it calculates the last available message.
     */
    private fun watchChannelRequest(pagination: Pagination, limit: Int, baseMessageId: String?): WatchChannelRequest {
        val messageId = baseMessageId ?: getLoadMoreBaseMessageId(pagination)
        return QueryChannelPaginationRequest(limit).apply {
            messageId?.let {
                messageFilterDirection = pagination
                messageFilterValue = it
            }
        }.toWatchChannelRequest(userPresence)
    }

    /**
     * Calculates base messageId for [WatchChannelRequest] depending on [Pagination] when requesting more messages.
     *
     * @param direction [Pagination] instance which shows direction of pagination.
     */
    private fun getLoadMoreBaseMessageId(direction: Pagination): String? {
        val messages = mutableState.sortedMessages.value.takeUnless(Collection<Message>::isEmpty) ?: return null
        return when (direction) {
            Pagination.GREATER_THAN_OR_EQUAL,
            Pagination.GREATER_THAN,
            -> messages.last().id
            Pagination.LESS_THAN,
            Pagination.LESS_THAN_OR_EQUAL,
            Pagination.AROUND_ID,
            -> messages.first().id
        }
    }

    /**
     * Remove a local message from the current list.
     *
     * @param message The [Message] to remove.
     */
    internal fun removeLocalMessage(message: Message) {
        mutableState._messages.value = mutableState._messages.value - message.id
    }

    /**
     * Removes messages before the given date and optionally adds a system message
     * that was coming with the event.
     *
     * @param date The date used for generating result.
     * @param systemMessage The system message to display.
     */
    internal fun removeMessagesBefore(
        date: Date,
        systemMessage: Message? = null,
    ) {
        val messages = mutableState._messages.value.filter { it.value.wasCreatedAfter(date) }

        if (systemMessage == null) {
            mutableState._messages.value = messages
        } else {
            mutableState._messages.value = messages + listOf(systemMessage).associateBy(Message::id)
            updateLastMessageAtByNewMessages(listOf(systemMessage))
        }
    }

    /**
     * Hides the messages created before the given date.
     *
     * @param date The date used for generating result.
     */
    internal fun hideMessagesBefore(date: Date) {
        mutableState.hideMessagesBefore = date
    }

    private fun upsertEventMessage(message: Message) {
        // make sure we don't lose ownReactions
        getMessage(message.id)?.let {
            message.ownReactions = it.ownReactions
        }

        upsertMessages(listOf(message))
    }

    /**
     * Returns message stored in [ChannelMutableState] if exists and wasn't hidden.
     *
     * @param messageId The id of the message.
     *
     * @return [Message] if exists and wasn't hidden, null otherwise.
     */
    internal fun getMessage(messageId: String): Message? {
        val copy = mutableState.messageList.value
        var message = copy.firstOrNull { it.id == messageId }

        if (mutableState.hideMessagesBefore != null) {
            if (message != null && message.wasCreatedBeforeOrAt(mutableState.hideMessagesBefore)) {
                message = null
            }
        }

        return message
    }

    private fun deleteMember(userId: String) {
        mutableState._members.value = mutableState._members.value - userId
        mutableState._membersCount.value -= 1
    }

    private fun upsertMembers(members: List<Member>) {
        mutableState._members.value = mutableState._members.value + members.associateBy { it.user.id }
    }

    private fun upsertMember(member: Member) {
        upsertMembers(listOf(member))
    }

    private fun upsertUserPresence(user: User) {
        val userId = user.id
        // members and watchers have users
        val members = mutableState.members.value
        val watchers = mutableState.watchers.value
        val member = members.firstOrNull { it.getUserId() == userId }?.copy()
        val watcher = watchers.firstOrNull { it.id == userId }
        if (member != null) {
            member.user = user
            upsertMember(member)
        }
        if (watcher != null) {
            upsertWatcher(user)
        }
    }

    private fun upsertWatcher(user: User) {
        mutableState._watchers.value = mutableState._watchers.value + mapOf(user.id to user)
    }

    private fun deleteWatcher(user: User) {
        mutableState._watchers.value = mutableState._watchers.value - user.id
    }

    private fun upsertUser(user: User) {
        upsertUserPresence(user)
        // channels have users
        val userId = user.id
        val channelData = mutableState._channelData.value
        if (channelData != null) {
            if (channelData.createdBy.id == userId) {
                channelData.createdBy = user
            }
        }

        // updating messages is harder
        // user updates don't happen frequently, it's probably ok for this update to be sluggish
        // if it turns out to be slow we can do a simple reverse index from user -> message
        val messages = mutableState.messageList.value
        val changedMessages = mutableListOf<Message>()
        for (message in messages) {
            var changed = false
            if (message.user.id == userId) {
                message.user = user
                changed = true
            }
            for (reaction in message.ownReactions) {
                if (reaction.user!!.id == userId) {
                    reaction.user = user
                    changed = true
                }
            }
            for (reaction in message.latestReactions) {
                if (reaction.user!!.id == userId) {
                    reaction.user = user
                    changed = true
                }
            }
            if (changed) changedMessages.add(message)
        }
        if (changedMessages.isNotEmpty()) {
            upsertMessages(changedMessages)
        }
    }

    private fun setTyping(userId: String, event: ChatEvent?) {
        val copy = mutableState._typing.value.toMutableMap()
        if (event == null) {
            copy.remove(userId)
        } else {
            copy[userId] = event
        }
        globalMutableState.user.value?.id.let(copy::remove)
        mutableState._typing.value = copy.toMap()
    }

    /**
     * Handles events received from the socket.
     *
     * @see [handleEvent]
     */
    internal fun handleEvents(events: List<ChatEvent>) {
        for (event in events) {
            handleEvent(event)
        }
    }

    /**
     * Handles event received from the socket.
     * Responsible for synchronizing [ChannelMutableState].
     */
    internal fun handleEvent(event: ChatEvent) {
        StreamLog.d("Channel-Logic") { "[handleEvent] cid: $cid, event: $event" }
        when (event) {
            is NewMessageEvent -> {
                /* If we are inside a search, ignore new messages, because the last message is not available in the
                 * screen */
                if (!mutableState.insideSearch.value) {
                    upsertEventMessage(event.message)
                }
                incrementUnreadCountIfNecessary(event.message)
                setHidden(false)
            }
            is MessageUpdatedEvent -> {
                event.message.apply {
                    replyTo = mutableState.messageList.value.firstOrNull { it.id == replyMessageId }
                }.let(::upsertEventMessage)

                setHidden(false)
            }
            is MessageDeletedEvent -> {
                if (event.hardDelete) {
                    removeLocalMessage(event.message)
                } else {
                    upsertEventMessage(event.message)
                }
                setHidden(false)
            }
            is NotificationMessageNewEvent -> {
                /* If we are inside a search, ignore new messages, because the last message is not available in the
                 * screen */
                if (!mutableState.insideSearch.value) {
                    upsertEventMessage(event.message)
                }
                incrementUnreadCountIfNecessary(event.message)
                setHidden(false)
            }
            is ReactionNewEvent -> {
                upsertMessage(event.message)
            }
            is ReactionUpdateEvent -> {
                upsertMessage(event.message)
            }
            is ReactionDeletedEvent -> {
                upsertMessage(event.message)
            }
            is MemberRemovedEvent -> {
                deleteMember(event.user.id)
            }
            is NotificationRemovedFromChannelEvent -> {
                deleteMember(event.member.user.id)
            }
            is MemberAddedEvent -> {
                mutableState._membersCount.value += 1
                upsertMember(event.member)
            }
            is MemberUpdatedEvent -> {
                upsertMember(event.member)
            }
            is NotificationAddedToChannelEvent -> {
                mutableState._membersCount.value += event.channel.members.size
                upsertMembers(event.channel.members)
            }
            is UserPresenceChangedEvent -> {
                upsertUserPresence(event.user)
            }
            is UserUpdatedEvent -> {
                upsertUser(event.user)
            }
            is UserStartWatchingEvent -> {
                upsertWatcher(event.user)
                setWatcherCount(event.watcherCount)
            }
            is UserStopWatchingEvent -> {
                deleteWatcher(event.user)
                setWatcherCount(event.watcherCount)
            }
            is ChannelUpdatedEvent -> {
                updateChannelData(event.channel)
            }
            is ChannelUpdatedByUserEvent -> {
                updateChannelData(event.channel)
            }
            is ChannelHiddenEvent -> {
                setHidden(true)
            }
            is ChannelVisibleEvent -> {
                setHidden(false)
            }
            is ChannelDeletedEvent -> {
                removeMessagesBefore(event.createdAt)
                mutableState._channelData.value = mutableState.channelData.value.copy(deletedAt = event.createdAt)
            }
            is ChannelTruncatedEvent -> {
                removeMessagesBefore(event.createdAt, event.message)
            }
            is NotificationChannelTruncatedEvent -> {
                removeMessagesBefore(event.createdAt)
            }
            is TypingStopEvent -> {
                setTyping(event.user.id, null)
            }
            is TypingStartEvent -> {
                setTyping(event.user.id, event)
            }
            is MessageReadEvent -> {
                updateRead(ChannelUserRead(event.user, event.createdAt))
            }
            is NotificationMarkReadEvent -> {
                updateRead(ChannelUserRead(event.user, event.createdAt))
            }
            is MarkAllReadEvent -> {
                updateRead(ChannelUserRead(event.user, event.createdAt))
            }
            is NotificationInviteAcceptedEvent -> {
                upsertMember(event.member)
                updateChannelData(event.channel)
            }
            is NotificationInviteRejectedEvent -> {
                upsertMember(event.member)
                updateChannelData(event.channel)
            }
            is NotificationChannelMutesUpdatedEvent -> {
                mutableState._muted.value = event.me.channelMutes.any { mute ->
                    mute.channel.cid == mutableState.cid
                }
            }
            is ChannelUserBannedEvent,
            is ChannelUserUnbannedEvent,
            is NotificationChannelDeletedEvent,
            is NotificationInvitedEvent,
            is ConnectedEvent,
            is ConnectingEvent,
            is DisconnectedEvent,
            is ErrorEvent,
            is GlobalUserBannedEvent,
            is GlobalUserUnbannedEvent,
            is HealthEvent,
            is NotificationMutesUpdatedEvent,
            is UnknownEvent,
            is UserDeletedEvent,
            -> Unit // Ignore these events
        }
    }

    fun toChannel(): Channel = mutableState.toChannel()

    internal fun replyMessage(repliedMessage: Message?) {
        mutableState._repliedMessage.value = repliedMessage
    }

    private companion object {
        private const val TAG = "Channel-Logic"
        private const val OFFSET_EVENT_TIME = 5L
    }
}
