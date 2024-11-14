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

package io.getstream.chat.android.compose.ui.theme

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.RippleConfiguration
import androidx.compose.material.RippleDefaults
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Configuration for customizing the ripple effect on the composable components.
 *
 * @param color The color of the ripple effect.
 * @param rippleAlpha The alpha of the ripple effect.
 */
public class StreamRippleConfiguration(
    public val color: Color,
    public val rippleAlpha: RippleAlpha,
) {

    public companion object {

        /**
         * Creates the default [StreamRippleConfiguration].
         *
         * @param contentColor The current content color.
         * @param lightTheme Indicator if the system is in light theme.
         */
        @Composable
        public fun defaultRippleConfiguration(contentColor: Color, lightTheme: Boolean): StreamRippleConfiguration =
            StreamRippleConfiguration(
                color = RippleDefaults.rippleColor(contentColor, lightTheme),
                rippleAlpha = RippleDefaults.rippleAlpha(contentColor, lightTheme),
            )
    }
}

/**
 * Maps a [StreamRippleConfiguration] to the android [RippleConfiguration].
 * Utility method serving as a workaround for [RippleConfiguration] being experimental until compose v1.8.0,
 * so that we can opt-in internally, instead of exposing the experimental api outside of the [ChatTheme].
 */
@OptIn(ExperimentalMaterialApi::class)
internal fun StreamRippleConfiguration.toRippleConfiguration(): RippleConfiguration =
    RippleConfiguration(color = color, rippleAlpha = rippleAlpha)
