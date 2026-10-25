package com.mahjongplay.game

import com.mahjongplay.model.Fuuro
import com.mahjongplay.model.MahjongTile
import org.mahjong4j.hands.Kantsu
import org.mahjong4j.hands.Shuntsu

object TaiwanScoring {
    enum class Kind { SEQUENCE, TRIPLET, QUAD }

    data class Meld(val kind: Kind, val tile: Int, val open: Boolean) {
        val tiles: List<Int>
            get() = when (kind) {
                Kind.SEQUENCE -> listOf(tile, tile + 1, tile + 2)
                Kind.TRIPLET -> List(3) { tile }
                Kind.QUAD -> List(4) { tile }
            }
    }

    data class Tai(val name: String, val tai: Int)

    data class Context(
        val winningTile: Int,
        val selfDraw: Boolean,
        val seatWind: Int,
        val roundWind: Int,
        val heavenly: Boolean = false,
        val earthly: Boolean = false,
        val human: Boolean = false,
        val lastDraw: Boolean = false,
        val lastDiscard: Boolean = false,
        val replacement: Boolean = false,
        val robKong: Boolean = false,
    )

    private class Decomposition(val pair: Int, val melds: List<Meld>)

    fun codes(tiles: List<MahjongTile>): List<Int> = tiles.map { it.mahjong4jTile.code }

    fun melds(fuuroList: List<Fuuro>): List<Meld> = fuuroList.map { fuuro ->
        val codes = codes(fuuro.tiles)
        when (fuuro.mentsu) {
            is Shuntsu -> Meld(Kind.SEQUENCE, codes.min(), true)
            is Kantsu -> Meld(Kind.QUAD, codes[0], fuuro.mentsu.isOpen)
            else -> Meld(Kind.TRIPLET, codes[0], true)
        }
    }

    fun flowerIndices(flowers: List<MahjongTile>): List<Int> = flowers.map { it.ordinal - MahjongTile.SPRING.ordinal }

    private fun decompositions(hand: List<Int>, declared: List<Meld>): List<Decomposition> {
        if (declared.size > 5 || hand.size != (5 - declared.size) * 3 + 2) return emptyList()
        val counts = IntArray(34)
        val physical = IntArray(34)
        for (tile in hand) {
            if (tile !in 0 until 34 || ++physical[tile] > 4) return emptyList()
            counts[tile]++
        }
        for (meld in declared) for (tile in meld.tiles) if (++physical[tile] > 4) return emptyList()
        val out = mutableListOf<Decomposition>()
        for (pair in 0 until 34) if (counts[pair] >= 2) {
            counts[pair] -= 2
            decompose(counts, 5 - declared.size, pair, mutableListOf(), out)
            counts[pair] += 2
        }
        return out
    }

    private fun decompose(c: IntArray, remaining: Int, pair: Int, path: MutableList<Meld>, out: MutableList<Decomposition>) {
        val first = (0 until 34).firstOrNull { c[it] > 0 }
        if (first == null) {
            if (remaining == 0) out += Decomposition(pair, path.toList())
            return
        }
        if (remaining <= 0) return
        if (c[first] >= 3) {
            c[first] -= 3
            path += Meld(Kind.TRIPLET, first, false)
            decompose(c, remaining - 1, pair, path, out)
            path.removeLast()
            c[first] += 3
        }
        if (first < 27 && first % 9 <= 6 && c[first + 1] > 0 && c[first + 2] > 0) {
            c[first]--; c[first + 1]--; c[first + 2]--
            path += Meld(Kind.SEQUENCE, first, false)
            decompose(c, remaining - 1, pair, path, out)
            path.removeLast()
            c[first]++; c[first + 1]++; c[first + 2]++
        }
    }

    fun canWin(hand: List<Int>, declared: List<Meld>): Boolean = decompositions(hand, declared).isNotEmpty()

    fun waits(hand: List<Int>, declared: List<Meld>): List<Int> = (0 until 34).filter { canWin(hand + it, declared) }

    fun chooseDiscard(hand: List<Int>, declared: List<Meld>, drawn: Int): Int {
        var bestTile = -1
        var bestQuality = Int.MIN_VALUE
        for (tile in hand.distinct()) {
            val q = quality(hand - tile, declared)
            if (q > bestQuality || q == bestQuality && (tile == drawn || bestTile != drawn && tile > bestTile)) {
                bestQuality = q
                bestTile = tile
            }
        }
        return bestTile
    }

    private fun quality(hand: List<Int>, declared: List<Meld>): Int {
        val counts = IntArray(34)
        hand.forEach { counts[it]++ }
        val waits = waits(hand, declared)
        if (waits.isNotEmpty()) {
            val outs = waits.sumOf { tile -> (4 - counts[tile] - declared.sumOf { m -> m.tiles.count { it == tile } }).coerceAtLeast(0) }
            return 100_000 + outs * 100 + waits.size
        }
        var score = 0
        for (tile in 0 until 34) {
            when {
                counts[tile] >= 3 -> score += 150
                counts[tile] == 2 -> score += 35
                counts[tile] == 1 && tile >= 27 -> score -= 10
                counts[tile] == 1 && (tile % 9 == 0 || tile % 9 == 8) -> score -= 2
            }
            if (tile >= 27) continue
            if (tile % 9 <= 7) score += 12 * minOf(counts[tile], counts[tile + 1])
            if (tile % 9 <= 6) {
                score += 4 * minOf(counts[tile], counts[tile + 2])
                score += 100 * minOf(counts[tile], counts[tile + 1], counts[tile + 2])
            }
        }
        return score
    }

    fun score(hand: List<Int>, declared: List<Meld>, flowers: List<Int>, ctx: Context): List<Tai>? {
        val ds = decompositions(hand, declared)
        if (ds.isEmpty() || ctx.winningTile !in hand) return null
        val uniqueWait = waits(hand.toMutableList().apply { remove(ctx.winningTile) }, declared).size == 1
        var best: List<Tai>? = null
        for (d in ds) {
            val placements = buildList {
                if (d.pair == ctx.winningTile) add(-1)
                d.melds.forEachIndexed { i, m -> if (ctx.winningTile in m.tiles) add(i) }
            }
            for (placement in placements) {
                val s = evaluate(d, declared, flowers, ctx, uniqueWait, placement)
                if (best == null || s.sumOf { it.tai } > best.sumOf { it.tai }) best = s
            }
        }
        return best
    }

    private fun evaluate(
        d: Decomposition,
        declared: List<Meld>,
        flowers: List<Int>,
        c: Context,
        uniqueWait: Boolean,
        winningMeld: Int,
    ): List<Tai> = buildList {
        fun add(name: String, tai: Int) { this += Tai(name, tai) }
        val all = declared + d.melds
        val closed = declared.none { it.open }
        if (!c.heavenly && !c.earthly && !c.human) {
            if (closed && c.selfDraw) add("門清自摸", 3)
            else {
                if (closed) add("門清", 1)
                if (c.selfDraw) add("自摸", 1)
            }
        }
        val sets = IntArray(34)
        var concealed = 0
        var sequences = 0
        var quads = 0
        val suits = BooleanArray(3)
        var honors = false
        (listOf(d.pair) + all.map { it.tile }).forEach { if (it >= 27) honors = true else suits[it / 9] = true }
        all.forEach { m ->
            if (m.kind == Kind.SEQUENCE) sequences++
            else {
                sets[m.tile]++
                if (!m.open) concealed++
                if (m.kind == Kind.QUAD) quads++
            }
        }
        if (!c.selfDraw && winningMeld >= 0 && d.melds[winningMeld].kind == Kind.TRIPLET) concealed--
        if (sequences == 0) add("碰碰胡", 4)
        when (concealed) {
            5 -> add("五暗刻", 8)
            4 -> add("四暗刻", 5)
            3 -> add("三暗刻", 2)
        }
        if (quads >= 4) add("四槓胡", 8)
        when (suits.count { it }) {
            0 -> add("字一色", 16)
            1 -> if (honors) add("混一色", 4) else add("清一色", 8)
        }
        val dragons = sets[31] + sets[32] + sets[33]
        val winds = sets[27] + sets[28] + sets[29] + sets[30]
        when {
            dragons == 3 -> add("大三元", 8)
            dragons == 2 && d.pair >= 31 -> add("小三元", 4)
            else -> for (t in 31..33) if (sets[t] > 0) add("${name(t)}刻子", 1)
        }
        when {
            winds == 4 -> add("大四喜", 16)
            winds == 3 && d.pair in 27..30 -> add("小四喜", 8)
            else -> {
                if (sets[27 + c.seatWind] > 0) add("門風（${name(27 + c.seatWind)}）", 1)
                if (sets[27 + c.roundWind] > 0) add("圈風（${name(27 + c.roundWind)}）", 1)
            }
        }
        flowers.forEach { if (it % 4 == c.seatWind) add("正花（${MahjongTile.flowers[it].displayName}）", 1) }
        if (flowers.containsAll(listOf(0, 1, 2, 3))) add("花槓（春夏秋冬）", 2)
        if (flowers.containsAll(listOf(4, 5, 6, 7))) add("花槓（梅蘭竹菊）", 2)
        if (declared.size == 5 && declared.all { it.open } && !c.selfDraw) add("全求人", 2)
        else if (uniqueWait) add("獨聽", 1)
        val twoSided = winningMeld >= 0 && d.melds[winningMeld].let { m ->
            val offset = c.winningTile - m.tile
            m.kind == Kind.SEQUENCE && (offset == 0 && m.tile % 9 != 6 || offset == 2 && m.tile % 9 != 0)
        }
        if (sequences == 5 && !honors && flowers.isEmpty() && !c.selfDraw && twoSided && !uniqueWait) add("平胡", 2)
        when {
            c.heavenly -> add("天胡", 16)
            c.earthly -> add("地胡", 16)
            c.human -> add("人胡", 8)
        }
        if (c.lastDraw) add("海底撈月", 1)
        if (c.lastDiscard) add("河底撈魚", 1)
        if (c.replacement) add("槓上開花／補花自摸", 1)
        if (c.robKong) add("搶槓胡", 1)
    }

    private fun name(code: Int): String = MahjongTile.entries[code].displayName
}
