package io.getstream.chat.android.livedata.usecase

import io.getstream.chat.android.client.utils.Result
import io.getstream.chat.android.livedata.Call2
import io.getstream.chat.android.livedata.CallImpl2
import io.getstream.chat.android.livedata.ChatDomainImpl
import io.getstream.chat.android.livedata.controller.ChannelController
import kotlinx.coroutines.launch

class WatchChannel(var domainImpl: ChatDomainImpl) {
    operator fun invoke(cid: String, messageLimit: Int): Call2<ChannelController> {
        val channelControllerImpl = domainImpl.channel(cid)
        val channelControllerI: ChannelController = channelControllerImpl

        if (messageLimit> 0) {
            channelControllerImpl.scope.launch {
                channelControllerImpl.watch(messageLimit)
            }
        }

        var runnable = suspend {
            Result(channelControllerI, null)
        }
        return CallImpl2<ChannelController>(runnable, channelControllerImpl.scope)
    }
}
