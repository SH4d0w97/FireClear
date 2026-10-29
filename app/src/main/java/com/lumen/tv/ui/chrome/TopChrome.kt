package com.lumen.tv.ui.chrome

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.tv.data.ConnectionState
import com.lumen.tv.focus.LauncherPage
import com.lumen.tv.ui.theme.Tokens
import com.lumen.tv.ui.theme.blurCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global Top Chrome: 64dp height, focusable via D-pad remote.
 * Left: Live clock (15s ticker, "h:mm a")
 * Center: Segmented tabs ("Home / Apps / Settings") with 30dp gap
 * Right: Wi-Fi chip (SSID, pill, fill white 6%, blur 18dp)
 */
@Composable
fun TopChrome(
    activePage: LauncherPage,
    connectionState: ConnectionState,
    onPageSelected: (LauncherPage) -> Unit,
    onWifiChipClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf("") }

    // Live clock ticker (15s interval)
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        while (true) {
            currentTime = fmt.format(Date())
            delay(15_000)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = Tokens.OverscanHorizontal)
    ) {
        // 1. Left: Live Clock
        Text(
            text = currentTime,
            color = Tokens.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // 2. Center: Tabs (Home / Apps / Settings) with 30dp gap
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(30.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LauncherPage.values().forEach { page ->
                val isActive = activePage == page
                var isFocused by remember { mutableStateOf(false) }

                val textColor by animateColorAsState(
                    targetValue = when {
                        isFocused -> Color.White
                        isActive -> Tokens.TextPrimary
                        else -> Tokens.TextTertiary
                    },
                    animationSpec = Tokens.MotionColorSpec,
                    label = "tabTextCol"
                )

                Box(
                    modifier = Modifier
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onPageSelected(page) }
                        .focusable()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (page) {
                            LauncherPage.HOME -> "Home"
                            LauncherPage.APPS -> "Apps"
                            LauncherPage.SETTINGS -> "Settings"
                        },
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Right: Wi-Fi Chip (Glass pill, blur 18dp, hairline border)
        var isWifiFocused by remember { mutableStateOf(false) }
        val wifiBorderColor = if (isWifiFocused) Color.White.copy(alpha = 0.32f) else Tokens.Hairline
        val wifiTextColor = if (isWifiFocused) Color.White else Tokens.TextSecondary
        val wifiAlpha = if (connectionState.isWifiOn) 1.0f else 0.50f

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clip(CircleShape)
                .blurCompat(18.dp)
                .background(Tokens.GlassFill)
                .border(1.dp, wifiBorderColor, CircleShape)
                .onFocusChanged { isWifiFocused = it.isFocused }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onWifiChipClicked() }
                .focusable()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (connectionState.isWifiOn) Icons.Default.Wifi else Icons.Default.WifiOff,
                contentDescription = "Wi-Fi Status",
                tint = wifiTextColor.copy(alpha = wifiAlpha),
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = connectionState.wifiSsid,
                color = wifiTextColor.copy(alpha = wifiAlpha),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
