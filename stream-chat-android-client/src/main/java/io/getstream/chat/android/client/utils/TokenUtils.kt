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

package io.getstream.chat.android.client.utils

import android.util.Base64
import io.getstream.chat.android.client.logger.ChatLogger
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets

internal object TokenUtils {

    val logger = ChatLogger.get("TokenUtils")

    fun getUserId(token: String): String = try {
        JSONObject(
            token
                .takeIf { it.contains(".") }
                ?.split(".")
                ?.getOrNull(1)
                ?.let { String(Base64.decode(it.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)) }
                ?: ""
        ).optString("user_id")
    } catch (e: JSONException) {
        logger.logE("Unable to obtain userId from JWT Token Payload", e)
        ""
    } catch (e: IllegalArgumentException) {
        logger.logE("Unable to obtain userId from JWT Token Payload", e)
        ""
    }
}
