package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.ai.SentenceCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Feature 19 ("Say it again" re-drill loop): starting a re-drill snapshots the
 * parent result as baseline, switches to a short recording, and cancelling
 * returns to the untouched parent result.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RedrillFlowTest {

    private fun viewModelWithCorrections(): SpeechViewModel {
        val vm = SpeechViewModel(ApplicationProvider.getApplicationContext())
        vm.setMetricsForTest(
            SpeechMetrics(
                score = 82,
                accuracy = 88,
                wpm = 120,
                sentenceCorrections = listOf(
                    SentenceCorrection(
                        ownSentence = "I have been working here since three years.",
                        correctedSentence = "I have been working here for three years.",
                        rule = "Use 'for' with a length of time."
                    )
                )
            )
        )
        return vm
    }

    @Test
    fun startRedrill_snapshotsBaselineAndStartsShortRecording() {
        val vm = viewModelWithCorrections()
        vm.startRedrill(0, null)

        assertEquals(AppState.Recording, vm.appState.value)
        assertEquals(30, vm.totalTargetSeconds.value)
        val target = vm.redrillTarget.value
        assertTrue(target != null)
        assertEquals(0, target!!.index)
        assertEquals("I have been working here for three years.", target.correctedSentence)
        assertEquals(82, vm.redrillBaseline.value?.score)
    }

    @Test
    fun startRedrill_invalidIndexIsIgnored() {
        val vm = viewModelWithCorrections()
        vm.startRedrill(5, null)
        assertEquals(AppState.Dashboard, vm.appState.value)
        assertNull(vm.redrillTarget.value)
        assertNull(vm.redrillBaseline.value)
    }

    @Test
    fun cancelRedrill_returnsToUntouchedParentResult() {
        val vm = viewModelWithCorrections()
        vm.startRedrill(0, null)
        vm.cancelRedrill()

        assertEquals(AppState.Result, vm.appState.value)
        assertNull(vm.redrillTarget.value)
        // Parent result metrics were never cleared.
        assertEquals(82, vm.metrics.value.score)
        assertEquals(1, vm.metrics.value.sentenceCorrections.size)
    }

    @Test
    fun freshRecording_clearsRedrillState() {
        val vm = viewModelWithCorrections()
        vm.startRedrill(0, null)
        vm.startRecording(null)

        assertNull(vm.redrillTarget.value)
        assertNull(vm.redrillBaseline.value)
        assertEquals(AppState.Recording, vm.appState.value)
    }
}
