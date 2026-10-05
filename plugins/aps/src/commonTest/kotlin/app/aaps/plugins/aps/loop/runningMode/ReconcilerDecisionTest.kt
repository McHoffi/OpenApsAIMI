package app.aaps.plugins.aps.loop.runningMode

import app.aaps.core.data.model.RM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The running-mode state table: which action each mode change asks the pump for.
 *
 * In `commonTest` rather than `androidHostTest`: [ReconcilerDecision] lives in `commonMain` and is the
 * thing that decides whether a TBR is cancelled or a zero TBR is issued, so a wrong entry here is a
 * dosing fault on every platform. The test needed nothing from the Android test base, so it moved as
 * it was, with Truth swapped for `kotlin.test`.
 */
class ReconcilerDecisionTest {

    private val working = listOf(
        RM.Mode.OPEN_LOOP, RM.Mode.CLOSED_LOOP, RM.Mode.CLOSED_LOOP_LGS, RM.Mode.RESUME
    )
    private val stopped = listOf(RM.Mode.DISABLED_LOOP, RM.Mode.SUSPENDED_BY_USER)
    private val zeroDelivery = listOf(RM.Mode.DISCONNECTED_PUMP, RM.Mode.SUPER_BOLUS)
    private val suspendedNoTbr = listOf(RM.Mode.SUSPENDED_BY_DST)
    private val pumpReported = listOf(RM.Mode.SUSPENDED_BY_PUMP)

    // --- Bucket classification ---

    @Test
    fun `working modes map to Working bucket`() {
        working.forEach {
            assertEquals(ReconcilerDecision.Bucket.Working, ReconcilerDecision.bucketOf(it))
        }
    }

    @Test
    fun `zero delivery modes map to ZeroDelivery bucket`() {
        zeroDelivery.forEach {
            assertEquals(ReconcilerDecision.Bucket.ZeroDelivery, ReconcilerDecision.bucketOf(it))
        }
    }

    @Test
    fun `suspended no tbr modes map to SuspendedNoTbr bucket`() {
        suspendedNoTbr.forEach {
            assertEquals(ReconcilerDecision.Bucket.SuspendedNoTbr, ReconcilerDecision.bucketOf(it))
        }
    }

    @Test
    fun `stopped modes map to Stopped bucket`() {
        stopped.forEach {
            assertEquals(ReconcilerDecision.Bucket.Stopped, ReconcilerDecision.bucketOf(it))
        }
    }

    @Test
    fun `suspended by pump maps to PumpReported bucket`() {
        pumpReported.forEach {
            assertEquals(ReconcilerDecision.Bucket.PumpReported, ReconcilerDecision.bucketOf(it))
        }
    }

    // --- Entry to zero-delivery ---

    @Test
    fun `working to disconnected pump issues zero TBR and cancels eb`() {
        working.forEach { prev ->
            assertEquals(
                ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = true),
                ReconcilerDecision.decide(prev, RM.Mode.DISCONNECTED_PUMP)
            )
        }
    }

    @Test
    fun `working to super bolus issues zero TBR and cancels eb`() {
        working.forEach { prev ->
            assertEquals(
                ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = true),
                ReconcilerDecision.decide(prev, RM.Mode.SUPER_BOLUS)
            )
        }
    }

    @Test
    fun `suspended no tbr to zero delivery issues zero TBR and cancels eb`() {
        suspendedNoTbr.forEach { prev ->
            zeroDelivery.forEach { next ->
                assertEquals(
                    ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = true),
                    ReconcilerDecision.decide(prev, next)
                )
            }
        }
    }

    @Test
    fun `zero delivery to zero delivery also issues zero TBR defensively`() {
        // Observer dedupes via pump state; decision here just states intent.
        zeroDelivery.forEach { prev ->
            zeroDelivery.forEach { next ->
                assertEquals(
                    ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = true),
                    ReconcilerDecision.decide(prev, next)
                )
            }
        }
    }

    // --- Entry to suspended-no-tbr ---

    @Test
    fun `working to suspended no tbr cancels TBR`() {
        working.forEach { prev ->
            suspendedNoTbr.forEach { next ->
                assertEquals(ReconcilerDecision.Action.CancelTbr, ReconcilerDecision.decide(prev, next))
            }
        }
    }

    @Test
    fun `zero delivery to suspended no tbr cancels TBR`() {
        zeroDelivery.forEach { prev ->
            suspendedNoTbr.forEach { next ->
                assertEquals(ReconcilerDecision.Action.CancelTbr, ReconcilerDecision.decide(prev, next))
            }
        }
    }

    @Test
    fun `suspended no tbr to suspended no tbr cancels TBR defensively`() {
        // Transition between two suspended-no-tbr modes: still ensure no active TBR.
        suspendedNoTbr.forEach { prev ->
            suspendedNoTbr.forEach { next ->
                assertEquals(ReconcilerDecision.Action.CancelTbr, ReconcilerDecision.decide(prev, next))
            }
        }
    }

    // --- Entry to stopped (DISABLED_LOOP) ---

    @Test
    fun `working to stopped cancels TBR`() {
        working.forEach { prev ->
            stopped.forEach { next ->
                assertEquals(ReconcilerDecision.Action.CancelTbr, ReconcilerDecision.decide(prev, next))
            }
        }
    }

    @Test
    fun `zero delivery to stopped cancels TBR`() {
        zeroDelivery.forEach { prev ->
            stopped.forEach { next ->
                assertEquals(ReconcilerDecision.Action.CancelTbr, ReconcilerDecision.decide(prev, next))
            }
        }
    }

    @Test
    fun `the same stopped mode again is no-op`() {
        // Extending a suspend. A TBR active now was set by hand during the suspend - keep it.
        stopped.forEach { mode ->
            assertEquals(ReconcilerDecision.Action.NoOp, ReconcilerDecision.decide(mode, mode))
        }
    }

    @Test
    fun `a different stopped mode still cancels TBR`() {
        assertEquals(
            ReconcilerDecision.Action.CancelTbr,
            ReconcilerDecision.decide(RM.Mode.DISABLED_LOOP, RM.Mode.SUSPENDED_BY_USER),
        )
        assertEquals(
            ReconcilerDecision.Action.CancelTbr,
            ReconcilerDecision.decide(RM.Mode.SUSPENDED_BY_USER, RM.Mode.DISABLED_LOOP),
        )
    }

    @Test
    fun `suspended no tbr to stopped cancels TBR defensively`() {
        suspendedNoTbr.forEach { prev ->
            stopped.forEach { next ->
                assertEquals(ReconcilerDecision.Action.CancelTbr, ReconcilerDecision.decide(prev, next))
            }
        }
    }

    // --- Exit from zero-delivery ---

    @Test
    fun `zero delivery to working cancels TBR`() {
        zeroDelivery.forEach { prev ->
            working.forEach { next ->
                assertEquals(ReconcilerDecision.Action.CancelTbr, ReconcilerDecision.decide(prev, next))
            }
        }
    }

    // --- Exit from suspended-no-tbr to working: no-op ---

    @Test
    fun `suspended no tbr to working is no-op`() {
        // No TBR was set in suspended-no-tbr, so nothing to clean up.
        suspendedNoTbr.forEach { prev ->
            working.forEach { next ->
                assertEquals(ReconcilerDecision.Action.NoOp, ReconcilerDecision.decide(prev, next))
            }
        }
    }

    // --- Exit from stopped to working: no-op ---

    @Test
    fun `stopped to working is no-op`() {
        // Stopped already had its TBR canceled on entry; nothing to clean up on exit.
        stopped.forEach { prev ->
            working.forEach { next ->
                assertEquals(ReconcilerDecision.Action.NoOp, ReconcilerDecision.decide(prev, next))
            }
        }
    }

    // --- Working to working: no-op ---

    @Test
    fun `working to working is no-op`() {
        working.forEach { prev ->
            working.forEach { next ->
                assertEquals(ReconcilerDecision.Action.NoOp, ReconcilerDecision.decide(prev, next))
            }
        }
    }

    // --- SUSPENDED_BY_PUMP: precheck owns this ---

    @Test
    fun `anything to suspended by pump is no-op`() {
        RM.Mode.entries.forEach { prev ->
            assertEquals(ReconcilerDecision.Action.NoOp, ReconcilerDecision.decide(prev, RM.Mode.SUSPENDED_BY_PUMP))
        }
    }

    @Test
    fun `suspended by pump to anything is no-op`() {
        RM.Mode.entries.forEach { next ->
            assertEquals(ReconcilerDecision.Action.NoOp, ReconcilerDecision.decide(RM.Mode.SUSPENDED_BY_PUMP, next))
        }
    }

    // --- Exhaustiveness guard ---

    @Test
    fun `every mode pair produces an action`() {
        // Regression guard: adding a new RM.Mode without updating bucketOf() would fail compilation
        // (when is exhaustive). This test catches the subtler case where a new mode gets bucketed
        // but decide() doesn't have the transition right - at minimum, assert every cell returns
        // a defined Action rather than throwing.
        val known = listOf(
            ReconcilerDecision.Action.NoOp,
            ReconcilerDecision.Action.CancelTbr,
            ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = true),
            ReconcilerDecision.Action.IssueZeroTbr(cancelExtendedBolus = false)
        )
        RM.Mode.entries.forEach { prev ->
            RM.Mode.entries.forEach { next ->
                val a = ReconcilerDecision.decide(prev, next)
                assertTrue(a in known, "unexpected action $a for $prev -> $next")
            }
        }
    }
}
