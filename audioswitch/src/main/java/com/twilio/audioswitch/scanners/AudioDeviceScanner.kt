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
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal var listener: Scanner.Listener? = null
    private val deviceTypes = Collections.synchronizedSet(mutableSetOf<Int>())
    private val refreshHandler: Handler =
        if (handler.looper == Looper.getMainLooper()) backgroundRefreshHandler else handler

    override fun isDeviceActive(audioDevice: AudioDevice): Boolean =
        when (audioDevice) {
            is AudioDevice.BluetoothHeadset -> hasBluetoothDevice()
            is AudioDevice.WiredHeadset -> hasWiredDevice()
            is AudioDevice.Earpiece -> hasEarpieceDevice()
            is AudioDevice.Speakerphone -> hasSpeakerphoneDevice()
        }

    override fun start(listener: Scanner.Listener): Boolean {
        this.listener = listener
        this.audioManager.registerAudioDeviceCallback(this, this.handler)
        this.refreshHandler.post { refreshDeviceTypes() }
        return true
    }

    override fun stop(): Boolean {
        this.audioManager.unregisterAudioDeviceCallback(this)
        this.listener = null
        this.deviceTypes.clear()
        return true
    }

    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
        super.onAudioDevicesAdded(addedDevices)
        val connectedDevices = addedDevices?.mapNotNull { it.audioDevice }?.toSet() ?: emptySet()
        this.refreshHandler.post {
            refreshDeviceTypes()
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
            refreshDeviceTypes()
            this.handler.post {
                disconnectedDevices.forEach {
                    this.listener?.onDeviceDisconnected(it)
                }
            }
        }
    }

    private fun refreshDeviceTypes() {
        val types = this.audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { it.type }
            .toSet()
        synchronized(this.deviceTypes) {
            this.deviceTypes.clear()
            this.deviceTypes.addAll(types)
        }
    }

    private fun hasType(type: Int): Boolean = synchronized(this.deviceTypes) {
        this.deviceTypes.contains(type)
    }

    private fun hasBluetoothDevice(): Boolean {
        if (hasType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)) {
            return true
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            hasType(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    private fun hasWiredDevice(): Boolean {
        val hasWired = hasType(AudioDeviceInfo.TYPE_WIRED_HEADSET) || hasType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        if (hasWired) {
            return true
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasType(AudioDeviceInfo.TYPE_USB_HEADSET)
    }

    private fun hasEarpieceDevice(): Boolean = hasType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)

    private fun hasSpeakerphoneDevice(): Boolean = hasType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)

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

    fun AudioDeviceInfo.isAudioDevice(audioDevice: AudioDevice): Boolean =
        when (audioDevice) {
            is AudioDevice.BluetoothHeadset ->
                if (this.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    true
                } else {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && this.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
            is AudioDevice.Earpiece ->
                this.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            is AudioDevice.Speakerphone ->
                this.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            is AudioDevice.WiredHeadset ->
                if (this.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || this.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                    true
                } else {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
        }

    private companion object {
        val backgroundRefreshThread: HandlerThread by lazy {
            HandlerThread("AudioDeviceScannerRefresh").apply { start() }
        }
        val backgroundRefreshHandler: Handler by lazy { Handler(backgroundRefreshThread.looper) }
    }
}