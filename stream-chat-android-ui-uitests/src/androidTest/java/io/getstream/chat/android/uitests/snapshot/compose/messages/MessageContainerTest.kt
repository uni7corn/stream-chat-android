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

package io.getstream.chat.android.uitests.snapshot.compose.messages

import io.getstream.chat.android.client.models.Message
import io.getstream.chat.android.compose.state.messages.list.DateSeparatorState
import io.getstream.chat.android.compose.state.messages.list.SystemMessageState
import io.getstream.chat.android.compose.state.messages.list.ThreadSeparatorState
import io.getstream.chat.android.compose.ui.messages.list.MessageContainer
import io.getstream.chat.android.uitests.snapshot.compose.ComposeScreenshotTest
import io.getstream.chat.android.uitests.util.TestData
import org.junit.Test

class MessageContainerTest : ComposeScreenshotTest() {

    @Test
    fun dateSeparator() = runScreenshotTest {
        MessageContainer(DateSeparatorState(TestData.date1()))
    }

    @Test
    fun threadSeparator() = runScreenshotTest {
        MessageContainer(ThreadSeparatorState(replyCount = 5))
    }

    @Test
    fun systemMessage() = runScreenshotTest {
        MessageContainer(SystemMessageState(Message(text = "System message")))
    }
}
