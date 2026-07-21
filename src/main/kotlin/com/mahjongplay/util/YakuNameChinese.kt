package com.mahjongplay.util

import org.mahjong4j.yaku.normals.NormalYaku
import org.mahjong4j.yaku.yakuman.Yakuman

object YakuNameChinese {

    private val normalYakuNames = mapOf(
        NormalYaku.TANYAO to "斷幺九",
        NormalYaku.TSUMO to "門前清自摸和",
        NormalYaku.PINFU to "平和",
        NormalYaku.IPEIKO to "一杯口",
        NormalYaku.HAKU to "白",
        NormalYaku.HATSU to "發",
        NormalYaku.CHUN to "中",
        NormalYaku.JIKAZE to "自風牌",
        NormalYaku.BAKAZE to "場風牌",
        NormalYaku.IPPATSU to "一發",
        NormalYaku.HOUTEI to "河底撈魚",
        NormalYaku.HAITEI to "海底摸月",
        NormalYaku.REACH to "立直",
        NormalYaku.DORA to "寶牌",
        NormalYaku.URADORA to "裏寶牌",
        NormalYaku.RINSHANKAIHOH to "嶺上開花",
        NormalYaku.CHANKAN to "搶槓",
        NormalYaku.DOUBLE_REACH to "雙立直",
        NormalYaku.CHANTA to "混全帶幺九",
        NormalYaku.HONROHTOH to "混老頭",
        NormalYaku.SANSHOKUDOHJUN to "三色同順",
        NormalYaku.IKKITSUKAN to "一氣通貫",
        NormalYaku.TOITOIHO to "對對和",
        NormalYaku.SANSHOKUDOHKO to "三色同刻",
        NormalYaku.SANANKO to "三暗刻",
        NormalYaku.SANKANTSU to "三槓子",
        NormalYaku.SHOSANGEN to "小三元",
        NormalYaku.CHITOITSU to "七對子",
        NormalYaku.RYANPEIKO to "二杯口",
        NormalYaku.JUNCHAN to "純全帶幺九",
        NormalYaku.HONITSU to "混一色",
        NormalYaku.CHINITSU to "清一色",
    )

    private val yakumanNames = mapOf(
        Yakuman.KOKUSHIMUSO to "國士無雙",
        Yakuman.SUANKO to "四暗刻",
        Yakuman.CHURENPOHTO to "九蓮寶燈",
        Yakuman.DAISANGEN to "大三元",
        Yakuman.TSUISO to "字一色",
        Yakuman.SHOSUSHI to "小四喜",
        Yakuman.DAISUSHI to "大四喜",
        Yakuman.RYUISO to "綠一色",
        Yakuman.CHINROTO to "清老頭",
        Yakuman.SUKANTSU to "四槓子",
        Yakuman.RENHO to "人和",
        Yakuman.CHIHO to "地和",
        Yakuman.TENHO to "天和",
    )

    fun getName(yaku: NormalYaku): String = normalYakuNames[yaku] ?: yaku.japanese
    fun getName(yakuman: Yakuman): String = yakumanNames[yakuman] ?: yakuman.japanese
}
