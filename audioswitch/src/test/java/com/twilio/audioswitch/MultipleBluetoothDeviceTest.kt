package com.twilio.audioswitch

import android.media.AudioDeviceInfo
import android.os.Handler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.twilio.audioswitch.scanners.AudioDeviceScanner
import com.twilio.audioswitch.scanners.CommunicationDeviceScanner
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two Bluetooth headsets can be connected at once (watch + earbuds, or an LE Audio headset that
 * exposes both an SCO and an LE endpoint), but [AbstractAudioSwitch.availableUniqueAudioDevices]
 * dedupes by device class, so only one [AudioDevice.BluetoothHeadset] entry ever exists while
 * [AudioDevice.BluetoothHeadset.equals] still compares names.
 */
class MultipleBluetoothDeviceTest : BaseTest() {

    private val watch = AudioDevice.BluetoothHeadset("Watch")
    private val earbuds = AudioDevice.BluetoothHeadset("Earbuds")

    private val plainHandler = mock<Handler> {
        whenever(mock.post(any())).thenAnswer {
            (it.arguments[0] as Runnable).run()
            true
        }
    }

    /** What the platform currently offers, mirroring [AudioDeviceScanner]'s refreshed cache. */
    private val connectedDevices = mutableListOf<AudioDevice>()

    private val deviceScanner = mock<AudioDeviceScanner> {
        whenever(mock.findActiveDevice(any())).thenAnswer { invocation ->
            val requested = invocation.arguments[0] as AudioDevice
            connectedDevices.firstOrNull { it.javaClass == requested.javaClass }
        }
        whenever(mock.isDeviceActive(any())).thenAnswer { invocation ->
            val requested = invocation.arguments[0] as AudioDevice
            connectedDevices.any { it.javaClass == requested.javaClass }
        }
    }

    @Test
    fun `AudioSwitch should keep bluetooth available when one of two headsets disconnects`() {
        connectedDevices.addAll(listOf(AudioDevice.Earpiece(), watch, earbuds))
        val audioSwitch = getModernAudioSwitch(deviceScanner)
        audioSwitch.start(audioDeviceChangeListener)
        audioSwitch.onDeviceConnected(AudioDevice.Earpiece())
        audioSwitch.onDeviceConnected(watch)
        audioSwitch.onDeviceConnected(earbuds)

        connectedDevices.remove(watch)
        audioSwitch.onDeviceDisconnected(watch)

        assertThat(
            audioSwitch.availableAudioDevices.any { it is AudioDevice.BluetoothHeadset },
            equalTo(true),
        )
        assertThat(audioSwitch.selectedAudioDevice, equalTo(earbuds as AudioDevice))
    }

    @Test
    fun `CommDeviceAudioSwitch should keep bluetooth available when one of two headsets disconnects`() {
        val watchInfo = bluetoothInfo(id = 1, productName = watch.name)
        val earbudsInfo = bluetoothInfo(id = 2, productName = earbuds.name)
        val earpieceInfo = earpieceInfo(id = 3)
        whenever(audioManager.getAvailableCommunicationDevices())
            .thenReturn(listOf(watchInfo, earbudsInfo, earpieceInfo))

        val scanner = CommunicationDeviceScanner(audioManager, plainHandler, logger)
        val audioSwitch = getCommDeviceAudioSwitch(scanner)
        audioSwitch.start(audioDeviceChangeListener)

        whenever(audioManager.getAvailableCommunicationDevices())
            .thenReturn(listOf(earbudsInfo, earpieceInfo))
        scanner.onAudioDevicesRemoved(emptyArray())

        // The platform still offers the earbuds, so this is purely a bookkeeping loss.
        assertTrue(scanner.isDeviceActive(earbuds))
        // Naming the surviving device matters: backfilling the disconnected watch instead would
        // still leave a bluetooth entry behind, but it would be the wrong one.
        assertThat(
            audioSwitch.availableAudioDevices.filterIsInstance<AudioDevice.BluetoothHeadset>(),
            equalTo(listOf(earbuds)),
        )
        assertThat(audioSwitch.selectedAudioDevice, equalTo(earbuds as AudioDevice))
    }

    @Test
    fun `CommDeviceAudioSwitch should route to a bluetooth headset that renamed itself`() {
        val earpieceInfo = earpieceInfo(id = 3)
        val scoInfo = bluetoothInfo(id = 1, productName = "Buds")
        whenever(audioManager.getAvailableCommunicationDevices())
            .thenReturn(listOf(scoInfo, earpieceInfo))

        val scanner = CommunicationDeviceScanner(audioManager, plainHandler, logger)
        val audioSwitch = getCommDeviceAudioSwitch(scanner)
        audioSwitch.start(audioDeviceChangeListener)
        audioSwitch.activate()

        // Same endpoint id, new product name, so the scanner reports no connect/disconnect at all.
        val renamedInfo = bluetoothInfo(id = 1, productName = "Buds LE")
        whenever(audioManager.getAvailableCommunicationDevices())
            .thenReturn(listOf(renamedInfo, earpieceInfo))
        scanner.onAudioDevicesAdded(emptyArray())

        audioSwitch.activate()

        verify(audioManager).setCommunicationDevice(renamedInfo)
    }

    private fun bluetoothInfo(id: Int, productName: String) = mock<AudioDeviceInfo> {
        whenever(mock.id).thenReturn(id)
        whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        whenever(mock.productName).thenReturn(productName)
    }

    private fun earpieceInfo(id: Int) = mock<AudioDeviceInfo> {
        whenever(mock.id).thenReturn(id)
        whenever(mock.type).thenReturn(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
        whenever(mock.productName).thenReturn("Earpiece")
    }
}
