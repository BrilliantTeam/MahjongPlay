package com.mahjongplay.model

import org.mahjong4j.hands.Mentsu

class Fuuro(
    val mentsu: Mentsu,
    val tiles: List<MahjongTile>,
    val claimTarget: ClaimTarget,
    val claimTile: MahjongTile
)
