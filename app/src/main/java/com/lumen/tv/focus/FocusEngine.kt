package com.lumen.tv.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect

enum class LauncherPage { HOME, APPS, SETTINGS }

/**
 * FocusEngine tracks the active page, stores the last focused item per page,
 * and handles initial focus placement on page switch.
 */
class FocusEngine {
    // Page focus restoration storage
    private val lastFocusedKey = mutableMapOf<LauncherPage, String>()

    // Registered candidate bounds for spatial calculations if needed
    val registeredBounds = mutableMapOf<String, Rect>()

    fun recordFocus(page: LauncherPage, itemKey: String) {
        lastFocusedKey[page] = itemKey
    }

    fun getLastFocused(page: LauncherPage): String? {
        return lastFocusedKey[page]
    }
}

@Composable
fun rememberFocusEngine(): FocusEngine {
    return remember { FocusEngine() }
}
