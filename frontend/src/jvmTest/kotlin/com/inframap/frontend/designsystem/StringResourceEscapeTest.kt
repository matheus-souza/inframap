package com.inframap.frontend.designsystem

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compose Multiplatform resources unescape only `\n`, `\t`, `\uXXXX` and `\\`. An Android-style
 * `\"` or `\'` reaches the screen with its backslash, so `excluir a sub-rede \"Alvo\"?` is shown
 * literally. Values must use plain quotes instead.
 */
class StringResourceEscapeTest {
    private val escapedQuote = Regex("""\\["']""")

    @Test
    fun stringValuesDoNotContainBackslashEscapedQuotes() {
        val resourcesDir = File("src/commonMain/composeResources")
        assertTrue(resourcesDir.exists(), "composeResources directory must exist at ${resourcesDir.absolutePath}")

        val files = resourcesDir.walkTopDown().filter { it.isFile && it.extension == "xml" }.toList()
        assertTrue(files.isNotEmpty(), "no XML value resources found under ${resourcesDir.absolutePath}")

        val violations =
            files.flatMap { file ->
                // Parsing decodes entities, so `\&quot;` is caught as the `\"` it turns into.
                val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val values = document.getElementsByTagName("*")
                (0 until values.length)
                    .map { values.item(it) as Element }
                    .filter { it.hasAttribute("name") && escapedQuote.containsMatchIn(it.textContent) }
                    .map { "${file.parentFile.name}/${file.name}: ${it.getAttribute("name")} = ${it.textContent}" }
            }

        assertTrue(
            violations.isEmpty(),
            "String resources with a backslash before a quote (rendered literally):\n${violations.joinToString("\n")}",
        )
    }
}
