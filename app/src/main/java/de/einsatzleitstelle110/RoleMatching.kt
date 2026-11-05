package de.einsatzleitstelle110

/** One vehicle covers at most one requested role, including repeated roles and custom types. */
internal object RoleMatching {
    data class Match(val role: Int, val unit: Int)
    private class Edge(val to: Int, val reverse: Int, var capacity: Int, val cost: Long, val fee: Int)

    /** Maximum coverage within budget; then minimum fees, then the caller's preference score.
     * Residual edges allow a versatile vehicle to move to another role instead of blocking it.
     * This remains polynomial for imported chains with many requirements (no bitmask limit).
     */
    fun <T> assign(
        roles: List<String>, units: List<T>, fits: (T, String) -> Boolean,
        preference: (T, String) -> Int = { _, _ -> 0 },
        fee: (T) -> Int = { 0 }, budget: Long = Long.MAX_VALUE,
    ): List<Match> {
        if (roles.isEmpty() || units.isEmpty()) return emptyList()
        val scores = units.map { u -> roles.map { role -> if (fits(u, role)) preference(u, role).coerceAtLeast(0) else -1 } }
        val scale = (scores.flatten().maxOrNull() ?: 0).coerceAtLeast(0).toLong() * minOf(roles.size, units.size) + 1
        val source = units.size + roles.size
        val sink = source + 1
        val graph = Array(sink + 1) { mutableListOf<Edge>() }
        fun edge(a: Int, b: Int, cost: Long, charge: Int = 0): Edge {
            val forward = Edge(b, graph[b].size, 1, cost, charge)
            val reverse = Edge(a, graph[a].size, 0, -cost, -charge)
            graph[a].add(forward); graph[b].add(reverse)
            return forward
        }
        val links = mutableListOf<Pair<Match, Edge>>()
        for (i in units.indices) {
            val charge = fee(units[i]).coerceAtLeast(0)
            edge(source, i, charge * scale, charge)
            for (j in roles.indices) if (scores[i][j] >= 0)
                links.add(Match(j, i) to edge(i, units.size + j, scores[i][j].toLong()))
        }
        for (j in roles.indices) edge(units.size + j, sink, 0)
        var spent = 0L
        while (true) {
            val distance = LongArray(graph.size) { Long.MAX_VALUE }
            val previous = IntArray(graph.size) { -1 }
            val via = IntArray(graph.size)
            distance[source] = 0
            for (pass in 1 until graph.size) {
                var changed = false
                for (a in graph.indices) if (distance[a] != Long.MAX_VALUE)
                    for ((i, e) in graph[a].withIndex()) if (e.capacity > 0 && distance[a] + e.cost < distance[e.to]) {
                        distance[e.to] = distance[a] + e.cost
                        previous[e.to] = a; via[e.to] = i; changed = true
                    }
                if (!changed) break
            }
            if (previous[sink] < 0) break
            var nextSpent = spent
            var node = sink
            while (node != source) {
                val a = previous[node]
                nextSpent += graph[a][via[node]].fee
                node = a
            }
            if (nextSpent > budget.coerceAtLeast(0)) break
            node = sink
            while (node != source) {
                val a = previous[node]
                val e = graph[a][via[node]]
                e.capacity--; graph[node][e.reverse].capacity++
                node = a
            }
            spent = nextSpent
        }
        return links.filter { it.second.capacity == 0 }.map { it.first }.sortedBy { it.role }
    }
}
