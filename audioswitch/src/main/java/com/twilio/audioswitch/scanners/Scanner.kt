package com.twilio.audioswitch.scanners

import com.twilio.audioswitch.AudioDevice

interface Scanner {
    fun isDeviceActive(audioDevice: AudioDevice): Boolean
    fun isDeviceInactive(audioDevice: AudioDevice): Boolean =
        this.isDeviceActive(audioDevice).not()

    /**
     * Returns the device that currently serves the same role as [audioDevice], or null when none is
     * available. The result can carry a different [AudioDevice.name] than [audioDevice]: several
     * bluetooth headsets can be connected at once, and the platform can report a new product name
     * for an endpoint it already exposed.
     */
    fun findActiveDevice(audioDevice: AudioDevice): AudioDevice? =
        audioDevice.takeIf { this.isDeviceActive(it) }

    fun start(listener: Listener): Boolean
    fun start(
        onDeviceConnected: (audioDevice: AudioDevice) -> Unit,
        onDeviceDisconnected: (audioDevice: AudioDevice) -> Unit
    ): Boolean =
        this.start(object : Listener {
            override fun onDeviceConnected(audioDevice: AudioDevice) {
                onDeviceConnected(audioDevice)
            }

            override fun onDeviceDisconnected(audioDevice: AudioDevice) {
                onDeviceDisconnected(audioDevice)
            }
        })

    fun stop(): Boolean

    interface Listener {
        fun onDeviceConnected(audioDevice: AudioDevice)
        fun onDeviceDisconnected(audioDevice: AudioDevice)
    }
}

/**
 * Devices of the same kind are interchangeable for routing purposes, while [AudioDevice] equality
 * also compares [AudioDevice.name].
 */
internal fun AudioDevice.isSameKindAs(other: AudioDevice): Boolean = this.javaClass == other.javaClass
