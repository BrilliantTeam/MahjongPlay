package com.mahjongplay.model

import kotlinx.serialization.Serializable
import org.mahjong4j.yaku.normals.NormalYaku
import org.mahjong4j.yaku.yakuman.Yakuman

@Serializable
data class YakuSettlement(
    val displayName: String,
    val uuid: String,
    val isRealPlayer: Boolean,
    val botCode: Int = MahjongTile.UNKNOWN.code,
    val yakuList: List<NormalYaku>,
    val yakumanList: List<Yakuman>,
    val doubleYakumanList: List<DoubleYakuman>,
    val nagashiMangan: Boolean = false,
    val redFiveCount: Int = 0,
    val nukiDoraCount: Int = 0,
    val riichi: Boolean,
    val winningTile: MahjongTile,
    val hands: List<MahjongTile>,
    val fuuroList: List<Pair<Boolean, List<MahjongTile>>>,
    val doraIndicators: List<MahjongTile>,
    val uraDoraIndicators: List<MahjongTile>,
    val fu: Int,
    val han: Int,
    val score: Int,
) {
    companion object {
        fun nagashiMangan(
            playerDisplayName: String,
            playerUUID: String,
            isRealPlayer: Boolean,
            botCode: Int,
            riichi: Boolean,
            hands: List<MahjongTile>,
            doraIndicators: List<MahjongTile>,
            uraDoraIndicators: List<MahjongTile>,
            isDealer: Boolean
        ): YakuSettlement = YakuSettlement(
            displayName = playerDisplayName,
            uuid = playerUUID,
            isRealPlayer = isRealPlayer,
            botCode = botCode,
            yakuList = listOf(),
            yakumanList = listOf(),
            doubleYakumanList = listOf(),
            nagashiMangan = true,
            winningTile = MahjongTile.UNKNOWN,
            riichi = riichi,
            hands = hands,
            fuuroList = listOf(),
            doraIndicators = doraIndicators,
            uraDoraIndicators = uraDoraIndicators,
            fu = 0,
            han = 0,
            score = if (isDealer) 12000 else 8000
        )

        val NO_YAKU = YakuSettlement(
            displayName = "",
            uuid = "",
            isRealPlayer = false,
            botCode = MahjongTile.UNKNOWN.code,
            yakuList = emptyList(),
            yakumanList = emptyList(),
            doubleYakumanList = emptyList(),
            nagashiMangan = false,
            redFiveCount = 0,
            riichi = false,
            winningTile = MahjongTile.UNKNOWN,
            hands = emptyList(),
            fuuroList = emptyList(),
            doraIndicators = emptyList(),
            uraDoraIndicators = emptyList(),
            fu = 0,
            han = 0,
            score = 0
        )
    }
}
