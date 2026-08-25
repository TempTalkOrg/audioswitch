package com.twilio.audioswitch

import android.media.AudioDeviceInfo
import android.os.Handler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.twilio.audioswitch.scanners.CommunicationDeviceScanner
import org.junit.Test
import org.mockito.Mockito.clearInvocations

/**
 * getAvailableCommunicationDevices() enumerates audio ports through the audio server, and device
 * callbacks land on the main thread by default, so it must not be called once per candidate device
 * while picking one.
 */
class CommDeviceScannerCachingTest : BaseTest() {

    private val plainHandler = mock<Handler> {
        whenever(mock.post(any())).thenAnswer {
            (it.arguments[0] as Runnable).run()
            true
        }
    }

    private val earpieceInfo = deviceInfo(1, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "Earpiece")
    private val speakerInfo = deviceInfo(2, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Speaker")
    private val bluetoothInfo = deviceInfo(3, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Buds")

    @Test
    fun `a device change should enumerate communication devices once`() {
        whenever(audioManager.getAvailableCommunicationDevices())
            .thenReturn(listOf(earpieceInfo, speakerInfo))
        val scanner = CommunicationDeviceScanner(audioManager, plainHandler, logger)
        val audioSwitch = getCommDeviceAudioSwitch(scanner)
        audioSwitch.start(audioDeviceChangeListener)
        audioSwitch.activate()
        clearInvocations(audioManager)

        // Connecting a preferred device runs the full path: snapshot, diff, reselect, reroute.
        whenever(audioManager.getAvailableCommunicationDevices())
            .thenReturn(listOf(earpieceInfo, speakerInfo, bluetoothInfo))
        scanner.onAudioDevicesAdded(emptyArray())

        verify(audioManager).setCommunicationDevice(bluetoothInfo)
        verify(audioManager, times(1)).getAvailableCommunicationDevices()
    }

    private fun deviceInfo(id: Int, type: Int, productName: String) = mock<AudioDeviceInfo> {
        whenever(mock.id).thenReturn(id)
        whenever(mock.type).thenReturn(type)
        whenever(mock.productName).thenReturn(productName)
    }
}
