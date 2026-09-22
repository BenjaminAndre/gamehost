package com.gamehost.content

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentRepositoryTest {

    private val root = ContentId("fake://root")
    private val cartes = ContentId("fake://cartes")
    private val donjon = ContentId("fake://donjon")

    private fun repository() = ContentRepository(
        FakeContentSource(
            rootId = root,
            tree = mapOf(
                root to listOf(
                    image("fake://b", "carte10.png", root.value),
                    folder(cartes.value, "Cartes", root.value),
                    image("fake://a", "carte2.png", root.value),
                    folder("fake://hidden", ".gamehost", root.value),
                ),
                cartes to listOf(folder(donjon.value, "Donjon", cartes.value)),
                donjon to listOf(image("fake://salle", "Salle-4.png", donjon.value)),
            ),
        ),
    )

    @Test
    fun `dotfiles are hidden from the browser`() = runTest {
        val names = repository().children(root).map { it.displayName }
        assertEquals(listOf("Cartes", "carte2.png", "carte10.png"), names)
    }

    @Test
    fun `listings come back ordered`() = runTest {
        val images = repository().images(root).map { it.displayName }
        assertEquals(listOf("carte2.png", "carte10.png"), images)
    }

    @Test
    fun `a second read is served from cache`() = runTest {
        val source = FakeContentSource(rootId = root, tree = mapOf(root to listOf(image("x", "a.png"))))
        val repository = ContentRepository(source)

        repository.children(root)
        repository.children(root)

        assertEquals(1, source.listCalls)
    }

    @Test
    fun `refresh re-queries the source`() = runTest {
        val source = FakeContentSource(rootId = root, tree = mapOf(root to listOf(image("x", "a.png"))))
        val repository = ContentRepository(source)

        repository.children(root)
        repository.children(root, refresh = true)

        assertEquals(2, source.listCalls)
    }

    /** The cross-folder recall that makes the slot bank worth having (§6.1). */
    @Test
    fun `resolve walks several levels from the campaign root`() = runTest {
        val item = repository().resolve(ContentPath("Cartes/Donjon/Salle-4.png"))
        assertEquals("Salle-4.png", item?.displayName)
    }

    @Test
    fun `resolve returns null for a renamed file`() = runTest {
        assertNull(repository().resolve(ContentPath("Cartes/Donjon/Salle-5.png")))
    }

    @Test
    fun `resolve returns null when a path segment is a file, not a folder`() = runTest {
        assertNull(repository().resolve(ContentPath("carte2.png/nested.png")))
    }

    @Test
    fun `resolve returns null for an empty path`() = runTest {
        assertNull(repository().resolve(ContentPath("")))
    }
}
