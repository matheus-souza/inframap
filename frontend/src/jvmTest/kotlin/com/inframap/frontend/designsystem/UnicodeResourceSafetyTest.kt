package com.inframap.frontend.designsystem

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class UnicodeResourceSafetyTest {
    private val forbiddenCodePoints =
        setOf(
            0x21B5, // ↵ Downwards Arrow with Corner Leftwards
            0x2715, // ✕ Multiplication X
            0x26A1, // ⚡ High Voltage Sign
            0x1F5A5, // 🖥 Desktop Computer
            0x1F310, // 🌐 Globe with Meridians
            0x1F9ED, // 🧭 Compass
            0x1F680, // 🚀 Rocket
        )

    private val allowedCodePoints =
        setOf(
            0x00A9, // ©
            0x00AE, // ®
            0x2122, // ™
            0x00B0, // °
            0x00B1, // ±
            0x00A7, // §
            0x00B6, // ¶
            0x2022, // •
            0x2013, // –
            0x2014, // —
            0x00AB, // «
            0x00BB, // »
        )

    @Test
    fun composeResourceStringsDoNotContainRawEmojisOrUnsupportedSymbols() {
        val resourcesDir = File("src/commonMain/composeResources")
        assertTrue(resourcesDir.exists(), "composeResources directory must exist at ${resourcesDir.absolutePath}")

        val violations = mutableListOf<String>()

        resourcesDir.walkTopDown().filter { it.extension == "xml" }.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    var i = 0
                    while (i < line.length) {
                        val codePoint = line.codePointAt(i)
                        val charCount = Character.charCount(codePoint)
                        val category = Character.getType(codePoint)

                        val isEmojiOrForbidden =
                            category == Character.SURROGATE.toInt() ||
                                category == Character.OTHER_SYMBOL.toInt() ||
                                category == Character.MODIFIER_SYMBOL.toInt() ||
                                forbiddenCodePoints.contains(codePoint)

                        if (isEmojiOrForbidden && codePoint !in allowedCodePoints) {
                            violations.add(
                                "${file.name}:${index + 1}: contains code point U+${Integer.toHexString(codePoint).uppercase()} in: $line",
                            )
                        }
                        i += charCount
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found raw emojis/symbols in composeResources XML strings:\n${violations.joinToString("\n")}",
        )
    }

    @Test
    fun productTourStringsDoNotContainNavRailReference() {
        val tourStepFile = File("src/commonMain/kotlin/com/inframap/frontend/ui/tour/ProductTourStep.kt")
        assertTrue(tourStepFile.exists(), "ProductTourStep.kt must exist")

        val content = tourStepFile.readText()
        assertTrue(
            !content.contains("(NavRail)"),
            "ProductTourStep.kt must not contain technical (NavRail) text in user-facing tour strings",
        )
    }
}
