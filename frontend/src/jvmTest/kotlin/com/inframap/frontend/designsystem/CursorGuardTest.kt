package com.inframap.frontend.designsystem

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class CursorGuardTest {
    @Test
    fun allClickableUsagesEnforceHandCursorOrDocumentExemption() {
        val commonMainDir = File("src/commonMain/kotlin")
        assertTrue(commonMainDir.exists(), "commonMain must exist at ${commonMainDir.absolutePath}")

        val violations = scanDirectory(commonMainDir)

        assertTrue(
            violations.isEmpty(),
            "Found clickable usages without m3Clickable / m3ClickableCursor or '// no-hand-cursor:' exemption:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun syntheticNonCompliantSnippetTripsGuard() {
        val tempDir = createTempDirectory("cursor-guard-test")
        try {
            val nonCompliantTrailingLambda = File(tempDir.toFile(), "NonCompliantTrailingLambda.kt")
            nonCompliantTrailingLambda.writeText(
                """
                package com.inframap.frontend.test
                fun Component1() {
                    val m = Modifier.padding(4.dp).clickable { doSomething() }
                }
                """.trimIndent(),
            )

            val nonCompliantToggleable = File(tempDir.toFile(), "NonCompliantToggleable.kt")
            nonCompliantToggleable.writeText(
                """
                package com.inframap.frontend.test
                fun Component2() {
                    val m = Modifier.toggleable(value = true, onValueChange = {})
                }
                """.trimIndent(),
            )

            val nonCompliantSelectable = File(tempDir.toFile(), "NonCompliantSelectable.kt")
            nonCompliantSelectable.writeText(
                """
                package com.inframap.frontend.test
                fun Component3() {
                    val m = Modifier.selectable(selected = true, onClick = {})
                }
                """.trimIndent(),
            )

            val nonCompliantBareHoverIcon = File(tempDir.toFile(), "NonCompliantBareHoverIcon.kt")
            nonCompliantBareHoverIcon.writeText(
                """
                package com.inframap.frontend.test
                fun Component4() {
                    val m = Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = {})
                }
                """.trimIndent(),
            )

            val compliantSamples = File(tempDir.toFile(), "CompliantSamples.kt")
            compliantSamples.writeText(
                """
                package com.inframap.frontend.test
                fun Component5() {
                    val m1 = Modifier.m3ClickableCursor().clickable { }
                    // no-hand-cursor: modal backdrop scrim
                    val m2 = Modifier.clickable { }
                    val m3 = Modifier.pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true).clickable { }
                }
                """.trimIndent(),
            )

            val violations = scanDirectory(tempDir.toFile())
            assertTrue(
                violations.any { it.contains("NonCompliantTrailingLambda.kt") && it.contains("clickable") },
                "Must detect non-compliant trailing lambda .clickable { }",
            )
            assertTrue(
                violations.any { it.contains("NonCompliantToggleable.kt") && it.contains("toggleable") },
                "Must detect non-compliant .toggleable",
            )
            assertTrue(
                violations.any { it.contains("NonCompliantSelectable.kt") && it.contains("selectable") },
                "Must detect non-compliant .selectable",
            )
            assertTrue(
                violations.any { it.contains("NonCompliantBareHoverIcon.kt") && it.contains("pointerHoverIcon") },
                "Must detect bare pointerHoverIcon without overrideDescendants = true",
            )
            assertTrue(
                violations.none { it.contains("CompliantSamples.kt") },
                "Compliant samples must not produce violations:\n" + violations.joinToString("\n"),
            )
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun scanDirectory(dir: File): List<String> {
        val violations = mutableListOf<String>()
        val interactivePattern = Regex("""\.(clickable|toggleable|selectable)\b""")

        dir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                val isCommentOrImport =
                    trimmed.startsWith("//") ||
                        trimmed.startsWith("/*") ||
                        trimmed.startsWith("*") ||
                        trimmed.startsWith("import ")

                val isInteractiveInvocation =
                    interactivePattern.containsMatchIn(trimmed) && !isCommentOrImport

                if (isInteractiveInvocation) {
                    val prevLine = if (index > 0) lines[index - 1].trim() else ""
                    val hasExemption = prevLine.contains("// no-hand-cursor:") || trimmed.contains("// no-hand-cursor:")
                    if (!hasExemption) {
                        val startWindow = maxOf(0, index - 8)
                        val window = lines.subList(startWindow, index + 1).joinToString("\n")
                        val hasOverrideDescendants =
                            window.contains("overrideDescendants = true") ||
                                window.contains("overrideDescendants=true")
                        val hasCursor =
                            window.contains("m3Clickable") ||
                                window.contains("m3ClickableCursor") ||
                                (window.contains("pointerHoverIcon") && hasOverrideDescendants)
                        if (!hasCursor) {
                            violations.add("${file.name}:${index + 1}: $trimmed")
                        }
                    }
                }

                val isPointerHoverIconInvocation =
                    trimmed.contains("pointerHoverIcon") && !isCommentOrImport

                if (isPointerHoverIconInvocation) {
                    val startWindow = maxOf(0, index - 2)
                    val endWindow = minOf(lines.size, index + 5)
                    val window = lines.subList(startWindow, endWindow).joinToString("\n")
                    val hasOverrideDescendants =
                        window.contains("overrideDescendants = true") ||
                            window.contains("overrideDescendants=true")
                    if (!hasOverrideDescendants) {
                        violations.add(
                            "${file.name}:${index + 1}: pointerHoverIcon without overrideDescendants = true: $trimmed",
                        )
                    }
                }
            }
        }

        return violations
    }
}
