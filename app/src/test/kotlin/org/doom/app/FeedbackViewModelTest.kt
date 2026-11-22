package org.doom.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.doom.data.FeedbackReport
import org.doom.data.FeedbackResult
import org.doom.data.FeedbackSubmitter
import org.doom.data.MarketplaceSessionManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FeedbackViewModelTest {
    @Before fun setMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

    private fun report() = FeedbackReport("Bug", "Title", "Details", "Steps", "Expected", "Actual", "0.1", "API 36", "")

    @Test fun unauthenticatedUserAndMissingConsentAreRejected() = runBlocking {
        var calls = 0
        val vm = FeedbackViewModel(FeedbackSubmitter { _, _ -> calls++; FeedbackResult.Submitted("id", "open") },
            MarketplaceSessionManager())
        vm.submit(report(), consent = true)
        assertEquals(FeedbackUiStatus.SIGN_IN_REQUIRED, vm.state.value.status)
        val sessions = MarketplaceSessionManager().also { it.establish("user@example.org", "token", 300) }
        val authenticated = FeedbackViewModel(FeedbackSubmitter { _, _ -> calls++; FeedbackResult.Submitted("id", "open") }, sessions)
        authenticated.submit(report(), consent = false)
        assertEquals(FeedbackUiStatus.CONSENT_REQUIRED, authenticated.state.value.status)
        assertEquals(0, calls)
    }

    @Test fun confirmedSubmissionDisplaysServerIdentifier() = runBlocking {
        val sessions = MarketplaceSessionManager().also { it.establish("user@example.org", "token", 300) }
        val vm = FeedbackViewModel(FeedbackSubmitter { _, consent ->
            assertEquals(true, consent)
            FeedbackResult.Submitted("server-report-id", "open")
        }, sessions)
        vm.submit(report(), true)
        assertEquals(FeedbackUiStatus.SUBMITTED, vm.state.value.status)
        assertEquals("server-report-id", vm.state.value.reportId)
    }

    @Test fun duplicateTapsDoNotSendDuplicatePosts() = runBlocking {
        val sessions = MarketplaceSessionManager().also { it.establish("user@example.org", "token", 300) }
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val vm = FeedbackViewModel(FeedbackSubmitter { _, _ ->
            calls++
            entered.complete(Unit)
            finish.await()
            FeedbackResult.Submitted("one-id", "open")
        }, sessions)
        vm.submit(report(), true)
        vm.submit(report(), true)
        withTimeout(2_000) { entered.await() }
        assertEquals(1, calls)
        assertEquals(FeedbackUiStatus.SUBMITTING, vm.state.value.status)
        finish.complete(Unit)
        withTimeout(2_000) {
            while (vm.state.value.status != FeedbackUiStatus.SUBMITTED) kotlinx.coroutines.yield()
        }
        assertEquals("one-id", vm.state.value.reportId)
    }

    @Test fun backendErrorAndUncertainOutcomeRemainExplicit() = runBlocking {
        val sessions = MarketplaceSessionManager().also { it.establish("user@example.org", "token", 300) }
        val unauthorized = FeedbackViewModel(FeedbackSubmitter { _, _ ->
            FeedbackResult.Failure(org.doom.data.FeedbackFailure.UNAUTHORIZED, "Sign in again")
        }, sessions)
        unauthorized.submit(report(), true)
        assertEquals(FeedbackUiStatus.UNAUTHORIZED, unauthorized.state.value.status)
        assertNull(unauthorized.state.value.reportId)

        val uncertain = FeedbackViewModel(FeedbackSubmitter { _, _ ->
            FeedbackResult.Failure(org.doom.data.FeedbackFailure.UNKNOWN_OUTCOME, "Not confirmed")
        }, sessions)
        uncertain.submit(report(), true)
        assertEquals(FeedbackUiStatus.UNKNOWN_OUTCOME, uncertain.state.value.status)
    }
}
