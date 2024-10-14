/*
 * Copyright (c) 2014-2024 Stream.io Inc. All rights reserved.
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

package io.getstream.chat.android.client.api2.mapping

import io.getstream.chat.android.client.api2.model.dto.DownstreamThreadDto
import io.getstream.chat.android.client.api2.model.dto.DownstreamThreadParticipantDto
import io.getstream.chat.android.models.Thread
import io.getstream.chat.android.models.User
import io.getstream.chat.android.models.UserId

internal fun DownstreamThreadDto.toDomain(currentUserId: UserId?): Thread =
    Thread(
        activeParticipantCount = active_participant_count,
        cid = channel_cid,
        channel = channel?.toDomain(currentUserId),
        parentMessageId = parent_message_id,
        parentMessage = parent_message?.toDomain(currentUserId),
        createdByUserId = created_by_user_id,
        createdBy = created_by?.toDomain(currentUserId),
        replyCount = reply_count,
        participantCount = participant_count,
        threadParticipants = thread_participants?.map { it.toDomain(currentUserId) },
        lastMessageAt = last_message_at,
        createdAt = created_at,
        updatedAt = updated_at,
        deletedAt = deleted_at,
        title = title,
        latestReplies = latest_replies.map { it.toDomain(currentUserId) },
        read = read?.map { it.toDomain(currentUserId, last_message_at) },
    )

internal fun DownstreamThreadParticipantDto.toDomain(currentUserId: UserId?): User = user.toDomain(currentUserId)
