/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

/**
 * Represents actions that can be triggered from SettingsActivity.
 * These are one-shot commands (not persistent settings) that should be
 * communicated back to MainActivity via Activity results.
 */
sealed class SettingsAction {
    object LoadModel : SettingsAction()
    object ClearChatHistory : SettingsAction()

    companion object {
        const val EXTRA_ACTION = "settings_action"
        const val ACTION_LOAD_MODEL = "load_model"
        const val ACTION_CLEAR_CHAT = "clear_chat"

        fun fromExtra(action: String?): SettingsAction? {
            return when (action) {
                ACTION_LOAD_MODEL -> LoadModel
                ACTION_CLEAR_CHAT -> ClearChatHistory
                else -> null
            }
        }

        fun toExtra(action: SettingsAction): String {
            return when (action) {
                is LoadModel -> ACTION_LOAD_MODEL
                is ClearChatHistory -> ACTION_CLEAR_CHAT
            }
        }
    }
}
