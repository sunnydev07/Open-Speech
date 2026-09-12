package com.example

import com.example.ai.CefrLevel
import com.example.ai.CefrMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class CefrMapperTest {
    @Test
    fun highScore_mapsToC1() {
        assertEquals(CefrLevel.C1, CefrMapper.mapToCefr(score = 92, accuracy = 95, wpm = 132, pauses = 2))
    }

    @Test
    fun midScore_mapsToB2() {
        assertEquals(CefrLevel.B2, CefrMapper.mapToCefr(score = 80, accuracy = 90, wpm = 130, pauses = 1))
    }

    @Test
    fun lowScore_mapsToA2() {
        assertEquals(CefrLevel.A2, CefrMapper.mapToCefr(score = 40, accuracy = 90, wpm = 120, pauses = 1))
    }

    @Test
    fun weakAccuracy_downgradesOneLevel() {
        assertEquals(CefrLevel.B1, CefrMapper.mapToCefr(score = 80, accuracy = 70, wpm = 130, pauses = 1))
    }

    @Test
    fun manyPauses_downgradesOneLevel() {
        assertEquals(CefrLevel.B1, CefrMapper.mapToCefr(score = 80, accuracy = 90, wpm = 130, pauses = 8))
    }

    @Test
    fun offTargetPace_downgradesOneLevel() {
        assertEquals(CefrLevel.B1, CefrMapper.mapToCefr(score = 80, accuracy = 90, wpm = 60, pauses = 1))
    }

    @Test
    fun unknownWpm_isIgnored() {
        assertEquals(CefrLevel.B2, CefrMapper.mapToCefr(score = 80, accuracy = 90, wpm = 0, pauses = 1))
    }

    @Test
    fun floor_isA2() {
        assertEquals(CefrLevel.A2, CefrMapper.mapToCefr(score = 30, accuracy = 50, wpm = 40, pauses = 20))
    }
}
