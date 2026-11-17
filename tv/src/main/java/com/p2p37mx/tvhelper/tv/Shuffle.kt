package com.p2p37mx.tvhelper.tv

import java.io.File

/** Random photo order: every photo once per round, and never the same one twice in a row. */
object Shuffle {
    private val queue = ArrayDeque<String>()
    private var last: String? = null

    @Synchronized
    fun next(files: List<File>): File? {
        if (files.isEmpty()) return null
        val byName = files.associateBy { it.name }
        while (true) {
            if (queue.isEmpty()) {
                val round = byName.keys.shuffled().toMutableList()
                if (round.size > 1 && round.first() == last) round.add(round.removeAt(0))
                queue.addAll(round)
            }
            val n = queue.removeFirst()
            val f = byName[n] ?: continue // deleted meanwhile
            last = n
            return f
        }
    }
}
