package com.mahjongplay.model

import kotlinx.serialization.Serializable
import net.kyori.adventure.text.Component

@Serializable
data class ScoreSettlement(
    val titleTranslateKey: String,
    val scoreList: List<ScoreItem>
) {
    val rankedScoreList: List<RankedScoreItem> = buildList {
        val origin = scoreList.sortedWith(originalScoreComparator).reversed()
        val after = scoreList.sortedWith(totalScoreComparator).reversed()
        after.forEachIndexed { index, playerScore ->
            val rankOrigin = origin.indexOf(playerScore)
            val rankFloat = when {
                index < rankOrigin -> "↑"
                index > rankOrigin -> "↓"
                else -> ""
            }
            val scoreChangeString = playerScore.scoreChange.let {
                when {
                    it == 0 -> ""
                    it > 0 -> "+$it"
                    else -> "$it"
                }
            }
            this += RankedScoreItem(
                scoreItem = playerScore,
                scoreTotal = playerScore.scoreOrigin + playerScore.scoreChange,
                scoreChangeText = scoreChangeString,
                rankFloatText = rankFloat
            )
        }
    }

    companion object {
        private val originalScoreComparator = Comparator<ScoreItem> { o1, o2 ->
            compareValuesBy(o1, o2) { it.scoreOrigin }.let {
                if (it == 0) compareValuesBy(o1, o2) { arg -> arg.stringUUID } else it
            }
        }
        private val totalScoreComparator = Comparator<ScoreItem> { o1, o2 ->
            compareValuesBy(o1, o2) { it.scoreOrigin + it.scoreChange }.let {
                if (it == 0) compareValuesBy(o1, o2) { arg -> arg.stringUUID } else it
            }
        }
    }
}

@Serializable
data class RankedScoreItem(
    val scoreItem: ScoreItem,
    val scoreTotal: Int,
    val scoreChangeText: String,
    val rankFloatText: String
)

@Serializable
data class ScoreItem(
    val displayName: String,
    val stringUUID: String,
    val isRealPlayer: Boolean,
    val botCode: Int = MahjongTile.UNKNOWN.code,
    val scoreOrigin: Int,
    val scoreChange: Int
)
