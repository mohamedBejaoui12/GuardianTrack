package com.guardian.track.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import com.guardian.track.util.buildSmsAlertMessage
import java.util.Locale
import java.util.TimeZone

class IncidentDateFormatTest {

    private lateinit var originalLocale: Locale
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        originalTimeZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun toFormattedDate_formatsTimestampAsExpected() {
        assertEquals("01/01/1970", 0L.toFormattedDate())
    }

    @Test
    fun toFormattedTime_formatsTimestampAsExpected() {
        assertEquals("00:00:00", 0L.toFormattedTime())
    }

    @Test
    fun toFormattedDate_andTime_formatAnotherKnownTimestamp() {
        val timestamp = 1_000_000L

        assertEquals("01/01/1970", timestamp.toFormattedDate())
        assertEquals("00:16:40", timestamp.toFormattedTime())
    }

    @Test
    fun buildSmsAlertMessage_includesIncidentTypeAndFormattedTimestamp() {
        assertEquals(
            "Emergency Detector ALERT: Fall incident at 00:00 01/01. Please check on me.",
            buildSmsAlertMessage("Fall", 0L)
        )
    }
}