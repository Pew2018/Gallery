package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseOneDiagnosticsTest {
    private data class Event(val name: String, val requestId: Long, val fields: Map<String, Any?>)

    private class Trace {
        var time = 100L
        val events = mutableListOf<Event>()

        fun request(generation: Long, previous: Long? = null, type: ViewerScopeType = ViewerScopeType.FOLDER,
                    locator: String = "/test/folder", revision: Long? = 7L) =
            MediaRequestDiagnostics.start(generation, type, locator, revision, "resume", previous, { time }) { name, id, fields ->
                events.add(Event(name, id, fields))
            }

        fun named(name: String) = events.filter { it.name == name }
    }

    @Test fun d01NewRequestHasItsOwnIdentityAndAnExplicitPreviousLink() {
        val trace = Trace()
        val old = trace.request(1)
        val fresh = trace.request(2, old.id)
        val created = trace.named("media_request_created")
        assertEquals(listOf(old.id, fresh.id), created.map { it.requestId })
        assertNotEquals(old.id, fresh.id)
        assertEquals(old.id, created.last().fields["previousRequestId"])
    }

    @Test fun d02RequestedAndAdmittedAreDistinctFromStarted() {
        val trace = Trace()
        val request = trace.request(1)
        request.admit()
        assertEquals(listOf("media_request_created", "media_request_admitted"), trace.events.map { it.name })
        trace.time = 145
        request.workerStarted()
        val started = trace.named("media_worker_started").single()
        assertEquals(45L, started.fields["sinceRequestMs"])
        assertEquals(request.id, started.fields["workerId"])
    }

    @Test fun d03RejectedAndCancelledBeforeStartHaveNoWorkerTerminal() {
        val trace = Trace()
        val rejected = trace.request(1)
        rejected.admit()
        rejected.skip("executor_rejected")
        val cancelled = trace.request(2)
        cancelled.cancellationRequested("activity_pause")
        cancelled.skipIfUnadmitted("unknown")
        assertEquals(listOf("executor_rejected", "activity_pause"),
            trace.named("media_request_skipped").map { it.fields["terminalReason"] })
        assertTrue(trace.named("media_worker_started").isEmpty())
        assertTrue(trace.named("media_worker_terminal").isEmpty())
    }

    @Test fun d04PublicationsAreIndependentOfWorkerTerminal() {
        val trace = Trace()
        val request = trace.request(1)
        request.admit()
        request.workerStarted()
        request.published(MediaPublicationStage.PREVIEW, 5, 1, "METADATA_PREVIEW")
        request.published(MediaPublicationStage.PARTIAL, 15, 2, "INDEX_PREVIEW")
        assertTrue(trace.named("media_worker_terminal").isEmpty())
        request.workerFinished(GalleryScanState.PARTIAL, false)
        assertEquals(listOf("PREVIEW", "PARTIAL"), trace.named("media_content_published").map { it.fields["stage"] })
        val terminal = trace.named("media_worker_terminal").single()
        assertEquals("PARTIAL", terminal.fields["scanState"])
        assertEquals("FAILED", terminal.fields["terminal"])
    }

    @Test fun d05AndD17TerminalIsUniqueAcrossCancellationFailureAndReentry() {
        for ((scan, expected) in listOf(
            GalleryScanState.COMPLETE to "COMPLETE",
            GalleryScanState.ACCESS_REQUIRED to "FAILED",
            GalleryScanState.CANCELLED to "CANCELLED"
        )) {
            val trace = Trace()
            val request = trace.request(3)
            request.admit()
            request.workerStarted()
            request.workerStarted()
            if (scan == GalleryScanState.CANCELLED) {
                request.cancellationRequested("activity_pause")
                request.cancellationRequested("stop_fetching")
            }
            trace.time = 160
            request.workerFinished(scan, scan == GalleryScanState.CANCELLED)
            request.skip("cancelled_before_start")
            request.workerFinished(GalleryScanState.FAILED, false)
            assertEquals(1, trace.named("media_worker_started").size)
            val terminal = trace.named("media_worker_terminal").single()
            assertEquals(expected, terminal.fields["terminal"])
            assertEquals(60L, terminal.fields["workerMs"])
            if (scan == GalleryScanState.CANCELLED) assertEquals("activity_pause", terminal.fields["terminalReason"])
        }
    }

    @Test fun d06ActivityLocalLaunchNumbersCannotAliasDiagnosticSessions() {
        val sameLocalLaunch = 1L
        val first = TransitionOperation(DiagnosticSequence.next(), DiagnosticSequence.next(), sameLocalLaunch)
        val second = TransitionOperation(DiagnosticSequence.next(), DiagnosticSequence.next(), sameLocalLaunch)
        assertNotEquals(first.viewerSessionId, second.viewerSessionId)
        assertFalse(first.accepts(second, TransitionPhase.PREPARING, TransitionPhase.ENTRY_CAPTURED))
        assertEquals(0L, ViewerEntryDiagnostics.entryId("other-process", first.requestId))
    }

    @Test fun d07EntryAndReturnHaveSeparateOperationIdsAndOneSession() {
        val entryId = DiagnosticSequence.next()
        val entry = TransitionOperation(entryId, entryId, 1)
        val returning = TransitionOperation(entry.viewerSessionId, DiagnosticSequence.next(), 2)
        assertEquals(entry.viewerSessionId, returning.viewerSessionId)
        assertNotEquals(entry.requestId, returning.requestId)
        assertTrue(returning.accepts(returning, TransitionPhase.RETURN_PREPARING, TransitionPhase.RETURNING))
        assertFalse(returning.accepts(entry, TransitionPhase.RETURN_PREPARING, TransitionPhase.RETURNING))
    }

    @Test fun d08SupersededWorkerRetainsItsOwnGenerationAndTerminal() {
        val trace = Trace()
        val old = trace.request(1)
        old.admit()
        old.workerStarted()
        val fresh = trace.request(2, old.id)
        old.workerFinished(GalleryScanState.CANCELLED, true)
        fresh.admit()
        fresh.workerStarted()
        fresh.workerFinished(GalleryScanState.COMPLETE, false)
        assertEquals(listOf(old.id, fresh.id), trace.named("media_worker_terminal").map { it.requestId })
        assertEquals(listOf(1L, 2L), trace.named("media_worker_terminal").map { it.fields["requestGeneration"] })
    }

    @Test fun d09AdapterSubmitCommitKeyIncludesInstanceAndDiffGeneration() {
        val submit = AdapterSubmission(DiagnosticSequence.next(), 2, 30)
        val commit = submit.copy()
        assertEquals(submit, commit)
        assertNotEquals(submit, commit.copy(generation = 3))
        assertNotEquals(submit, commit.copy(adapterId = DiagnosticSequence.next()))
    }

    @Test fun d10NoOpCanCommitAVersionWithoutChangingVisibleRows() {
        assertFalse(AdapterCommitKind.NO_OP.visibleListChanged)
        assertTrue(AdapterCommitKind.entries.filter { it != AdapterCommitKind.NO_OP }.all { it.visibleListChanged })
    }

    @Test fun d11DrawCannotBeReportedAsPresented() {
        assertEquals(FrameEvidence.DRAW, FrameEvidence.gridAcknowledgement(false))
        assertEquals("NOT_OBSERVABLE", FrameEvidence.DRAW.presented)
    }

    @Test fun d12FrameCommitCannotBeReportedAsPresented() {
        assertEquals(FrameEvidence.FRAME_COMMIT, FrameEvidence.gridAcknowledgement(true))
        assertEquals("NOT_OBSERVABLE", FrameEvidence.FRAME_COMMIT.presented)
    }

    @Test fun d15PrivateScopeIsOnlyEmittedAsAnOpaqueId() {
        val trace = Trace()
        val secret = "/tmp/private favorite/content://provider/entry?token=example"
        trace.request(1, type = ViewerScopeType.FAVORITES, locator = secret)
        trace.request(2, type = ViewerScopeType.FAVORITES, locator = secret)
        trace.request(3, locator = secret)
        val created = trace.named("media_request_created")
        val id = created.first().fields["scopeId"].toString()
        assertTrue(id.matches(Regex("[0-9a-f]{16}")))
        assertEquals(id, created[1].fields["scopeId"])
        assertNotEquals(id, created[2].fields["scopeId"])
        assertFalse(trace.events.toString().contains("private favorite"))
        assertFalse(trace.events.toString().contains("token=example"))
    }

    @Test fun d16DisabledLoggerSkipsPayloadConstruction() {
        assertFalse(GalleryDiagnosticLogger.isEnabled())
        GalleryDiagnosticLogger.observe { error("disabled diagnostics evaluated a payload") }
        assertEquals(0L, ViewerEntryDiagnostics.newRequest())
    }

    @Test fun d18MissingRevisionAndPresentationEvidenceAreExplicit() {
        val trace = Trace()
        trace.request(1, revision = null)
        assertEquals("UNKNOWN", trace.events.single().fields["scopeRevision"])
        assertEquals("NOT_OBSERVABLE", FrameEvidence.UNKNOWN.presented)
    }

    @Test fun d19DuplicateStaleAndIllegalTransitionEventsCannotAdvanceTheContract() {
        val operation = TransitionOperation(20, 21, 4)
        assertFalse(operation.accepts(operation, TransitionPhase.ENTERING, TransitionPhase.ENTERING))
        assertFalse(operation.accepts(operation.copy(generation = 3), TransitionPhase.ENTERING, TransitionPhase.VIEWING))
        assertFalse(operation.accepts(operation, TransitionPhase.PREPARING, TransitionPhase.RETURNING))
        assertTrue(operation.accepts(operation, TransitionPhase.ENTERING, TransitionPhase.VIEWING))
        assertTrue(operation.accepts(operation, TransitionPhase.INTERACTIVE_RETURN, TransitionPhase.VIEWING))
        assertTrue(operation.accepts(operation, TransitionPhase.CANCELLED, TransitionPhase.CLEANED))
        assertFalse(operation.accepts(operation, TransitionPhase.CLEANED, TransitionPhase.CLEANED))
    }
}
