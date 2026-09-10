package app.foqos.android

import app.foqos.android.data.db.SessionEntity
import app.foqos.android.session.SessionTimeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTimeCalculatorTest {

    private val start = 1_000_000L
    private val minute = 60_000L

    private fun session(
        endTime: Long? = null,
        breakStart: Long? = null,
        breakEnd: Long? = null,
        usedBreakMs: Long = 0,
        pauseStart: Long? = null,
        pauseEnd: Long? = null,
        expectedEnd: Long? = null,
    ) = SessionEntity(
        id = "s1",
        profileId = "p1",
        tag = "tag",
        startTime = start,
        endTime = endTime,
        expectedEndTime = expectedEnd,
        breakStartTime = breakStart,
        breakEndTime = breakEnd,
        usedBreakDurationMs = usedBreakMs,
        pauseStartTime = pauseStart,
        pauseEndTime = pauseEnd,
    )

    @Test
    fun `elapsed focus time is wall clock when nothing interrupted the session`() {
        val elapsed = SessionTimeCalculator.elapsedFocusMs(session(), now = start + 10 * minute)
        assertEquals(10 * minute, elapsed)
    }

    @Test
    fun `a finished break is subtracted from focus time`() {
        val entity = session(breakStart = start + 2 * minute, breakEnd = start + 5 * minute)
        val elapsed = SessionTimeCalculator.elapsedFocusMs(entity, now = start + 10 * minute)
        assertEquals(7 * minute, elapsed)
    }

    @Test
    fun `an open break keeps subtracting as it runs`() {
        val entity = session(breakStart = start + 2 * minute)
        val elapsed = SessionTimeCalculator.elapsedFocusMs(entity, now = start + 6 * minute)
        assertEquals(2 * minute, elapsed)
    }

    @Test
    fun `a pause counts as non focus time`() {
        val entity = session(pauseStart = start + minute, pauseEnd = start + 3 * minute)
        val elapsed = SessionTimeCalculator.elapsedFocusMs(entity, now = start + 10 * minute)
        assertEquals(8 * minute, elapsed)
    }

    @Test
    fun `break allowance shrinks across several breaks`() {
        val entity = session(
            breakStart = start + 8 * minute,
            usedBreakMs = 4 * minute,
        )
        val remaining = SessionTimeCalculator.remainingBreakMs(
            entity,
            breakTimeInMinutes = 15,
            allowMultipleBreaks = true,
            now = start + 10 * minute,
        )
        assertEquals(9 * minute, remaining)
    }

    @Test
    fun `break allowance never goes negative`() {
        val entity = session(breakStart = start, usedBreakMs = 20 * minute)
        val remaining = SessionTimeCalculator.remainingBreakMs(
            entity,
            breakTimeInMinutes = 15,
            allowMultipleBreaks = true,
            now = start + 30 * minute,
        )
        assertEquals(0L, remaining)
    }

    @Test
    fun `a timer session reports expiry only after its deadline`() {
        val entity = session(expectedEnd = start + 25 * minute)
        assertFalse(SessionTimeCalculator.isTimerExpired(entity, now = start + 24 * minute))
        assertTrue(SessionTimeCalculator.isTimerExpired(entity, now = start + 25 * minute))
        assertEquals(
            minute,
            SessionTimeCalculator.remainingTimerMs(entity, now = start + 24 * minute),
        )
    }

    @Test
    fun `an open ended session has no remaining time`() {
        assertEquals(null, SessionTimeCalculator.remainingTimerMs(session(), now = start))
    }

    @Test
    fun `durations format as hours minutes seconds`() {
        assertEquals("05:00", SessionTimeCalculator.formatDuration(5 * minute))
        assertEquals("1:05:00", SessionTimeCalculator.formatDuration(65 * minute))
        assertEquals("1h 5m", SessionTimeCalculator.formatCompact(65 * minute))
    }
}
