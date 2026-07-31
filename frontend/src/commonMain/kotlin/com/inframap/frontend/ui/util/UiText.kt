package com.inframap.frontend.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Immutable
sealed interface UiText {
    @Immutable
    data class DynamicString(
        val value: String,
    ) : UiText

    @Immutable
    data class Resource(
        val resId: StringResource,
        val args: List<Any> = emptyList(),
    ) : UiText {
        constructor(resId: StringResource, vararg args: Any) : this(resId, args.toList())
    }

    @Suppress("SpreadOperator")
    @Composable
    fun asString(): String =
        when (this) {
            is DynamicString -> value
            is Resource -> stringResource(resId, *args.toTypedArray())
        }

    @Suppress("SpreadOperator")
    suspend fun asStringAsync(): String =
        when (this) {
            is DynamicString -> value
            is Resource -> getString(resId, *args.toTypedArray())
        }
}

fun String.asUiText(): UiText = UiText.DynamicString(this)

fun StringResource.asUiText(vararg args: Any): UiText = UiText.Resource(this, *args)
