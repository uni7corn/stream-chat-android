package io.getstream.chat.android.offline.plugin.logic.channel.internal

import io.getstream.chat.android.offline.plugin.state.channel.internal.ChannelMutableState
import io.getstream.chat.android.offline.randomMessage
import io.getstream.chat.android.offline.randomUser
import io.getstream.chat.android.test.randomString
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be`
import org.junit.jupiter.api.Test
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
internal class GapLogicTest {

    private val scope = TestScope()
    private val channelType = randomString()
    private val channelId = randomString()
    private val userFlow = MutableStateFlow(randomUser())

    @Test
    fun `given there's a gap in messages, it should be propagated to state for newer messages`() {
        val state = ChannelMutableState(channelType, channelId, scope, userFlow, MutableStateFlow(mapOf()))
        val gapLogic = GapLogic(state)

        state.gapsInMessageList.value?.first `should be` null

        gapLogic.handleNewerMessagesLimit(true, listOf(randomMessage()), true)

        state.gapsInMessageList.value?.first `should be` true
    }

    @Test
    fun `given there's no gap messages should always be added bellow gap messages`() {
        val state = ChannelMutableState(channelType, channelId, scope, userFlow, MutableStateFlow(mapOf()))
        val gapLogic = GapLogic(state)
        val newMessages1 = listOf(randomMessage(createdAt = Date()))
        val newMessages2 = listOf(randomMessage(createdAt = Date()))
        //This message will be added at the the gap
        val newMessages3 = listOf(randomMessage(createdAt = Date()))

        gapLogic.handleOlderMessagesLimit(true, newMessages1)
        gapLogic.handleOlderMessagesLimit(true, newMessages2)
        gapLogic.handleNewerMessagesLimit(true, newMessages3, true)

        val hasGaps = state.gapsInMessageList.value?.first
        val gapInfo = state.gapsInMessageList.value?.second
        val expectedGapInfo = MessagesGapInfo(
            emptyList(),
            listOf(newMessages1.first().id.hashCode().toLong(), newMessages2.first().id.hashCode().toLong()),
            newMessages1 + newMessages2
        )

        hasGaps `should be` true
        gapInfo `should be equal to` expectedGapInfo
    }


    @Test
    fun `given there's no gap messages should always be added bellow gap messages with list of messages`() {
        val state = ChannelMutableState(channelType, channelId, scope, userFlow, MutableStateFlow(mapOf()))
        val gapLogic = GapLogic(state)

        val newMessages1 = listOf(nowMessage(), nowMessage(), nowMessage(), nowMessage())
        val newMessages2 = listOf(nowMessage(), nowMessage(), nowMessage(), nowMessage())
        //This message will be added at the the gap
        val newMessages3 = listOf(olderMessage(), oldestMessage(), oldestMessage())

        gapLogic.handleOlderMessagesLimit(true, newMessages1)
        gapLogic.handleOlderMessagesLimit(true, newMessages2)
        gapLogic.handleNewerMessagesLimit(true, newMessages3, true)

        val hasGaps = state.gapsInMessageList.value?.first
        val gapInfo = state.gapsInMessageList.value?.second

        val expectedHashes = mutableListOf<Long>().apply {
            newMessages1.map { message ->
                message.id.hashCode().toLong()
            }.let(this::addAll)

            newMessages2.map { message ->
                message.id.hashCode().toLong()
            }.let(this::addAll)
        }

        val expectedGapInfo = MessagesGapInfo(
            emptyList(),
            expectedHashes,
            newMessages1 + newMessages2
        )

        hasGaps `should be` true
        gapInfo `should be equal to` expectedGapInfo
    }

    @Test
    fun `given a gap was closed when loading newer messages, it should be propagated`() {
        val state = ChannelMutableState(channelType, channelId, scope, userFlow, MutableStateFlow(mapOf()))
        val gapLogic = GapLogic(state)

        state.gapsInMessageList.value?.first `should be` null

        gapLogic.handleNewerMessagesLimit(true, listOf(randomMessage()), true)

        state.gapsInMessageList.value?.first `should be` true

        gapLogic.handleNewerMessagesLimit(false, listOf(randomMessage()), true)

        state.gapsInMessageList.value?.first `should be` false
    }

    @Test
    fun `given a overlap occurs when loading newer messages, gap should be considered closed`() {
        val state = ChannelMutableState(channelType, channelId, scope, userFlow, MutableStateFlow(mapOf()))
        val gapLogic = GapLogic(state)

        state.gapsInMessageList.value?.first `should be` null

        val messageList = listOf(randomMessage(), randomMessage(), randomMessage(), randomMessage())

        gapLogic.handleNewerMessagesLimit(true, messageList, true)

        state.gapsInMessageList.value?.first `should be` true

        gapLogic.handleNewerMessagesLimit(false, messageList, true)

        state.gapsInMessageList.value?.first `should be` false
    }

    @Test
    fun `given a overlap occurs when loading older messages, gap should be considered closed`() {
        val state = ChannelMutableState(channelType, channelId, scope, userFlow, MutableStateFlow(mapOf()))
        val gapLogic = GapLogic(state)

        state.gapsInMessageList.value?.first `should be` null

        val message = randomMessage()

        val messageList1 = listOf(randomMessage(), randomMessage(), randomMessage(), message)

        gapLogic.handleNewerMessagesLimit(true, messageList1, true)

        state.gapsInMessageList.value?.first `should be` true

        val messageList2 = listOf(randomMessage(), randomMessage(), randomMessage(), message)
        gapLogic.handleOlderMessagesLimit(false, messageList2)

        state.gapsInMessageList.value?.first `should be` false
    }

    @Test
    fun `given more older messages are loaded to fill gap, the oldest should always be the first`() {
        //Todo: Implement this!!
    }

    private fun nowMessage() = randomMessage(createdAt = Date())
    private fun olderMessage() = randomMessage(createdAt = Date(100))
    private fun oldestMessage() = randomMessage(createdAt = Date(Long.MIN_VALUE))
}
