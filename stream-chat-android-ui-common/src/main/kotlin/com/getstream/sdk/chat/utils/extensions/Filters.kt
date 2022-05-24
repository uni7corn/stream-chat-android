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

package com.getstream.sdk.chat.utils.extensions

import io.getstream.chat.android.client.api.models.FilterObject
import io.getstream.chat.android.client.models.Filters
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.core.internal.InternalStreamChatApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

@InternalStreamChatApi
public fun Filters.defaultChannelListFilter(user: User?): FilterObject? {
    return if (user == null) {
        null
    } else {
        and(
            eq("type", "messaging"),
            `in`("members", listOf(user.id)),
        )
    }
}

/**
 * Builds the default filter object using a [Flow] that
 * emits [User] values.
 *
 * You can use this function in conjunction with
 * [io.getstream.chat.android.offline.extensions.globalState]'s user property
 * to create a reactive set of filters that change when the user is switched.
 */
@InternalStreamChatApi
public fun defaultUserFilterFlow(userFlow: Flow<User>): Flow<FilterObject> =
    userFlow.map(Filters::defaultChannelListFilter).filterNotNull()
