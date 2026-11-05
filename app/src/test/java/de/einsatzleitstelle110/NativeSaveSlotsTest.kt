package de.einsatzleitstelle110

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NativeSaveSlotsTest {
    private fun <T> inDirectory(block: (File, SlotRepository) -> T): T {
        val dir = Files.createTempDirectory("el110-slot-test").toFile()
        try { return block(dir, SlotRepository(dir)) } finally { dir.deleteRecursively() }
    }

    @Test fun fiveSlotsStayIndependentAndRoundTripTheirOwnStates() = inDirectory { dir, repo ->
        assertEquals(5, repo.list().size)
        assertTrue(repo.list().none { it.hasSave || it.corrupt })
        for (slot in 1..5) {
            val state = GameState(day = slot, budget = slot * 10000)
            state.logs.add("Dienst $slot")
            repo.save(slot, "Dienst $slot", state)
            state.logs.clear()
            state.budget = 0
        }
        val reopened = SlotRepository(dir)
        for (slot in 1..5) {
            val state = reopened.load(slot)
            assertEquals(slot, state.day)
            assertEquals(slot * 10000, state.budget)
            assertEquals(listOf("Dienst $slot"), state.logs)
            assertEquals("Dienst $slot", reopened.list()[slot - 1].name)
        }
        reopened.delete(3)
        assertFalse(reopened.list()[2].hasSave)
        assertEquals(40000, reopened.load(4).budget)
    }

    @Test fun corruptReplacementRecoversLastValidBackupAndDoesNotPoisonIt() = inDirectory { dir, repo ->
        repo.save(1, "Früh", GameState(day = 2))
        repo.save(1, "Spät", GameState(day = 3))
        File(dir, "slot-1.json").writeText("corrupt")
        assertEquals(2, repo.load(1).day)
        assertTrue(repo.list()[0].recoveredFromBackup)
        repo.save(1, "Nacht", GameState(day = 4))
        File(dir, "slot-1.json").writeText("corrupt again")
        assertEquals(2, repo.load(1).day)
        assertEquals("Früh", repo.list()[0].name)
        repo.delete(1)
        assertFalse(File(dir, "slot-1.json.backup").exists())
        assertFalse(repo.list()[0].hasSave)
    }

    @Test fun legacyNativeSchemaFiveMigratesInsideSlotWithoutChangingActiveAutosave() = inDirectory { dir, repo ->
        val native = GameStore.toJson(GameState(day = 9)).put("schemaVersion", 5)
        native.remove("chief")
        val active = File(dir, "einsatzleitstelle-native-v1.json")
        active.writeText("active remains untouched")
        File(dir, "slot-2.json").writeText(native.toString())
        assertEquals(9, repo.load(2).day)
        assertTrue(repo.load(2).chief.stations.isEmpty())
        assertEquals("active remains untouched", active.readText())
        assertEquals("Importierter Spielstand", repo.list()[1].name)
    }

    @Test fun invalidIndicesAndNamesCannotChoosePathsOrWriteFiles() = inDirectory { dir, repo ->
        for (slot in listOf(-1, 0, 6, Int.MAX_VALUE)) {
            try { repo.save(slot, "Test", GameState()); fail("index $slot") } catch (_: IllegalArgumentException) {}
            try { repo.load(slot); fail("index $slot") } catch (_: IllegalArgumentException) {}
        }
        for (name in listOf("", "   ", "a".repeat(49), "bad\nname")) {
            try { repo.save(1, name, GameState()); fail("name $name") } catch (_: IllegalArgumentException) {}
        }
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        repo.save(1, "../../name is only metadata", GameState())
        assertEquals(listOf("slot-1.json"), dir.listFiles()!!.map { it.name })
    }

    @Test fun emptyAndCorruptSlotsAreDistinctAndNeverLoadArbitraryDefaults() = inDirectory { dir, repo ->
        try { repo.load(4); fail("empty") } catch (_: IOException) {}
        File(dir, "slot-4.json").writeText(JSONObject().put("slotFormat", 99).toString())
        assertTrue(repo.list()[3].corrupt)
        assertFalse(repo.list()[3].hasSave)
        try { repo.load(4); fail("corrupt") } catch (_: IOException) {}
    }

    @Test fun competingRepositoriesSerializeAtomicReplacements() = inDirectory { dir, repo ->
        val workers = Executors.newFixedThreadPool(3)
        try {
            val results = workers.invokeAll((1..12).map { day -> Callable {
                SlotRepository(dir).save(1, "Dienst $day", GameState(day = day))
            } })
            results.forEach { assertTrue(it.get().hasSave) }
            assertTrue(repo.load(1).day in 1..12)
            assertNotNull(GameStore.fromJson(JSONObject(File(dir, "slot-1.json.backup").readText()).getJSONObject("state")))
            assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
        } finally { workers.shutdownNow() }
    }
}
