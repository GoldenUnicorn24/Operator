package de.einsatzleitstelle110

import org.junit.Assert.*
import org.junit.Test

class NativeRoleMatchingTest {
    @Test fun residualReassignmentFindsCoverageThatGreedyMisses() {
        val roles = listOf("A","B","C")
        val units = listOf(setOf("A","B"),setOf("B","C"),setOf("A"))
        val m = RoleMatching.assign(roles,units,{ u,r -> r in u })
        assertEquals(3,m.size)
        assertEquals(2,m.single { it.role == 0 }.unit)
        assertEquals(0,m.single { it.role == 1 }.unit)
    }
    @Test fun monetaryPriorityDoesNotLoseCoverageOrUseOneUnitTwice() {
        val m = RoleMatching.assign(listOf("A","B"),listOf(0,1,2),{ _,_ -> true },
            { u,_ -> if (u==2) 0 else 100 },{ if(it==2) 1 else 0 },0)
        assertEquals(setOf(0,1),m.map { it.unit }.toSet())
        assertEquals(2,m.map { it.role }.distinct().size)
    }
    @Test fun matchesIndependentExhaustiveSearchForSmallBudgetsAndCapabilitySets() {
        val random = java.util.Random(19)
        repeat(150) {
            val roles = listOf("A","B","C")
            val caps = List(4) { roles.filter { random.nextBoolean() }.toSet() }
            val fees = List(4) { random.nextInt(4) }
            val scores = List(4) { List(3) { random.nextInt(8) } }
            val budget = random.nextInt(8)
            var best = Triple(-1,Int.MAX_VALUE,Int.MAX_VALUE)
            fun search(role: Int, used: Set<Int>, spent: Int, score: Int) {
                if (spent > budget) return
                if (role == roles.size) {
                    val v = Triple(used.size,spent,score)
                    if (v.first > best.first || v.first == best.first && (v.second < best.second || v.second == best.second && v.third < best.third)) best = v
                    return
                }
                search(role+1,used,spent,score)
                for (u in caps.indices) if (u !in used && roles[role] in caps[u]) search(role+1,used+u,spent+fees[u],score+scores[u][role])
            }
            search(0,emptySet(),0,0)
            val found = RoleMatching.assign(roles,caps.indices.toList(),{ u,r -> r in caps[u] },{ u,r -> scores[u][roles.indexOf(r)] },{ fees[it] },budget.toLong())
            assertEquals("case $it",best,Triple(found.size,found.sumOf { fees[it.unit] },found.sumOf { scores[it.unit][it.role] }))
        }
    }
    @Test fun largeImportedRequirementListHasNoBitmaskLimit() {
        val m = RoleMatching.assign(List(40) { "A" },(0 until 45).toList(),{ _,_ -> true })
        assertEquals(40,m.size); assertEquals(40,m.map { it.unit }.distinct().size)
    }
}
