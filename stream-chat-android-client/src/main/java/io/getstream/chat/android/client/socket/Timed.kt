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

package io.getstream.chat.android.client.socket

/**
 * A wrapper that contains timestamp along with the [value].
 */
internal data class Timed<T>(val value: T, val time: Long)

internal fun List<Timed<Event.Lifecycle>>.combineLifecycleState() =
    if (any { it.value.isStoppedAndAborted() }) {
        filter { it.value.isStoppedAndAborted() }
            .minByOrNull { it.time }!!
            .value
    } else if (any { it.value.isStopped() }) {
        filter { it.value.isStopped() }
            .minByOrNull { it.time }!!
            .value
    } else Event.Lifecycle.Started
