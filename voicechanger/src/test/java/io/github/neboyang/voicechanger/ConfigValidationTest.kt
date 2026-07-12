package io.github.neboyang.voicechanger

import org.junit.Assert.assertThrows
import org.junit.Test

class ConfigValidationTest {

    @Test
    fun `audio config rejects unsupported values`() {
        assertThrows(IllegalArgumentException::class.java) { AudioConfig(sampleRate = 7999) }
        assertThrows(IllegalArgumentException::class.java) { AudioConfig(channels = 3) }
    }

    @Test
    fun `voice effect rejects non-finite and out-of-range values`() {
        assertThrows(IllegalArgumentException::class.java) { VoiceEffect(pitchSemiTones = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { VoiceEffect(tempo = Float.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { VoiceEffect(rate = 0.1f) }
    }
}
