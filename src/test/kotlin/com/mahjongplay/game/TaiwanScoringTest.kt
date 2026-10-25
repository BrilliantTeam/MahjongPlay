package com.mahjongplay.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaiwanScoringTest {
    private val man = (0..8).toList()

    @Test
    fun sixteenTileHandWaitsAndScores() {
        val waiting = man + listOf(9, 10, 11, 31, 31, 31, 27)
        assertEquals(16, waiting.size)
        assertEquals(listOf(27), TaiwanScoring.waits(waiting, emptyList()))
        assertFalse(TaiwanScoring.canWin(waiting + 28, emptyList()))

        val complete = waiting + 27
        assertTrue(TaiwanScoring.canWin(complete, emptyList()))
        val tais = TaiwanScoring.score(
            complete, emptyList(), listOf(0, 4),
            TaiwanScoring.Context(winningTile = 27, selfDraw = true, seatWind = 0, roundWind = 1),
        )!!.associate { it.name to it.tai }
        assertEquals(3, tais["門清自摸"])
        assertEquals(1, tais["白刻子"])
        assertEquals(1, tais["獨聽"])
        assertEquals(1, tais["正花（春）"])
        assertEquals(1, tais["正花（梅）"])
        assertNull(tais["平胡"])
    }

    @Test
    fun fullFlushWithOpenMeld() {
        val hand = listOf(0, 0, 0, 1, 2, 3, 3, 4, 5, 6, 7, 8, 8, 8)
        val melds = listOf(TaiwanScoring.Meld(TaiwanScoring.Kind.TRIPLET, 4, true))
        val tais = TaiwanScoring.score(hand, melds, emptyList(), TaiwanScoring.Context(winningTile = 1, selfDraw = false, seatWind = 1, roundWind = 0))!!
            .associate { it.name to it.tai }
        assertEquals(8, tais["清一色"])
        assertNull(tais["門清"])
    }

    @Test
    fun botDiscardKeepsTenpai() {
        val hand = man + listOf(9, 10, 11, 31, 31, 31, 27, 33)
        val discard = TaiwanScoring.chooseDiscard(hand, emptyList(), 33)
        assertTrue(discard == 27 || discard == 33)
        assertTrue(TaiwanScoring.waits(hand - discard, emptyList()).isNotEmpty())
    }
}
