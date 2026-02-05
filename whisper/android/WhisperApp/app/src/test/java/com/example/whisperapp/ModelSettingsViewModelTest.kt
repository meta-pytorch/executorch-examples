package com.example.whisperapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for ModelSettingsViewModel.
 */
class ModelSettingsViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `listLocalFiles returns empty list for non-existent directory`() {
        val files = ModelSettingsViewModel.listLocalFiles(
            "/non/existent/path",
            arrayOf(".pte")
        )
        assertTrue(files.isEmpty())
    }

    @Test
    fun `listLocalFiles returns empty list for empty directory`() {
        val emptyDir = tempFolder.newFolder("empty")
        val files = ModelSettingsViewModel.listLocalFiles(
            emptyDir.absolutePath,
            arrayOf(".pte")
        )
        assertTrue(files.isEmpty())
    }

    @Test
    fun `listLocalFiles filters by extension`() {
        val dir = tempFolder.newFolder("models")
        File(dir, "model1.pte").createNewFile()
        File(dir, "model2.pte").createNewFile()
        File(dir, "tokenizer.json").createNewFile()
        File(dir, "readme.txt").createNewFile()

        val pteFiles = ModelSettingsViewModel.listLocalFiles(
            dir.absolutePath,
            arrayOf(".pte")
        )

        assertEquals(2, pteFiles.size)
        assertTrue(pteFiles.all { it.endsWith(".pte") })
    }

    @Test
    fun `listLocalFiles supports multiple extensions`() {
        val dir = tempFolder.newFolder("tokens")
        File(dir, "tokenizer.json").createNewFile()
        File(dir, "tokenizer.bin").createNewFile()
        File(dir, "tokenizer.model").createNewFile()
        File(dir, "model.pte").createNewFile()

        val tokenizerFiles = ModelSettingsViewModel.listLocalFiles(
            dir.absolutePath,
            arrayOf(".json", ".bin", ".model")
        )

        assertEquals(3, tokenizerFiles.size)
        assertTrue(tokenizerFiles.none { it.endsWith(".pte") })
    }

    @Test
    fun `listLocalFiles returns absolute paths`() {
        val dir = tempFolder.newFolder("abs")
        File(dir, "model.pte").createNewFile()

        val files = ModelSettingsViewModel.listLocalFiles(
            dir.absolutePath,
            arrayOf(".pte")
        )

        assertEquals(1, files.size)
        assertTrue(files[0].startsWith("/"))
        assertTrue(files[0].contains(dir.name))
    }

    @Test
    fun `listLocalFiles returns sorted list`() {
        val dir = tempFolder.newFolder("sorted")
        File(dir, "zebra.pte").createNewFile()
        File(dir, "alpha.pte").createNewFile()
        File(dir, "beta.pte").createNewFile()

        val files = ModelSettingsViewModel.listLocalFiles(
            dir.absolutePath,
            arrayOf(".pte")
        )

        assertEquals(3, files.size)
        assertTrue(files[0].endsWith("alpha.pte"))
        assertTrue(files[1].endsWith("beta.pte"))
        assertTrue(files[2].endsWith("zebra.pte"))
    }

    @Test
    fun `listLocalFiles ignores directories`() {
        val dir = tempFolder.newFolder("mixed")
        File(dir, "model.pte").createNewFile()
        File(dir, "subdir.pte").mkdirs() // Creates a directory with .pte extension

        val files = ModelSettingsViewModel.listLocalFiles(
            dir.absolutePath,
            arrayOf(".pte")
        )

        assertEquals(1, files.size)
        assertTrue(files[0].endsWith("model.pte"))
    }

    @Test
    fun `listLocalFiles is case insensitive for extensions`() {
        val dir = tempFolder.newFolder("case")
        File(dir, "model.pte").createNewFile()
        File(dir, "model2.PTE").createNewFile()
        File(dir, "model3.Pte").createNewFile()

        val files = ModelSettingsViewModel.listLocalFiles(
            dir.absolutePath,
            arrayOf(".pte")
        )

        assertEquals(3, files.size)
    }
}
