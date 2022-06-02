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

package io.getstream.chat.android.client.api.models.querysort.internal

import io.getstream.chat.android.client.api.models.querysort.IQuerySort.Companion.EQUAL_ON_COMPARISON
import io.getstream.chat.android.client.api.models.querysort.IQuerySort.Companion.LESS_ON_COMPARISON
import io.getstream.chat.android.client.api.models.querysort.IQuerySort.Companion.MORE_ON_COMPARISON
import io.getstream.chat.android.client.api.models.querysort.SortDirection

internal fun compare(
    first: Comparable<Any>?,
    second: Comparable<Any>?,
    sortDirection: SortDirection,
): Int {
    return when {
        first == null && second == null -> EQUAL_ON_COMPARISON
        first == null && second != null -> LESS_ON_COMPARISON * sortDirection.value
        first != null && second == null -> MORE_ON_COMPARISON * sortDirection.value
        first != null && second != null -> first.compareTo(second) * sortDirection.value
        else -> error("Impossible case!")
    }
}
