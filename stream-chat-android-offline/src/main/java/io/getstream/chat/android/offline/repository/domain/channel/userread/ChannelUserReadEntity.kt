package io.getstream.chat.android.offline.repository.domain.channel.userread

import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * Efficiently store the channel user read info.
 */
@JsonClass(generateAdapter = true)
public data class ChannelUserReadEntity(
    val userId: String,
    val lastRead: Date?,
    val unreadMessages: Int,
    val lastMessageSeenDate: Date?,
)
