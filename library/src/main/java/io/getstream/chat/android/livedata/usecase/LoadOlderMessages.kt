package io.getstream.chat.android.livedata.usecase

import io.getstream.chat.android.client.models.Channel
import io.getstream.chat.android.livedata.Call2
import io.getstream.chat.android.livedata.CallImpl2
import io.getstream.chat.android.livedata.ChatDomainImpl

class LoadOlderMessages(var domainImpl: ChatDomainImpl) {
    operator fun invoke(cid: String, messageLimit: Int): Call2<Channel> {
        val channelRepo = domainImpl.channel(cid)
        var runnable = suspend {
            channelRepo.loadOlderMessages(messageLimit)
        }
        return CallImpl2<Channel>(runnable, channelRepo.scope)
    }
}
