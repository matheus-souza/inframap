package com.inframap.frontend.ui.util

import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.loading
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UiTextTest {
    @Test
    fun dynamicStringReturnsCorrectValue() =
        runTest {
            val uiText = UiText.DynamicString("Hello World")
            assertIs<UiText.DynamicString>(uiText)
            assertEquals("Hello World", uiText.value)
            assertEquals("Hello World", uiText.asStringAsync())
        }

    @Test
    fun stringExtensionAsUiTextCreatesDynamicString() {
        val string = "Test String"
        val uiText = string.asUiText()
        assertIs<UiText.DynamicString>(uiText)
        assertEquals("Test String", uiText.value)
    }

    @Test
    fun resourceUiTextStoresResIdAndArgs() {
        val uiText = UiText.Resource(Res.string.loading, "arg1")
        assertIs<UiText.Resource>(uiText)
        assertEquals(Res.string.loading, uiText.resId)
        assertEquals(listOf("arg1"), uiText.args)
    }

    @Test
    fun stringResourceExtensionAsUiTextCreatesResource() {
        val uiText = Res.string.loading.asUiText("arg1", "arg2")
        assertIs<UiText.Resource>(uiText)
        assertEquals(Res.string.loading, uiText.resId)
        assertEquals(listOf("arg1", "arg2"), uiText.args)
    }
}
