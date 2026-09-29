package me.misa198.airmedy

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The Arabic translation is kept complete: every translatable string, plural and string array
 * in values/ needs a counterpart in values-ar/. The other locales may lag behind by design,
 * so only Arabic is enforced.
 */
class ArabicTranslationCompletenessTest {
    private val resDir = File("src/main/res")

    @Test
    fun everyTranslatableKeyHasAnArabicTranslation() {
        val source = translatableKeys(File(resDir, "values/strings.xml"))
        val arabic = translatableKeys(File(resDir, "values-ar/strings.xml"))
        val missing = source - arabic
        assertTrue("Missing from values-ar/strings.xml: ${missing.sorted()}", missing.isEmpty())
    }

    @Test
    fun arabicHasNoKeysThatNoLongerExist() {
        val source = translatableKeys(File(resDir, "values/strings.xml"))
        val arabic = translatableKeys(File(resDir, "values-ar/strings.xml"))
        val extra = arabic - source
        assertTrue("Not in values/strings.xml: ${extra.sorted()}", extra.isEmpty())
    }

    private fun translatableKeys(file: File): Set<String> {
        assertTrue("${file.path} not found (working directory ${File(".").absolutePath})", file.isFile)
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val nodes = root.childNodes
        return (0 until nodes.length).asSequence()
            .mapNotNull { nodes.item(it) as? Element }
            .filter { it.tagName in setOf("string", "plurals", "string-array") }
            .filter { it.getAttribute("translatable") != "false" }
            .map { "${it.tagName}/${it.getAttribute("name")}" }
            .toSet()
    }
}
