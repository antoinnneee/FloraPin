package com.florapin.desktop.export

import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * L'export écrit sur le disque de l'utilisateur : un nom mal formé y produit
 * une erreur d'écriture ou, pire, écrase une photo déjà récupérée.
 */
class ExportNamingTest {

    @Test
    fun `les caracteres interdits par Windows sont remplaces`() {
        // Chaque caractère interdit devient un tiret, sans réintroduire
        // d'espace autour : « jardin: » donne donc « jardin- ».
        assertEquals(
            "Rose - jardin- 2026",
            ExportNaming.sanitize("Rose / jardin: 2026"),
        )
        assertEquals("a-b-c-d-e-f", ExportNaming.sanitize("""a\b/c:d*e?f"""))
        assertEquals("guillemets-", ExportNaming.sanitize("""guillemets" """))
    }

    @Test
    fun `les accents et l'apostrophe sont conserves`() {
        // Windows les accepte : les remplacer abîmerait inutilement les noms
        // d'espèces françaises.
        assertEquals("Bruyère d'été", ExportNaming.sanitize("Bruyère d'été"))
    }

    @Test
    fun `les espaces multiples sont normalises`() {
        assertEquals("Coquelicot des champs", ExportNaming.sanitize("Coquelicot   des  champs"))
    }

    @Test
    fun `les points et espaces finaux sont retires`() {
        // L'Explorateur les supprime en silence : les garder produirait un
        // fichier au nom différent de celui annoncé à l'utilisateur.
        assertEquals("espece", ExportNaming.sanitize("espece. . "))
    }

    @Test
    fun `un libelle vide donne un nom utilisable`() {
        assertEquals("sans-nom", ExportNaming.sanitize("   "))
        assertEquals("sans-nom", ExportNaming.sanitize("..."))
    }

    @Test
    fun `les noms tres longs sont tronques`() {
        val long = "a".repeat(200)
        assertEquals(80, ExportNaming.sanitize(long).length)
    }

    @Test
    fun `un fichier libre garde son nom`() {
        val dir = Files.createTempDirectory("florapin-export").toFile()
        try {
            assertEquals("photo.webp", ExportNaming.uniqueFile(dir, "photo.webp").name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `une collision produit un suffixe numerique incremental`() {
        val dir = Files.createTempDirectory("florapin-export").toFile()
        try {
            File(dir, "photo.webp").writeText("x")
            val second = ExportNaming.uniqueFile(dir, "photo.webp")
            assertEquals("photo (2).webp", second.name)

            second.writeText("x")
            assertEquals("photo (3).webp", ExportNaming.uniqueFile(dir, "photo.webp").name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `une collision sans extension reste geree`() {
        val dir = Files.createTempDirectory("florapin-export").toFile()
        try {
            File(dir, "photo").writeText("x")
            assertEquals("photo (2)", ExportNaming.uniqueFile(dir, "photo").name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `deux especes differentes ne collisionnent pas apres nettoyage`() {
        assertNotEquals(
            ExportNaming.sanitize("Rose rouge"),
            ExportNaming.sanitize("Rose blanche"),
        )
    }

    @Test
    fun `le nom nettoye ne contient plus aucun caractere interdit`() {
        val cleaned = ExportNaming.sanitize("""Test<>:"/\|?*fin""")
        assertTrue(
            cleaned.none { it in """<>:"/\|?*""" },
            "caractère interdit restant dans « $cleaned »",
        )
    }
}
