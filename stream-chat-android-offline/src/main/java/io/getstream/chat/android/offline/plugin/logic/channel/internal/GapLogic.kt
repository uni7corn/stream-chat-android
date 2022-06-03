package io.getstream.chat.android.offline.plugin.logic.channel.internal

import android.util.Log
import io.getstream.chat.android.client.logger.ChatLogger
import io.getstream.chat.android.client.models.Message
import io.getstream.chat.android.offline.plugin.state.channel.internal.ChannelMutableState

internal class GapLogic(private val mutableState: ChannelMutableState) {

    private val logger = ChatLogger.get("GapLogic")

    private var messageIdsAboveGap = mutableListOf<Long>()
    private var messageIdsBellowGap = mutableListOf<Long>()
    private var messagesBellowGap = mutableListOf<Message>()

    /* This message divides gaps. Messages older than this, are added bellow gap, messes newer than this are
    * added bellow gap */
    private var gapDivisorMessage: Message? = null

    internal fun handleNewerMessagesLimit(
        moreMessagesAvailable: Boolean,
        newMessages: List<Message>,
        canCreateGap: Boolean,
    ) {

        when {
            /* The messages list has gaps but the end of messages of an overlap was found.
             * The message list is linear again. */
            mutableState.gapsInMessageList.value?.first == true &&
                (!moreMessagesAvailable || newMessages.hasMessageOverlap(messageIdsAboveGap)) -> {

                Log.d("GapLogic", "A gap has closed in new messages!!")
                gapDivisorMessage = null
                mutableState._gapsInMessageList.value = false to null

                messageIdsAboveGap.clear()
            }

            /* The messages list had no gaps but newer messages were loaded. As it didn't reach the end of the
             * messages nor has an overlap between messages, the list is  not linear anymore. */
            mutableState.gapsInMessageList.value?.first != true &&
                moreMessagesAvailable &&
                !newMessages.hasMessageOverlap(messageIdsAboveGap) &&
                canCreateGap -> {
                Log.d("GapLogic", "A gap has started in new messages!!")

                gapDivisorMessage = newMessages.first()

                addNewerGapMessages(gapDivisorMessage, newMessages)
                mutableState._gapsInMessageList.value =
                    true to MessagesGapInfo(messageIdsAboveGap, messageIdsBellowGap, messagesBellowGap)
            }

            // Has gaps and loading more messages
            mutableState.gapsInMessageList.value?.first == true -> {
                addNewerGapMessages(gapDivisorMessage, newMessages)
                Log.d("GapLogic", "A gap keeps opened in new messages!!")
                mutableState._gapsInMessageList.value =
                    true to MessagesGapInfo(messageIdsAboveGap, messageIdsBellowGap, messagesBellowGap)
            }

            else -> {
                logger.logD("Unexpected state with gaps")
            }
        }
    }

    internal fun handleOlderMessagesLimit(
        moreMessagesAvailable: Boolean,
        newMessages: List<Message>,
    ) {
        when {
            /* The messages list has gaps but the end of messages of an overlap was found.
             * The message list is linear again. */
            mutableState._gapsInMessageList.value?.first == true &&
                (!moreMessagesAvailable || newMessages.hasMessageOverlap(messageIdsBellowGap)) -> {
                Log.d("GapLogic", "The gap has been closed in the older messages")
                gapDivisorMessage = null
                mutableState._gapsInMessageList.value = false to null

                messageIdsAboveGap.clear()
            }

            // Has gaps and loading more messages
            mutableState._gapsInMessageList.value?.first == true -> {
                Log.d("GapLogic", "has gaps and loading more messages")
                addOlderGapMessages(gapDivisorMessage, newMessages)
                mutableState._gapsInMessageList.value =
                    true to MessagesGapInfo(messageIdsAboveGap, messageIdsBellowGap, messagesBellowGap)
            }

            /* No gaps so far, only adding normal messages. They will be all considered at bellow the gap */
            mutableState._gapsInMessageList.value?.first != true -> {
                Log.d("GapLogic", "No gaps, adding normal messages")
                addOlderGapMessages(gapDivisorMessage, newMessages)
            }

            else -> {
                logger.logD("Unexpected state with gaps")
            }
        }
    }

    private fun addOlderGapMessages(gapDivisor: Message?, newMessages: List<Message>) {
        val hasGap = mutableState.gapsInMessageList.value?.first

        when {
            hasGap == true && gapDivisor != null -> {
                Log.d("GapLogic", "Adding older gap messages because has gap.")
                val filtered = newMessages.filter { message -> message.createdAt?.before(gapDivisor.createdAt) == true }
                val bellowGap = filtered.map { message -> message.id.hashCode().toLong() }

                messageIdsBellowGap.addAll(bellowGap)
                messagesBellowGap.addAll(filtered)

                val joint = messagesBellowGap.joinToString { message -> "${message.createdAt}" }
                Log.d("GapLogic", "Gap divisor: ${gapDivisor.createdAt}")
                Log.d("GapLogic", "All messages bellow gap: $joint")
            }

            hasGap != true -> {
                Log.d("GapLogic", "Adding older gap messages without gaps.")
                val bellowGap = newMessages.map { message -> message.id.hashCode().toLong() }
                messageIdsBellowGap.addAll(bellowGap)
                messagesBellowGap.addAll(newMessages)

                val joint = messagesBellowGap.joinToString { message -> "${message.text}|${message.createdAt}" }

                Log.d("GapLogic", "All messages bellow gap: $joint")
            }

            else -> {
                Log.d("GapLogic",
                    "No messages were updated at the gap!. hasGaps: $hasGap, has divisor: ${gapDivisor != null}")
            }
        }
    }

    private fun addNewerGapMessages(gapDivisor: Message?, newMessages: List<Message>) {
        if (gapDivisor != null) {
            val filtered = newMessages.filter { message ->
                message.createdAt?.after(gapDivisor.createdAt) == true && message != gapDivisorMessage
            }

            val ids = filtered.map { message ->
                message.id.hashCode().toLong()
            }

            messagesBellowGap.addAll(filtered)
            messageIdsBellowGap.addAll(ids)
        }
    }

    private fun List<Message>.hasMessageOverlap(idsList: List<Long>): Boolean {
        return this.map { it.id.hashCode().toLong() }.any(idsList::contains).also { hasGap ->
            if (hasGap) {
                val size = this.map { it.id.hashCode().toLong() }
                    .filter(idsList::contains)
                    .size

                Log.d("GapLogic", "gap match size: $size")
            }
        }
    }
}
