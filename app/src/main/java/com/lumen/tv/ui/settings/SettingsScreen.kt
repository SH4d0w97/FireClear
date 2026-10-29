package com.lumen.tv.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lumen.tv.data.ConnectionState
import com.lumen.tv.data.SettingsStateHolder
import com.lumen.tv.data.WallpaperPreset
import com.lumen.tv.data.WallpaperRepository
import com.lumen.tv.ui.theme.Tokens
import com.lumen.tv.ui.theme.blurCompat
import kotlinx.coroutines.launch

/**
 * Page 3 — Settings
 * Column max 720dp centered (or 2-column if width >= 1100dp).
 * Vertical scroll allowed.
 * Real-time search filtering.
 */
@Composable
fun SettingsScreen(
    connectionState: ConnectionState,
    settingsHolder: SettingsStateHolder,
    wallpaperPreset: WallpaperPreset,
    isWallpaperDimmed: Boolean,
    wallpaperRepo: WallpaperRepository,
    wifiFocusRequester: FocusRequester? = null,
    searchFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var searchQuery by remember { mutableStateOf("") }
    val q = searchQuery.trim().lowercase()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Tokens.OverscanHorizontal)
    ) {
        val isWide = maxWidth >= 1100.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(top = 100.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val contentMaxWidth = if (isWide) 1460.dp else 720.dp

            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Title
                Text(
                    text = "Settings",
                    style = Tokens.PageTitle,
                    color = Tokens.TextPrimary
                )

                // Search Field (rounded 12dp, fill white 7%, magnifier)
                SettingsSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    focusRequester = searchFocusRequester
                )

                if (isWide) {
                    // Two-Column Grid of Groups
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(44.dp)
                    ) {
                        // Left Column: Account & Connections
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            RenderAccountGroup(q)
                            RenderConnectionsGroup(
                                q = q,
                                state = connectionState,
                                holder = settingsHolder,
                                wifiFocusRequester = wifiFocusRequester
                            )
                        }

                        // Right Column: Launcher & Device
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            RenderLauncherGroup(
                                q = q,
                                preset = wallpaperPreset,
                                isDimmed = isWallpaperDimmed,
                                onPresetSelected = {
                                    coroutineScope.launch { wallpaperRepo.setPreset(it) }
                                },
                                onDimmedChanged = {
                                    coroutineScope.launch { wallpaperRepo.setDimmed(it) }
                                }
                            )

                            RenderDeviceGroup(q, onOpenPlatformSettings = {
                                try {
                                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) { /* ignore */ }
                            })
                        }
                    }
                } else {
                    // Single Column
                    RenderAccountGroup(q)
                    RenderConnectionsGroup(
                        q = q,
                        state = connectionState,
                        holder = settingsHolder,
                        wifiFocusRequester = wifiFocusRequester
                    )
                    RenderLauncherGroup(
                        q = q,
                        preset = wallpaperPreset,
                        isDimmed = isWallpaperDimmed,
                        onPresetSelected = {
                            coroutineScope.launch { wallpaperRepo.setPreset(it) }
                        },
                        onDimmedChanged = {
                            coroutineScope.launch { wallpaperRepo.setDimmed(it) }
                        }
                    )
                    RenderDeviceGroup(q, onOpenPlatformSettings = {
                        try {
                            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) { /* ignore */ }
                    })
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester?
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Tokens.CardBorderFocused else Tokens.CardBorderHairline,
        label = "searchBorder"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.RadiusSearch))
            .blurCompat(Tokens.BlurGlass)
            .background(if (isFocused) Tokens.CardFillFocused else Color.White.copy(alpha = 0.07f))
            .border(1.dp, borderColor, RoundedCornerShape(Tokens.RadiusSearch))
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (isFocused) Tokens.TextPrimary else Tokens.TextTertiary,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = Tokens.RowLabelText.copy(color = Tokens.TextPrimary),
            cursorBrush = SolidColor(Tokens.TextPrimary),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search settings",
                        style = Tokens.RowLabelText,
                        color = Tokens.TextTertiary
                    )
                }
                innerTextField()
            }
        )

        if (query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear search",
                tint = Tokens.TextTertiary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section Rendering with Real-Time Search Filtering
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RenderAccountGroup(q: String) {
    val matches = q.isEmpty() || "account".contains(q) || "alex".contains(q) || "profile".contains(q)
    if (!matches) return

    GroupCard(title = "ACCOUNT") {
        SettingsRow(
            label = "Alex",
            value = "Family Organizer · Synced",
            showDivider = false,
            customLeading = {
                // 38dp purple-gradient avatar (#BF5AF2 → #5E5CE6) "A"
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Tokens.ChipPurple, Tokens.ChipIndigo)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "A",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        )
    }
}

@Composable
private fun RenderConnectionsGroup(
    q: String,
    state: ConnectionState,
    holder: SettingsStateHolder,
    wifiFocusRequester: FocusRequester?
) {
    val matchesAirplane = q.isEmpty() || "airplane".contains(q)
    val matchesWifi = q.isEmpty() || "wifi".contains(q) || "wi-fi".contains(q) || "internet".contains(q)
    val matchesBt = q.isEmpty() || "bluetooth".contains(q)
    val matchesFocus = q.isEmpty() || "focus".contains(q) || "do not disturb".contains(q)

    if (!matchesAirplane && !matchesWifi && !matchesBt && !matchesFocus) return

    GroupCard(title = "CONNECTIONS") {
        if (matchesAirplane) {
            SwitchRow(
                label = "Airplane Mode",
                checked = state.isAirplaneOn,
                onCheckedChange = { holder.toggleAirplane(it) },
                icon = Icons.Default.Flight,
                iconTint = Tokens.ChipOrange,
                showDivider = matchesWifi || matchesBt || matchesFocus
            )
        }

        if (matchesWifi) {
            Box(
                modifier = if (wifiFocusRequester != null) {
                    Modifier.focusRequester(wifiFocusRequester)
                } else {
                    Modifier
                }
            ) {
                SwitchRow(
                    label = "Wi-Fi",
                    valueText = state.wifiSsid,
                    checked = state.isWifiOn,
                    onCheckedChange = { holder.toggleWifi(it) },
                    icon = Icons.Default.Wifi,
                    iconTint = Tokens.ChipBlue,
                    showDivider = matchesBt || matchesFocus
                )
            }
        }

        if (matchesBt) {
            SwitchRow(
                label = "Bluetooth",
                valueText = if (state.isBluetoothOn) "On" else "Off",
                checked = state.isBluetoothOn,
                onCheckedChange = { holder.toggleBluetooth(it) },
                icon = Icons.Default.Bluetooth,
                iconTint = Tokens.ChipBlue,
                showDivider = matchesFocus
            )
        }

        if (matchesFocus) {
            SwitchRow(
                label = "Focus",
                checked = state.isFocusOn,
                onCheckedChange = { holder.toggleFocus(it) },
                icon = Icons.Default.DarkMode,
                iconTint = Tokens.ChipIndigo,
                showDivider = false
            )
        }
    }
}

@Composable
private fun RenderLauncherGroup(
    q: String,
    preset: WallpaperPreset,
    isDimmed: Boolean,
    onPresetSelected: (WallpaperPreset) -> Unit,
    onDimmedChanged: (Boolean) -> Unit
) {
    val matchesWallpaper = q.isEmpty() || "wallpaper".contains(q) || "theme".contains(q)
    val matchesDim = q.isEmpty() || "dim".contains(q) || "brightness".contains(q)

    if (!matchesWallpaper && !matchesDim) return

    GroupCard(
        title = "LAUNCHER",
        footnote = "Wallpaper and dimming apply to the launcher only — device display settings are unchanged."
    ) {
        if (matchesWallpaper) {
            WallpaperDisclosureRow(
                currentPreset = preset,
                onPresetSelected = onPresetSelected,
                showDivider = matchesDim
            )
        }

        if (matchesDim) {
            SwitchRow(
                label = "Dim Wallpaper",
                checked = isDimmed,
                onCheckedChange = onDimmedChanged,
                icon = Icons.Default.WbSunny,
                iconTint = Tokens.ChipSlate,
                showDivider = false
            )
        }
    }
}

@Composable
private fun RenderDeviceGroup(
    q: String,
    onOpenPlatformSettings: () -> Unit
) {
    val matchesDevice = q.isEmpty() || "device settings".contains(q) || "system".contains(q)
    val matchesDisplays = q.isEmpty() || "displays".contains(q) || "screen".contains(q) || "resolution".contains(q)
    val matchesSound = q.isEmpty() || "sound".contains(q) || "audio".contains(q)
    val matchesRemotes = q.isEmpty() || "remotes".contains(q) || "accessories".contains(q)
    val matchesUpdate = q.isEmpty() || "software update".contains(q) || "update".contains(q)

    if (!matchesDevice && !matchesDisplays && !matchesSound && !matchesRemotes && !matchesUpdate) return

    GroupCard(
        title = "DEVICE",
        footnote = "Lumen OS 2.1 · Model L-2025 · 128 GB"
    ) {
        if (matchesDevice) {
            SettingsRow(
                label = "Device Settings",
                value = "Open",
                icon = Icons.Default.Tune,
                iconTint = Tokens.ChipGray,
                showChevron = true,
                showDivider = matchesDisplays || matchesSound || matchesRemotes || matchesUpdate,
                onClick = onOpenPlatformSettings
            )
        }

        if (matchesDisplays) {
            SettingsRow(
                label = "Displays",
                value = "4K HDR",
                icon = Icons.Default.Tv,
                iconTint = Tokens.ChipTeal,
                showChevron = true,
                showDivider = matchesSound || matchesRemotes || matchesUpdate
            )
        }

        if (matchesSound) {
            SettingsRow(
                label = "Sound",
                value = "Atmos",
                icon = Icons.Default.VolumeUp,
                iconTint = Tokens.ChipPink,
                showChevron = true,
                showDivider = matchesRemotes || matchesUpdate
            )
        }

        if (matchesRemotes) {
            SettingsRow(
                label = "Remotes & Accessories",
                value = "2 connected",
                icon = Icons.Default.SettingsRemote,
                iconTint = Tokens.ChipPurple,
                showChevron = true,
                showDivider = matchesUpdate
            )
        }

        if (matchesUpdate) {
            SettingsRow(
                label = "Software Update",
                value = "Up to date",
                icon = Icons.Default.Sync,
                iconTint = Tokens.ChipGreen,
                showChevron = true,
                showDivider = false
            )
        }
    }
}
