/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

/**
 * Holds app-wide settings that are independent of the current module/model.
 */
data class AppSettings(
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val saveChatHistory: Boolean = false
)
