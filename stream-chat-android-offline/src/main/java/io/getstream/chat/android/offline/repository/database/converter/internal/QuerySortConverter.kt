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

package io.getstream.chat.android.offline.repository.database.converter.internal

import androidx.room.TypeConverter
import com.squareup.moshi.adapter
import io.getstream.chat.android.client.api.models.querysort.QuerySort
import io.getstream.chat.android.client.api.models.querysort.QuerySortByMap
import io.getstream.chat.android.client.api.models.querysort.SortDirection
import io.getstream.chat.android.client.models.Channel

internal class QuerySortConverter {

    @OptIn(ExperimentalStdlibApi::class)
    private val adapter = moshi.adapter<List<Map<String, Any>>>()

    @TypeConverter
    fun stringToObject(data: String?): QuerySort<Channel> {
        if (data.isNullOrEmpty()) {
            return QuerySortByMap()
        }
        val listOfSortSpec = adapter.fromJson(data)
        return listOfSortSpec?.let(::parseQuerySort) ?: QuerySortByMap()
    }

    private fun parseQuerySort(listOfSortSpec: List<Map<String, Any>>): QuerySort<Channel> {
        return listOfSortSpec.fold(QuerySortByMap()) { sort, sortSpecMap ->
            val fieldName = sortSpecMap[QuerySort.KEY_FIELD_NAME] as? String ?: error("Cannot parse sortSpec to query sort\n$sortSpecMap")
            val direction = (sortSpecMap[QuerySort.KEY_DIRECTION] as? Number)?.toInt() ?: error("Cannot parse sortSpec to query sort\n$sortSpecMap")
            when (direction) {
                SortDirection.ASC.value -> sort.asc(fieldName)
                SortDirection.DESC.value -> sort.desc(fieldName)
                else -> error("Cannot parse sortSpec to query sort\n$sortSpecMap")
            }
        }
    }

    /**
     * @return Nullable [String] to let KSP know this function cannot be used for "Nullable to NonNull" converting.
     *
     * An example of the incorrect behaviour:
     *     val stringifiedObject: String? = anotherConverter.objectToString(...)
     *     val stringList: List<String>? = QuerySortConverter.stringToObject(stringifiedObject)
     *     val nonNullStringifiedObject: String = QuerySortConverter.objectToString(stringifiedObject)
     *     // ... binding nonNullStringifiedObject to table's column
     *
     * The current behavior:
     *     val stringifiedObject: String? = anotherConverter.objectToString(...)
     *     // ... binding stringifiedObject to table's column
     */
    @TypeConverter
    fun objectToString(querySort: QuerySort<Channel>): String? {
        return adapter.toJson(querySort.toDto())
    }
}
