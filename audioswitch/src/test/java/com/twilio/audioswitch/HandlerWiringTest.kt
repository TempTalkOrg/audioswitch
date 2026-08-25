package com.twilio.audioswitch

import android.content.Context
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Test

/**
 * The public constructors accept a [android.os.Handler] so that callers owning a dedicated thread can
 * keep every system callback and every routing call off the main thread. These tests pin that the
 * handler actually reaches the platform registration, since nothing else would fail if it were
 * dropped: the switch would silently fall back to the main thread.
 */
class HandlerWiringTest : BaseTest() {

    @Test
    fun `AudioSwitch should register device callbacks on the provided handler`() {
        whenever(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(audioManager)

        AudioSwitch(
            context = context,
            audioFocusChangeListener = defaultAudioFocusChangeListener,
            preferredDeviceList = preferredDeviceList,
            handler = handler,
        ).start(audioDeviceChangeListener)

        verify(audioManager).registerAudioDeviceCallback(any(), eq(handler))
    }

    @Test
    fun `CommDeviceAudioSwitch should register device callbacks on the provided handler`() {
        whenever(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(audioManager)

        CommDeviceAudioSwitch(
            context = context,
            audioFocusChangeListener = defaultAudioFocusChangeListener,
            preferredDeviceList = preferredDeviceList,
            handler = handler,
        ).start(audioDeviceChangeListener)

        verify(audioManager).registerAudioDeviceCallback(any(), eq(handler))
    }
}
