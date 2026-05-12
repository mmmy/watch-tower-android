package com.watchtower.android

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSignalSnapshotJsonTest {

    @Test
    fun roundTripsCachedAlertsForWidgetRendering() {
        val snapshot = WidgetSignalSnapshot(
            alerts = listOf(
                SignalAlert("BTCUSDT", "60", "tdMd", SignalSide.Bullish, 123L, read = false),
                SignalAlert("ETHUSDT", "30", "vegas", SignalSide.Bearish, 456L, read = true)
            ),
            updatedAtMillis = 789L
        )

        val decoded = WidgetSignalSnapshotJson.decode(WidgetSignalSnapshotJson.encode(snapshot))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun invalidSnapshotFallsBackToEmpty() {
        val decoded = WidgetSignalSnapshotJson.decode("not json")

        assertEquals(WidgetSignalSnapshot.empty(), decoded)
    }
}
