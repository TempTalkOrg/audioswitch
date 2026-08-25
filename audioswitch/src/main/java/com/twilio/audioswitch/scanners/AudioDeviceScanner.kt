package com.twilio.audioswitch.scanners

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import com.twilio.audioswitch.AudioDevice
import java.util.Collections

@RequiresApi(Build.VERSION_CODES.M)
internal class AudioDeviceScanner(
    private val audioManager: AudioManager,
    private val handler: Handler,
) : AudioDeviceCallback(), Scanner {
    // Written by whichever thread calls start()/stop(), read on the handler thread.
    @Volatile
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var listener: Scanner.Listener? = null
    private val activeDevices = Collections.synchronizedSet(mutableSetOf<AudioDevice>())
    private val refreshHandler: Handler =
        if (handler.looper == Looper.getMainLooper()) backgroundRefreshHandler else handler

    override fun isDeviceActive(audioDevice: AudioDevice): Boolean =
        this.findActiveDevice(audioDevice) != null

    override fun findActiveDevice(audioDevice: AudioDevice): AudioDevice? =
        synchronized(this.activeDevices) {
            this.activeDevices.firstOrNull { it.isSameKindAs(audioDevice) }
        }

    override fun start(listener: Scanner.Listener): Boolean {
        this.listener = listener
        this.audioManager.registerAudioDeviceCallback(this, this.handler)
        this.refreshHandler.post { refreshActiveDevices() }
        return true
    }

    override fun stop(): Boolean {
        this.audioManager.unregisterAudioDeviceCallback(this)
        this.listener = null
        this.activeDevices.clear()
        return true
    }

    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
        super.onAudioDevicesAdded(addedDevices)
        val connectedDevices = addedDevices?.mapNotNull { it.audioDevice }?.toSet() ?: emptySet()
        this.refreshHandler.post {
            refreshActiveDevices()
            this.handler.post {
                connectedDevices.forEach {
                    this.listener?.onDeviceConnected(it)
                }
            }
        }
    }

    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
        super.onAudioDevicesRemoved(removedDevices)
        val disconnectedDevices = removedDevices?.mapNotNull { it.audioDevice }?.toSet() ?: emptySet()
        this.refreshHandler.post {
            refreshActiveDevices()
            this.handler.post {
                disconnectedDevices.forEach {
                    this.listener?.onDeviceDisconnected(it)
                }
            }
        }
    }

    private fun refreshActiveDevices() {
        val devices = this.audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .mapNotNull { it.audioDevice }
        synchronized(this.activeDevices) {
            this.activeDevices.clear()
            this.activeDevices.addAll(devices)
        }
    }

    /**
     * Routing here goes through [AudioManager.startBluetoothSco], so only bluetooth endpoints that
     * carry HFP may be reported: A2DP and LE Audio speakers would show up as selectable and then
     * silently fail to route.
     *
     * Deliberately narrower than [toTwilioAudioDevice], which describes what
     * [AudioManager.setCommunicationDevice] accepts. Do not merge the two.
     */
    val AudioDeviceInfo.audioDevice: AudioDevice?
        get() = if (this.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            AudioDevice.BluetoothHeadset(this.productName.toString())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && this.type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            AudioDevice.BluetoothHeadset(this.productName.toString())
        } else if (this.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || this.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || this.type == AudioDeviceInfo.TYPE_USB_HEADSET) {
            AudioDevice.WiredHeadset()
        } else if (this.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) {
            AudioDevice.Earpiece()
        } else if (this.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            AudioDevice.Speakerphone()
        } else {
            null
        }

    private companion object {
        val backgroundRefreshThread: HandlerThread by lazy {
            HandlerThread("AudioDeviceScannerRefresh").apply { start() }
        }
        val backgroundRefreshHandler: Handler by lazy { Handler(backgroundRefreshThread.looper) }
    }
}