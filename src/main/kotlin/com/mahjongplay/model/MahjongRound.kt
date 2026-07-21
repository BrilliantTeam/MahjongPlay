package com.mahjongplay.model

import kotlinx.serialization.Serializable

@Serializable
data class MahjongRound(
    var wind: Wind = Wind.EAST,
    var round: Int = 0,
    var honba: Int = 0
) {
    private var spentRounds = 0

    fun nextRound(playerCount: Int = 4) {
        val nextRound = (this.round + 1) % playerCount
        honba = 0
        if (nextRound == 0) {
            val nextWindNum = (this.wind.ordinal + 1) % 4
            wind = Wind.entries[nextWindNum]
        }
        round = nextRound
        spentRounds++
    }

    fun isAllLast(rule: MahjongRule): Boolean = (spentRounds + 1) >= rule.length.getRounds(rule.playerCount)

    fun displayName(): String = "${wind.displayName}${round + 1}局"
}
