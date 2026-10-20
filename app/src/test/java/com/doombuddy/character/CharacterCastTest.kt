package com.doombuddy.character

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.doombuddy.data.PrefsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CharacterCastTest {
    @Test fun `roster has distinct selectable models and preserves the installed fox id`() {
        val cast = CharacterRoster.ALL
        assertEquals(listOf("Loui", "Buffer", "Goop", "Fenn"), cast.map { it.displayName })
        assertEquals(4, cast.map { it.id }.toSet().size)
        assertEquals(4, cast.map { it.modelAsset }.toSet().size)
        assertEquals(3, cast.take(3).map { it.species }.toSet().size)
        assertSame(CharacterRoster.MELT, CharacterRoster.byId("melt"))
        assertSame(CharacterRoster.LOUI, CharacterRoster.byId("removed-old-character"))
        cast.forEach { buddy ->
            (1..6).forEach { assertTrue(buddy.dialogue.forLevel(it).isNotEmpty()) }
            assertTrue(buddy.dialogue.celebration.isNotEmpty())
        }
    }

    @Test fun `all original assets bundle the complete named animation contract`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CharacterRoster.ALL.forEach { buddy ->
            val bytes = context.assets.open("characters/${buddy.modelAsset}").use { it.readBytes() }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0x46546c67, buffer.int)
            assertEquals(2, buffer.int)
            assertEquals(bytes.size, buffer.int)
            val jsonSize = buffer.int
            assertEquals(0x4e4f534a, buffer.int)
            val jsonBytes = ByteArray(jsonSize).also { buffer.get(it) }
            val json = JSONObject(String(jsonBytes, Charsets.UTF_8))
            val animations = json.getJSONArray("animations")
            val names = (0 until animations.length()).map { animations.getJSONObject(it).getString("name") }
            assertTrue("${buddy.id} must animate", names.isNotEmpty())
            if (buddy.id != "melt") {
                assertEquals(CharacterPerformance.entries.map { it.clipName }.toSet(), names.toSet())
                for (i in 0 until animations.length()) {
                    assertTrue("${buddy.id}/${names[i]} needs articulated movement",
                        animations.getJSONObject(i).getJSONArray("channels").length() > 10)
                }
            }
        }
    }

    @Test fun `each character selection survives a new repository instance`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = PrefsRepository(context)
        val original = repository.preferences.first().characterId
        try {
            CharacterRoster.ALL.forEach { buddy ->
                repository.setCharacter(buddy.id)
                val reopened = PrefsRepository(context)
                val saved = reopened.preferences.first()
                assertEquals(buddy.id, saved.characterId)
                assertEquals(buddy, reopened.character(saved))
            }
        } finally {
            repository.setCharacter(original)
        }
    }
}
