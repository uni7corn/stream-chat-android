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

package io.getstream.chat.android.compose.ui.messages.composer

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHost
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Bottom
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.getstream.sdk.chat.utils.MediaStringUtil
import io.getstream.chat.android.client.models.Attachment
import io.getstream.chat.android.client.models.ChannelCapabilities
import io.getstream.chat.android.client.models.Command
import io.getstream.chat.android.client.models.Message
import io.getstream.chat.android.client.models.User
import io.getstream.chat.android.common.composer.MessageComposerState
import io.getstream.chat.android.common.state.Edit
import io.getstream.chat.android.common.state.MessageMode
import io.getstream.chat.android.common.state.ValidationError
import io.getstream.chat.android.compose.R
import io.getstream.chat.android.compose.ui.components.composer.CoolDownIndicator
import io.getstream.chat.android.compose.ui.components.composer.MessageInput
import io.getstream.chat.android.compose.ui.components.composer.MessageInputOptions
import io.getstream.chat.android.compose.ui.components.suggestions.commands.CommandSuggestionList
import io.getstream.chat.android.compose.ui.components.suggestions.mentions.MentionSuggestionList
import io.getstream.chat.android.compose.ui.theme.ChatTheme
import io.getstream.chat.android.compose.ui.util.AboveAnchorPopupPositionProvider
import io.getstream.chat.android.compose.ui.util.mirrorRtl
import io.getstream.chat.android.compose.viewmodel.messages.MessageComposerViewModel

/**
 * Default MessageComposer component that relies on [MessageComposerViewModel] to handle data and
 * communicate various events.
 *
 * @param viewModel The ViewModel that provides pieces of data to show in the composer, like the
 * currently selected integration data or the user input. It also handles sending messages.
 * @param modifier Modifier for styling.
 * @param onSendMessage Handler when the user sends a message. By default it delegates this to the
 * ViewModel, but the user can override if they want more custom behavior.
 * @param onAttachmentsClick Handler for the default Attachments integration.
 * @param onCommandsClick Handler for the default Commands integration.
 * @param onValueChange Handler when the input field value changes.
 * @param onAttachmentRemoved Handler when the user taps on the cancel/delete attachment action.
 * @param onCancelAction Handler for the cancel button on Message actions, such as Edit and Reply.
 * @param onMentionSelected Handler when the user taps on a mention suggestion item.
 * @param onCommandSelected Handler when the user taps on a command suggestion item.
 * @param onAlsoSendToChannelSelected Handler when the user checks the also send to channel checkbox.
 * @param headerContent The content shown at the top of the message composer.
 * @param footerContent The content shown at the bottom of the message composer.
 * @param mentionPopupContent Customizable composable that represents the mention suggestions popup.
 * @param commandPopupContent Customizable composable that represents the instant command suggestions popup.
 * @param integrations A view that represents custom integrations. By default, we provide
 * [DefaultComposerIntegrations], which show Attachments & Giphy, but users can override this with
 * their own integrations, which they need to hook up to their own data providers and UI.
 * @param label Customizable composable that represents the input field label (hint).
 * @param input Customizable composable that represents the input field for the composer, [MessageInput] by default.
 * @param trailingContent Customizable composable that represents the trailing content of the composer, send button
 * by default.
 */
@Composable
public fun MessageComposer(
    viewModel: MessageComposerViewModel,
    modifier: Modifier = Modifier,
    onSendMessage: (Message) -> Unit = { viewModel.sendMessage(it) },
    onAttachmentsClick: () -> Unit = {},
    onCommandsClick: () -> Unit = {},
    onValueChange: (String) -> Unit = { viewModel.setMessageInput(it) },
    onAttachmentRemoved: (Attachment) -> Unit = { viewModel.removeSelectedAttachment(it) },
    onCancelAction: () -> Unit = { viewModel.dismissMessageActions() },
    onMentionSelected: (User) -> Unit = { viewModel.selectMention(it) },
    onCommandSelected: (Command) -> Unit = { viewModel.selectCommand(it) },
    onAlsoSendToChannelSelected: (Boolean) -> Unit = { viewModel.setAlsoSendToChannel(it) },
    headerContent: @Composable ColumnScope.(MessageComposerState) -> Unit = {
        DefaultMessageComposerHeaderContent(
            messageComposerState = it,
            onCancelAction = onCancelAction
        )
    },
    footerContent: @Composable ColumnScope.(MessageComposerState) -> Unit = {
        DefaultMessageComposerFooterContent(
            messageComposerState = it,
            onAlsoSendToChannelSelected = onAlsoSendToChannelSelected
        )
    },
    mentionPopupContent: @Composable (List<User>) -> Unit = {
        DefaultMentionPopupContent(
            mentionSuggestions = it,
            onMentionSelected = onMentionSelected
        )
    },
    commandPopupContent: @Composable (List<Command>) -> Unit = {
        DefaultCommandPopupContent(
            commandSuggestions = it,
            onCommandSelected = onCommandSelected
        )
    },
    integrations: @Composable RowScope.(MessageComposerState) -> Unit = {
        DefaultComposerIntegrations(
            messageInputState = it,
            onAttachmentsClick = onAttachmentsClick,
            onCommandsClick = onCommandsClick,
            ownCapabilities = it.ownCapabilities
        )
    },
    label: @Composable (MessageComposerState) -> Unit = { DefaultComposerLabel(it.ownCapabilities) },
    input: @Composable RowScope.(MessageComposerState) -> Unit = {
        DefaultComposerInputContent(
            messageComposerState = it,
            onValueChange = onValueChange,
            onAttachmentRemoved = onAttachmentRemoved,
            label = label,
        )
    },
    trailingContent: @Composable (MessageComposerState) -> Unit = {
        DefaultMessageComposerTrailingContent(
            value = it.inputValue,
            coolDownTime = it.coolDownTime,
            validationErrors = it.validationErrors,
            attachments = it.attachments,
            ownCapabilities = it.ownCapabilities,
            onSendMessage = { input, attachments ->
                val message = viewModel.buildNewMessage(input, attachments)

                onSendMessage(message)
            }
        )
    },
) {
    val messageComposerState by viewModel.messageComposerState.collectAsState()

    MessageComposer(
        modifier = modifier,
        onSendMessage = { text, attachments ->
            val messageWithData = viewModel.buildNewMessage(text, attachments)

            onSendMessage(messageWithData)
        },
        onMentionSelected = onMentionSelected,
        onCommandSelected = onCommandSelected,
        onAlsoSendToChannelSelected = onAlsoSendToChannelSelected,
        headerContent = headerContent,
        footerContent = footerContent,
        mentionPopupContent = mentionPopupContent,
        commandPopupContent = commandPopupContent,
        integrations = integrations,
        input = input,
        trailingContent = trailingContent,
        messageComposerState = messageComposerState,
        onCancelAction = onCancelAction
    )
}

/**
 * Clean version of the [MessageComposer] that doesn't rely on ViewModels, so the user can provide a
 * manual way to handle and represent data and various operations.
 *
 * @param messageComposerState The state of the message input.
 * @param onSendMessage Handler when the user wants to send a message.
 * @param modifier Modifier for styling.
 * @param onAttachmentsClick Handler for the default Attachments integration.
 * @param onCommandsClick Handler for the default Commands integration.
 * @param onValueChange Handler when the input field value changes.
 * @param onAttachmentRemoved Handler when the user taps on the cancel/delete attachment action.
 * @param onCancelAction Handler for the cancel button on Message actions, such as Edit and Reply.
 * @param onMentionSelected Handler when the user taps on a mention suggestion item.
 * @param onCommandSelected Handler when the user taps on a command suggestion item.
 * @param onAlsoSendToChannelSelected Handler when the user checks the also send to channel checkbox.
 * @param headerContent The content shown at the top of the message composer.
 * @param footerContent The content shown at the bottom of the message composer.
 * @param mentionPopupContent Customizable composable that represents the mention suggestions popup.
 * @param commandPopupContent Customizable composable that represents the instant command suggestions popup.
 * @param integrations A view that represents custom integrations. By default, we provide
 * [DefaultComposerIntegrations], which show Attachments & Giphy, but users can override this with
 * their own integrations, which they need to hook up to their own data providers and UI.
 * @param label Customizable composable that represents the input field label (hint).
 * @param input Customizable composable that represents the input field for the composer, [MessageInput] by default.
 * @param trailingContent Customizable composable that represents the trailing content of the composer, send button
 * by default.
 */
@Composable
public fun MessageComposer(
    messageComposerState: MessageComposerState,
    onSendMessage: (String, List<Attachment>) -> Unit,
    modifier: Modifier = Modifier,
    onAttachmentsClick: () -> Unit = {},
    onCommandsClick: () -> Unit = {},
    onValueChange: (String) -> Unit = {},
    onAttachmentRemoved: (Attachment) -> Unit = {},
    onCancelAction: () -> Unit = {},
    onMentionSelected: (User) -> Unit = {},
    onCommandSelected: (Command) -> Unit = {},
    onAlsoSendToChannelSelected: (Boolean) -> Unit = {},
    headerContent: @Composable ColumnScope.(MessageComposerState) -> Unit = {
        DefaultMessageComposerHeaderContent(
            messageComposerState = it,
            onCancelAction = onCancelAction,
        )
    },
    footerContent: @Composable ColumnScope.(MessageComposerState) -> Unit = {
        DefaultMessageComposerFooterContent(
            messageComposerState = it,
            onAlsoSendToChannelSelected = onAlsoSendToChannelSelected,
        )
    },
    mentionPopupContent: @Composable (List<User>) -> Unit = {
        DefaultMentionPopupContent(
            mentionSuggestions = it,
            onMentionSelected = onMentionSelected
        )
    },
    commandPopupContent: @Composable (List<Command>) -> Unit = {
        DefaultCommandPopupContent(
            commandSuggestions = it,
            onCommandSelected = onCommandSelected
        )
    },
    integrations: @Composable RowScope.(MessageComposerState) -> Unit = {
        DefaultComposerIntegrations(
            messageInputState = it,
            onAttachmentsClick = onAttachmentsClick,
            onCommandsClick = onCommandsClick,
            ownCapabilities = messageComposerState.ownCapabilities
        )
    },
    label: @Composable (MessageComposerState) -> Unit = { DefaultComposerLabel(messageComposerState.ownCapabilities) },
    input: @Composable RowScope.(MessageComposerState) -> Unit = {
        DefaultComposerInputContent(
            messageComposerState = messageComposerState,
            onValueChange = onValueChange,
            onAttachmentRemoved = onAttachmentRemoved,
            label = label,
        )
    },
    trailingContent: @Composable (MessageComposerState) -> Unit = {
        DefaultMessageComposerTrailingContent(
            value = it.inputValue,
            coolDownTime = it.coolDownTime,
            validationErrors = it.validationErrors,
            attachments = it.attachments,
            onSendMessage = onSendMessage,
            ownCapabilities = messageComposerState.ownCapabilities
        )
    },
) {
    val (_, attachments, activeAction, validationErrors, mentionSuggestions, commandSuggestions) = messageComposerState
    val snackbarHostState = remember { SnackbarHostState() }

    MessageInputValidationError(
        validationErrors = validationErrors,
        snackbarHostState = snackbarHostState
    )

    Surface(
        modifier = modifier,
        elevation = 4.dp,
        color = ChatTheme.colors.barsBackground,
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            headerContent(messageComposerState)

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Bottom
            ) {

                if (activeAction !is Edit) {
                    integrations(messageComposerState)
                } else {
                    Spacer(
                        modifier = Modifier.size(16.dp)
                    )
                }

                input(messageComposerState)

                trailingContent(messageComposerState)
            }

            footerContent(messageComposerState)
        }

        if (snackbarHostState.currentSnackbarData != null) {
            SnackbarPopup(snackbarHostState = snackbarHostState)
        }

        if (mentionSuggestions.isNotEmpty()) {
            mentionPopupContent(mentionSuggestions)
        }

        if (commandSuggestions.isNotEmpty() && attachments.isEmpty()) {
            commandPopupContent(commandSuggestions)
        }
    }
}

/**
 * Represents the default content shown at the top of the message composer component.
 *
 * @param messageComposerState The state of the message composer.
 * @param onCancelAction Handler for the cancel button on Message actions, such as Edit and Reply.
 */
@Composable
public fun DefaultMessageComposerHeaderContent(
    messageComposerState: MessageComposerState,
    onCancelAction: () -> Unit,
) {
    val activeAction = messageComposerState.action

    if (activeAction != null) {
        MessageInputOptions(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp, start = 8.dp, end = 8.dp),
            activeAction = activeAction,
            onCancelAction = onCancelAction
        )
    }
}

/**
 * Represents the default content shown at the bottom of the message composer component.
 *
 * @param messageComposerState The state of the message composer.
 * @param onAlsoSendToChannelSelected Handler when the user checks the also send to channel checkbox.
 */
@Composable
public fun DefaultMessageComposerFooterContent(
    messageComposerState: MessageComposerState,
    onAlsoSendToChannelSelected: (Boolean) -> Unit,
) {
    if (messageComposerState.messageMode is MessageMode.MessageThread) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = messageComposerState.alsoSendToChannel,
                onCheckedChange = { onAlsoSendToChannelSelected(it) },
                colors = CheckboxDefaults.colors(ChatTheme.colors.primaryAccent)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = stringResource(R.string.stream_compose_message_composer_show_in_channel),
                color = ChatTheme.colors.textLowEmphasis,
                textAlign = TextAlign.Center,
                style = ChatTheme.typography.body
            )
        }
    }
}

/**
 * Represents the default mention suggestion list popup shown above the message composer.
 *
 * @param mentionSuggestions The list of users that can be used to autocomplete the current mention input.
 * @param onMentionSelected Handler when the user taps on a mention suggestion item.
 */
@Composable
internal fun DefaultMentionPopupContent(
    mentionSuggestions: List<User>,
    onMentionSelected: (User) -> Unit,
) {
    MentionSuggestionList(
        users = mentionSuggestions,
        onMentionSelected = { onMentionSelected(it) }
    )
}

/**
 * Represents the default command suggestion list popup shown above the message composer.
 *
 * @param commandSuggestions The list of available commands in the channel.
 * @param onCommandSelected Handler when the user taps on a command suggestion item.
 */
@Composable
internal fun DefaultCommandPopupContent(
    commandSuggestions: List<Command>,
    onCommandSelected: (Command) -> Unit,
) {
    CommandSuggestionList(
        commands = commandSuggestions,
        onCommandSelected = { onCommandSelected(it) }
    )
}

/**
 * Composable that represents the message composer integrations (special actions).
 *
 * Currently just shows the Attachment picker action.
 *
 * @param messageInputState The state of the input.
 * @param onAttachmentsClick Handler when the user selects attachments.
 * @param onCommandsClick Handler when the user selects commands.
 * @param ownCapabilities Set of capabilities the user is given for the current channel.
 * For a full list @see [io.getstream.chat.android.client.models.ChannelCapabilities].
 */
@Composable
internal fun DefaultComposerIntegrations(
    messageInputState: MessageComposerState,
    onAttachmentsClick: () -> Unit,
    onCommandsClick: () -> Unit,
    ownCapabilities: Set<String>,
) {
    val hasTextInput = messageInputState.inputValue.isNotEmpty()
    val hasAttachments = messageInputState.attachments.isNotEmpty()
    val hasCommandInput = messageInputState.inputValue.startsWith("/")
    val hasCommandSuggestions = messageInputState.commandSuggestions.isNotEmpty()
    val hasMentionSuggestions = messageInputState.mentionSuggestions.isNotEmpty()

    val isAttachmentsButtonEnabled = !hasCommandInput && !hasCommandSuggestions && !hasMentionSuggestions
    val isCommandsButtonEnabled = !hasTextInput && !hasAttachments

    val canSendMessage = ownCapabilities.contains(ChannelCapabilities.SEND_MESSAGE)
    val canSendAttachments = ownCapabilities.contains(ChannelCapabilities.UPLOAD_FILE)

    if (canSendMessage) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canSendAttachments) {
                IconButton(
                    enabled = isAttachmentsButtonEnabled,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(4.dp),
                    content = {
                        Icon(
                            painter = painterResource(id = R.drawable.stream_compose_ic_attachments),
                            contentDescription = stringResource(id = R.string.stream_compose_attachments),
                            tint = if (isAttachmentsButtonEnabled) {
                                ChatTheme.colors.textLowEmphasis
                            } else {
                                ChatTheme.colors.disabled
                            },
                        )
                    },
                    onClick = onAttachmentsClick
                )
            }

            val commandsButtonTint = if (hasCommandSuggestions && isCommandsButtonEnabled) {
                ChatTheme.colors.primaryAccent
            } else if (isCommandsButtonEnabled) {
                ChatTheme.colors.textLowEmphasis
            } else {
                ChatTheme.colors.disabled
            }

            IconButton(
                modifier = Modifier
                    .size(32.dp)
                    .padding(4.dp),
                enabled = isCommandsButtonEnabled,
                content = {
                    Icon(
                        painter = painterResource(id = R.drawable.stream_compose_ic_command),
                        contentDescription = null,
                        tint = commandsButtonTint,
                    )
                },
                onClick = onCommandsClick
            )
        }
    } else {
        Spacer(modifier = Modifier.width(12.dp))
    }
}

/**
 * Default input field label that the user can override in [MessageComposer].
 *
 * @param ownCapabilities Set of capabilities the user is given for the current channel.
 * For a full list @see [io.getstream.chat.android.client.models.ChannelCapabilities].
 */
@Composable
internal fun DefaultComposerLabel(ownCapabilities: Set<String>) {
    val text =
        if (ownCapabilities.contains(ChannelCapabilities.SEND_MESSAGE)) {
            stringResource(id = R.string.stream_compose_message_label)
        } else {
            stringResource(id = R.string.stream_compose_cannot_send_messages_label)
        }

    Text(
        text = text,
        color = ChatTheme.colors.textLowEmphasis
    )
}

/**
 * Represents the default input content of the Composer.
 *
 * @param label Customizable composable that represents the input field label (hint).
 * @param messageComposerState The state of the message input.
 * @param onValueChange Handler when the input field value changes.
 * @param onAttachmentRemoved Handler when the user taps on the cancel/delete attachment action.
 */
@Composable
public fun RowScope.DefaultComposerInputContent(
    messageComposerState: MessageComposerState,
    onValueChange: (String) -> Unit,
    onAttachmentRemoved: (Attachment) -> Unit,
    label: @Composable (MessageComposerState) -> Unit,
) {
    MessageInput(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .weight(1f),
        label = label,
        messageComposerState = messageComposerState,
        onValueChange = onValueChange,
        onAttachmentRemoved = onAttachmentRemoved,
    )
}

/**
 * Represents the default trailing content for the Composer, which represent a send button or a cooldown timer.
 *
 * @param value The input value.
 * @param coolDownTime The amount of time left in cool-down mode.
 * @param attachments The selected attachments.
 * @param validationErrors List of errors for message validation.
 * @param onSendMessage Handler when the user wants to send a message.
 * @param ownCapabilities Set of capabilities the user is given for the current channel.
 * For a full list @see [io.getstream.chat.android.client.models.ChannelCapabilities].
 */
@Composable
internal fun DefaultMessageComposerTrailingContent(
    value: String,
    coolDownTime: Int,
    attachments: List<Attachment>,
    validationErrors: List<ValidationError>,
    ownCapabilities: Set<String>,
    onSendMessage: (String, List<Attachment>) -> Unit,
) {
    val isSendButtonEnabled = ownCapabilities.contains(ChannelCapabilities.SEND_MESSAGE)
    val isInputValid by lazy { (value.isNotBlank() || attachments.isNotEmpty()) && validationErrors.isEmpty() }
    val description = stringResource(id = R.string.stream_compose_cd_send_button)

    if (coolDownTime > 0) {
        CoolDownIndicator(coolDownTime = coolDownTime)
    } else {
        IconButton(
            modifier = Modifier.semantics { contentDescription = description },
            enabled = isSendButtonEnabled && isInputValid,
            content = {
                val layoutDirection = LocalLayoutDirection.current

                Icon(
                    modifier = Modifier.mirrorRtl(layoutDirection = layoutDirection),
                    painter = painterResource(id = R.drawable.stream_compose_ic_send),
                    contentDescription = stringResource(id = R.string.stream_compose_send_message),
                    tint = if (isInputValid) ChatTheme.colors.primaryAccent else ChatTheme.colors.textLowEmphasis
                )
            },
            onClick = {
                if (isInputValid) {
                    onSendMessage(value, attachments)
                }
            }
        )
    }
}

/**
 * Shows a [Toast] with an error if one of the following constraints are violated:
 *
 * - The message length exceeds the maximum allowed message length.
 * - The number of selected attachments is too big.
 * - At least one of the attachments is too big.
 *
 * @param validationErrors The list of validation errors for the current user input.
 */
@Composable
private fun MessageInputValidationError(validationErrors: List<ValidationError>, snackbarHostState: SnackbarHostState) {
    if (validationErrors.isNotEmpty()) {
        val firstValidationError = validationErrors.first()

        val errorMessage = when (firstValidationError) {
            is ValidationError.MessageLengthExceeded -> {
                stringResource(
                    R.string.stream_compose_message_composer_error_message_length,
                    firstValidationError.maxMessageLength
                )
            }
            is ValidationError.AttachmentCountExceeded -> {
                stringResource(
                    R.string.stream_compose_message_composer_error_attachment_count,
                    firstValidationError.maxAttachmentCount
                )
            }
            is ValidationError.AttachmentSizeExceeded -> {
                stringResource(
                    R.string.stream_compose_message_composer_error_file_size,
                    MediaStringUtil.convertFileSizeByteCount(firstValidationError.maxAttachmentSize)
                )
            }
            is ValidationError.ContainsLinksWhenNotAllowed -> {
                stringResource(
                    R.string.stream_compose_message_composer_error_sending_links_not_allowed,
                )
            }
        }

        val context = LocalContext.current
        LaunchedEffect(validationErrors.size) {
            if (firstValidationError is ValidationError.ContainsLinksWhenNotAllowed) {
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    actionLabel = context.getString(R.string.stream_compose_ok),
                    duration = SnackbarDuration.Indefinite
                )
            } else {
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * A snackbar wrapped inside of a popup allowing it be
 * displayed above the Composable it's anchored to.
 *
 * @param snackbarHostState The state of the snackbar host. Contains
 * the snackbar data necessary to display the snackbar.
 */
@Composable
private fun SnackbarPopup(snackbarHostState: SnackbarHostState) {
    Popup(popupPositionProvider = AboveAnchorPopupPositionProvider()) {
        SnackbarHost(hostState = snackbarHostState)
    }
}
