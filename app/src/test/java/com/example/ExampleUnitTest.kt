package com.example

import com.example.data.GuestCatStore
import com.example.location.calculateDistanceMeters
import com.example.model.DeviceRole
import com.example.model.HabitCategory
import com.example.model.computeCatRank
import com.example.model.safeHabitCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun computeCatRank_progressesAcrossThresholds() {
        assertEquals("Gatinho Aprendiz", computeCatRank(0).title)
        assertEquals("Caçador de Laser", computeCatRank(150).title)
        assertEquals("Mestre do Ronrom", computeCatRank(450).title)
        assertEquals("Guardião da Caixa", computeCatRank(800).title)
        assertEquals("Faraó Felino", computeCatRank(1200).title)
    }

    @Test
    fun safeHabitCategory_fallsBackGracefullyOnUnknownValue() {
        assertEquals(HabitCategory.HYDRATION, safeHabitCategory("HYDRATION"))
        assertEquals(HabitCategory.PLAY, safeHabitCategory("UNKNOWN_FUTURE_CATEGORY"))
        assertEquals(HabitCategory.PLAY, safeHabitCategory(null))
    }

    @Test
    fun calculateDistanceMeters_returnsZeroForSamePointAndPositiveOtherwise() {
        val same = calculateDistanceMeters(-23.55052, -46.633308, -23.55052, -46.633308)
        assertEquals(0.0, same, 0.01)

        val nearby = calculateDistanceMeters(-23.55052, -46.633308, -23.55142, -46.633308)
        assertTrue("Expected ~100m distance, was $nearby", nearby in 90.0..115.0)
    }

    @Test
    fun deviceRole_hasServerAndClientOptions() {
        assertEquals("Servidor", DeviceRole.SERVER.title)
        assertEquals("Cliente", DeviceRole.CLIENT.title)
    }

    @Test
    fun guestCatStore_startsWithoutHardcodedLocationAndRecordsFix() = runTest {
        val store = GuestCatStore()
        val initial = store.observeCatProfile().first()!!
        assertEquals(0.0, initial.homeLatitude, 0.0)
        assertEquals(0.0, initial.lastLatitude, 0.0)

        store.recordGpsLocation(
            latitude = -15.793889,
            longitude = -47.882778,
            accuracyMeters = 8.0,
            speedMps = 0.0,
            insideSafeZone = true,
            zoneLabel = "Posição Atual • Zona Segura (0m)",
            trackingActive = false
        )
        val updated = store.observeCatProfile().first()!!
        assertEquals(-15.793889, updated.lastLatitude, 0.0001)
        assertEquals(-47.882778, updated.lastLongitude, 0.0001)
    }
}
