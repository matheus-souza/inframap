package com.inframap.frontend.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ToastType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

data class ToastMessage(
    val id: Long = idCounter++,
    val message: String,
    val type: ToastType,
    val durationMs: Long,
) {
    companion object {
        private var idCounter = 0L
    }
}

object InfraMapToastManager {
    private val _toasts = MutableStateFlow<List<ToastMessage>>(emptyList())
    val toasts: StateFlow<List<ToastMessage>> = _toasts.asStateFlow()

    fun showToast(
        message: String,
        type: ToastType = ToastType.INFO,
        durationMs: Long = 3000L,
    ) {
        val toast = ToastMessage(message = message, type = type, durationMs = durationMs)
        _toasts.update { current -> current + toast }
    }

    internal fun removeToast(id: Long) {
        _toasts.update { current -> current.filter { it.id != id } }
    }

    internal fun clear() {
        _toasts.value = emptyList()
    }
}

@Composable
fun InfraMapToastHost(modifier: Modifier = Modifier) {
    val toasts by InfraMapToastManager.toasts.collectAsState()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            toasts.forEach { toast ->
                ToastItem(toast = toast, onDismiss = { InfraMapToastManager.removeToast(it.id) })
            }
        }
    }
}

@Composable
private fun ToastItem(
    toast: ToastMessage,
    onDismiss: (ToastMessage) -> Unit,
) {
    var visible by remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(toast.id) {
        visible = true
        delay(toast.durationMs)
        visible = false
        delay(300)
        onDismiss(toast)
    }

    val (icon, color) =
        when (toast.type) {
            ToastType.INFO -> Icons.Default.Info to accentPrimary
            ToastType.SUCCESS -> Icons.Default.CheckCircle to statusOnline
            ToastType.WARNING -> Icons.Default.Warning to statusWarning
            ToastType.ERROR -> Icons.Default.Error to statusAlert
        }

    AnimatedVisibility(
        visible = visible,
        enter =
            slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300),
            ) + fadeIn(animationSpec = tween(300)),
        exit =
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300),
            ) + fadeOut(animationSpec = tween(300)),
    ) {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = toast.message,
                color = InfraMapTextPrimary,
                style = InfraMapTypography.bodyMedium,
            )
        }
    }
}
