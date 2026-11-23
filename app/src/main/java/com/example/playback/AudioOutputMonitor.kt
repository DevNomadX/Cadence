package com.example.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.data.StableList
import com.example.data.toStableList

@Keep
enum class AudioOutputRouteType {
    SPEAKER,
    WIRED_HEADSET,
    USB_AUDIO,
    BLUETOOTH,
    EXTERNAL
}

@Keep
@Immutable
data class AudioOutputDevice(
    val id: Int,
    val name: String,
    val routeType: AudioOutputRouteType,
    val isActive: Boolean
)

@Keep
@Immutable
data class AudioOutputState(
    val activeRoute: AudioOutputRouteType = AudioOutputRouteType.SPEAKER,
    val activeDeviceName: String = "Phone Speaker",
    val devices: StableList<AudioOutputDevice> = StableList(
        listOf(
            AudioOutputDevice(
                id = -1,
                name = "Phone Speaker",
                routeType = AudioOutputRouteType.SPEAKER,
                isActive = true
            )
        )
    )
)

/**
 * Lightweight, permission-free Android audio output route observer and switcher.
 * - Uses [AudioManager.getDevices] and lifecycle-bound [AudioDeviceCallback] (zero leaks).
 * - Tries Android's native system Media Output Switcher on Android 11+ (MediaRouter2 API 34+
 *   and SystemUI Media Output dialog when resolvable) and returns false when unavailable so
 *   callers can immediately present the lightweight Material 3 fallback sheet.
 */
object AudioOutputMonitor {

    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG =
        "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG"

    fun queryAudioOutputState(context: Context): AudioOutputState {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val rawDevices = runCatching {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        }.getOrDefault(emptyArray())

        return resolveStateFromRawDevices(
            rawDevices = rawDevices,
            systemRouteHint = resolveSystemSelectedRouteHint(context)
        )
    }

    internal fun resolveStateFromRawDevices(
        rawDevices: Array<out AudioDeviceInfo>,
        systemRouteHint: Pair<AudioOutputRouteType, String>? = null
    ): AudioOutputState {
        val seenKeys = HashSet<String>(8)
        val candidates = ArrayList<AudioOutputDevice>(6)

        for (info in rawDevices) {
            val routeType = mapDeviceType(info.type) ?: continue
            val friendlyName = resolveDeviceName(info, routeType)
            val dedupeKey = "${routeType.name}:${friendlyName.lowercase()}"
            if (seenKeys.add(dedupeKey)) {
                candidates += AudioOutputDevice(
                    id = info.id,
                    name = friendlyName,
                    routeType = routeType,
                    isActive = false
                )
            }
        }

        if (candidates.none { it.routeType == AudioOutputRouteType.SPEAKER }) {
            candidates += AudioOutputDevice(
                id = -1,
                name = "Phone Speaker",
                routeType = AudioOutputRouteType.SPEAKER,
                isActive = false
            )
        }

        // Order connected external routes first, then Phone Speaker
        val sorted = candidates.sortedWith(
            compareBy<AudioOutputDevice> { routePriority(it.routeType) }.thenBy { it.name }
        )

        val activeDevice = when {
            systemRouteHint != null -> {
                sorted.firstOrNull {
                    it.routeType == systemRouteHint.first &&
                        it.name.equals(systemRouteHint.second, ignoreCase = true)
                } ?: sorted.firstOrNull { it.routeType == systemRouteHint.first }
                    ?: sorted.first()
            }
            else -> sorted.first()
        }

        val marked = sorted.map { device ->
            device.copy(isActive = device.id == activeDevice.id && device.routeType == activeDevice.routeType)
        }

        return AudioOutputState(
            activeRoute = activeDevice.routeType,
            activeDeviceName = activeDevice.name,
            devices = marked.toStableList()
        )
    }

    /**
     * Attempts to launch Android's native system Media Output Switcher dialog.
     * Returns true if the system UI accepted the request, or false to trigger our M3 fallback sheet.
     */
    fun openSystemOutputSwitcher(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val shown = runCatching {
                MediaRouter2.getInstance(context).showSystemOutputSwitcher()
            }.getOrDefault(false)
            if (shown) return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(ACTION_LAUNCH_MEDIA_OUTPUT_DIALOG)
                .setPackage(SYSTEM_UI_PACKAGE)
                .putExtra("package_name", context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            val resolvable = runCatching {
                context.packageManager.resolveActivity(intent, 0) != null
            }.getOrDefault(false)

            if (resolvable) {
                val started = runCatching {
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
                if (started) return true
            }
        }

        return false
    }

    fun openSystemAudioSettings(context: Context, openBluetooth: Boolean = false) {
        val action = if (openBluetooth) {
            Settings.ACTION_BLUETOOTH_SETTINGS
        } else {
            Settings.ACTION_SOUND_SETTINGS
        }
        runCatching {
            context.startActivity(
                Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun resolveSystemSelectedRouteHint(context: Context): Pair<AudioOutputRouteType, String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            val controller = MediaRouter2.getInstance(context).systemController
            val route = controller.selectedRoutes.firstOrNull() ?: return null
            val routeType = when (route.type) {
                android.media.MediaRoute2Info.TYPE_BLUETOOTH_A2DP,
                android.media.MediaRoute2Info.TYPE_BLE_HEADSET,
                android.media.MediaRoute2Info.TYPE_HEARING_AID -> AudioOutputRouteType.BLUETOOTH
                android.media.MediaRoute2Info.TYPE_USB_HEADSET,
                android.media.MediaRoute2Info.TYPE_USB_DEVICE,
                android.media.MediaRoute2Info.TYPE_USB_ACCESSORY -> AudioOutputRouteType.USB_AUDIO
                android.media.MediaRoute2Info.TYPE_WIRED_HEADPHONES,
                android.media.MediaRoute2Info.TYPE_WIRED_HEADSET -> AudioOutputRouteType.WIRED_HEADSET
                android.media.MediaRoute2Info.TYPE_HDMI,
                android.media.MediaRoute2Info.TYPE_HDMI_ARC,
                android.media.MediaRoute2Info.TYPE_HDMI_EARC -> AudioOutputRouteType.EXTERNAL
                android.media.MediaRoute2Info.TYPE_BUILTIN_SPEAKER -> AudioOutputRouteType.SPEAKER
                else -> return null
            }
            val label = route.name.toString().trim().ifEmpty { defaultLabelFor(routeType) }
            routeType to label
        }.getOrNull()
    }

    internal fun mapDeviceType(deviceType: Int): AudioOutputRouteType? = when (deviceType) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_HEARING_AID -> AudioOutputRouteType.BLUETOOTH
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioOutputRouteType.USB_AUDIO
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_AUX_LINE -> AudioOutputRouteType.WIRED_HEADSET
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC -> AudioOutputRouteType.EXTERNAL
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutputRouteType.SPEAKER
        else -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                (deviceType == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    deviceType == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    deviceType == AudioDeviceInfo.TYPE_BLE_BROADCAST)
            ) {
                AudioOutputRouteType.BLUETOOTH
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                deviceType == AudioDeviceInfo.TYPE_HDMI_EARC
            ) {
                AudioOutputRouteType.EXTERNAL
            } else {
                null
            }
        }
    }

    private fun resolveDeviceName(info: AudioDeviceInfo, routeType: AudioOutputRouteType): String {
        if (routeType == AudioOutputRouteType.SPEAKER) return "Phone Speaker"
        val product = info.productName?.toString()?.trim().orEmpty()
        return if (product.isNotEmpty() && !product.equals(Build.MODEL, ignoreCase = true)) {
            product
        } else {
            defaultLabelFor(routeType)
        }
    }

    private fun defaultLabelFor(routeType: AudioOutputRouteType): String = when (routeType) {
        AudioOutputRouteType.SPEAKER -> "Phone Speaker"
        AudioOutputRouteType.WIRED_HEADSET -> "Wired Headphones"
        AudioOutputRouteType.USB_AUDIO -> "USB Audio DAC"
        AudioOutputRouteType.BLUETOOTH -> "Bluetooth Audio"
        AudioOutputRouteType.EXTERNAL -> "External Audio"
    }

    private fun routePriority(routeType: AudioOutputRouteType): Int = when (routeType) {
        AudioOutputRouteType.BLUETOOTH -> 0
        AudioOutputRouteType.USB_AUDIO -> 1
        AudioOutputRouteType.WIRED_HEADSET -> 2
        AudioOutputRouteType.EXTERNAL -> 3
        AudioOutputRouteType.SPEAKER -> 4
    }
}

/**
 * Lifecycle-safe Compose observer for active Android audio output routes.
 * Registers [AudioDeviceCallback] strictly while composed and always unregisters in onDispose.
 */
@Composable
fun rememberAudioOutputState(): AudioOutputState {
    val context = LocalContext.current
    var outputState by remember(context) {
        mutableStateOf(AudioOutputMonitor.queryAudioOutputState(context))
    }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) {
            onDispose { }
        } else {
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    outputState = AudioOutputMonitor.queryAudioOutputState(context)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    outputState = AudioOutputMonitor.queryAudioOutputState(context)
                }
            }
            val mainHandler = Handler(Looper.getMainLooper())
            runCatching {
                audioManager.registerAudioDeviceCallback(callback, mainHandler)
            }
            outputState = AudioOutputMonitor.queryAudioOutputState(context)

            onDispose {
                runCatching {
                    audioManager.unregisterAudioDeviceCallback(callback)
                }
            }
        }
    }

    return outputState
}
