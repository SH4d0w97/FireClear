package com.lumen.tv.data

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectionState(
    val isAirplaneOn: Boolean = false,
    val isWifiOn: Boolean = true,
    val isBluetoothOn: Boolean = true,
    val isFocusOn: Boolean = false,
    val wifiSsid: String = "Lumen-5G"
)

class SettingsStateHolder(private val context: Context) {
    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    fun refreshNetworkState() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            var ssid = "Lumen-5G"
            if (isWifi) {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val raw = wm.connectionInfo?.ssid?.replace("\"", "") ?: ""
                if (raw.isNotBlank() && raw != "<unknown ssid>") {
                    ssid = raw
                }
            }

            val airplaneOn = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0

            _state.value = _state.value.copy(
                isAirplaneOn = airplaneOn,
                isWifiOn = isWifi,
                wifiSsid = if (isWifi) ssid else "Wi-Fi Off"
            )
        } catch (e: Exception) {
            // Keep default state
        }
    }

    // Platform Truths: Optimistic UI transitions & reconciliation with system panels
    fun toggleAirplane(on: Boolean) {
        _state.value = _state.value.copy(
            isAirplaneOn = on,
            isWifiOn = if (on) false else _state.value.isWifiOn,
            isBluetoothOn = if (on) false else _state.value.isBluetoothOn
        )
        // Android 10+ forbids programmatic airplane mode: open system panel
        try {
            val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) { /* ignore */ }
    }

    fun toggleWifi(on: Boolean) {
        _state.value = _state.value.copy(
            isWifiOn = on,
            isAirplaneOn = if (on) false else _state.value.isAirplaneOn
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            try {
                val fallback = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (e2: Exception) { /* ignore */ }
        }
    }

    fun toggleBluetooth(on: Boolean) {
        _state.value = _state.value.copy(
            isBluetoothOn = on,
            isAirplaneOn = if (on) false else _state.value.isAirplaneOn
        )
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) { /* ignore */ }
    }

    fun toggleFocus(on: Boolean) {
        _state.value = _state.value.copy(isFocusOn = on)
    }
}
