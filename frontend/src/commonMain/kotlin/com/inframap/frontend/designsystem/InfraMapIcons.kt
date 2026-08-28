package com.inframap.frontend.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object InfraMapIcons {
    val Dashboard: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Dashboard",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(3f, 13f)
                    horizontalLineToRelative(8f)
                    verticalLineTo(3f)
                    horizontalLineTo(3f)
                    verticalLineToRelative(10f)
                    close()
                    moveTo(3f, 21f)
                    horizontalLineToRelative(8f)
                    verticalLineToRelative(-6f)
                    horizontalLineTo(3f)
                    verticalLineToRelative(6f)
                    close()
                    moveTo(13f, 21f)
                    horizontalLineToRelative(8f)
                    verticalLineTo(11f)
                    horizontalLineToRelative(-8f)
                    verticalLineToRelative(10f)
                    close()
                    moveTo(13f, 3f)
                    verticalLineToRelative(6f)
                    horizontalLineToRelative(8f)
                    verticalLineTo(3f)
                    horizontalLineToRelative(-8f)
                    close()
                }
            }.build()
    }

    val Dns: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Dns",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(20f, 13f)
                    horizontalLineTo(4f)
                    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                    verticalLineToRelative(4f)
                    curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                    horizontalLineToRelative(16f)
                    curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                    verticalLineToRelative(-4f)
                    curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                    close()
                    moveTo(7f, 19f)
                    curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                    reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
                    reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                    reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
                    close()
                    moveTo(20f, 3f)
                    horizontalLineTo(4f)
                    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                    verticalLineToRelative(4f)
                    curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                    horizontalLineToRelative(16f)
                    curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                    verticalLineTo(5f)
                    curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                    close()
                    moveTo(7f, 9f)
                    curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                    reflectiveCurveToRelative(0.9f, -2f, 2f, -2f)
                    reflectiveCurveToRelative(2f, 0.9f, 2f, 2f)
                    reflectiveCurveToRelative(-0.9f, 2f, -2f, 2f)
                    close()
                }
            }.build()
    }

    val MoveToInbox: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "MoveToInbox",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(19f, 3f)
                    horizontalLineTo(4.99f)
                    curveToRelative(-1.11f, 0f, -1.98f, 0.9f, -1.98f, 2f)
                    lineTo(3f, 19f)
                    curveToRelative(0f, 1.1f, 0.88f, 2f, 1.99f, 2f)
                    horizontalLineTo(19f)
                    curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                    verticalLineTo(5f)
                    curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                    close()
                    moveTo(19f, 15f)
                    horizontalLineToRelative(-4f)
                    curveToRelative(0f, 1.66f, -1.35f, 3f, -3f, 3f)
                    reflectiveCurveToRelative(-3f, -1.34f, -3f, -3f)
                    horizontalLineTo(4.99f)
                    verticalLineTo(5f)
                    horizontalLineTo(19f)
                    verticalLineToRelative(10f)
                    close()
                    moveTo(16f, 10f)
                    horizontalLineToRelative(-2f)
                    verticalLineTo(7f)
                    horizontalLineToRelative(-4f)
                    verticalLineToRelative(3f)
                    horizontalLineTo(8f)
                    lineToRelative(4f, 4f)
                    lineToRelative(4f, -4f)
                    close()
                }
            }.build()
    }

    val Lan: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Lan",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(10f, 2f)
                    verticalLineToRelative(3f)
                    horizontalLineTo(8f)
                    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                    verticalLineToRelative(3f)
                    horizontalLineTo(2f)
                    verticalLineToRelative(8f)
                    horizontalLineToRelative(8f)
                    verticalLineToRelative(-8f)
                    horizontalLineTo(6f)
                    verticalLineTo(7f)
                    horizontalLineToRelative(5f)
                    verticalLineToRelative(3f)
                    horizontalLineToRelative(2f)
                    verticalLineTo(7f)
                    horizontalLineToRelative(5f)
                    verticalLineToRelative(3f)
                    horizontalLineToRelative(-4f)
                    verticalLineToRelative(8f)
                    horizontalLineToRelative(8f)
                    verticalLineToRelative(-8f)
                    horizontalLineToRelative(-4f)
                    verticalLineTo(7f)
                    curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                    horizontalLineToRelative(-2f)
                    verticalLineTo(2f)
                    horizontalLineToRelative(-4f)
                    close()
                }
            }.build()
    }

    val Radar: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Radar",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12f, 2f)
                    curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                    reflectiveCurveTo(6.48f, 22f, 12f, 22f)
                    reflectiveCurveTo(22f, 17.52f, 22f, 12f)
                    reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                    close()
                    moveTo(12f, 20f)
                    curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
                    reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
                    reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                    reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
                    close()
                    moveTo(6.34f, 17.66f)
                    curveTo(4.89f, 16.22f, 4f, 14.22f, 4f, 12f)
                    horizontalLineToRelative(2f)
                    curveToRelative(0f, 1.66f, 0.68f, 3.15f, 1.76f, 4.24f)
                    lineToRelative(1.42f, -1.42f)
                    curveTo(8.45f, 14.1f, 8f, 13.11f, 8f, 12f)
                    curveToRelative(0f, -2.21f, 1.79f, -4f, 4f, -4f)
                    reflectiveCurveToRelative(4f, 1.79f, 4f, 4f)
                    curveToRelative(0f, 1.11f, -0.45f, 2.1f, -1.18f, 2.82f)
                    lineToRelative(1.42f, 1.42f)
                    curveTo(17.32f, 15.15f, 18f, 13.66f, 18f, 12f)
                    horizontalLineToRelative(2f)
                    curveToRelative(0f, 2.22f, -0.89f, 4.22f, -2.34f, 5.66f)
                    lineToRelative(-1.42f, -1.42f)
                    curveTo(17.32f, 15.15f, 18f, 13.66f, 18f, 12f)
                    curveToRelative(0f, -3.31f, -2.69f, -6f, -6f, -6f)
                    reflectiveCurveToRelative(-6f, 2.69f, -6f, 6f)
                    curveToRelative(0f, 1.66f, 0.68f, 3.15f, 1.76f, 4.24f)
                    lineToRelative(-1.42f, 1.42f)
                    close()
                    moveTo(12f, 10f)
                    curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                    reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                    reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                    reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                    close()
                }
            }.build()
    }

    val AccountTree: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "AccountTree",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(22f, 11f)
                    verticalLineTo(3f)
                    horizontalLineToRelative(-8f)
                    verticalLineToRelative(3f)
                    horizontalLineTo(9f)
                    verticalLineTo(3f)
                    horizontalLineTo(1f)
                    verticalLineToRelative(8f)
                    horizontalLineToRelative(5f)
                    verticalLineToRelative(4f)
                    horizontalLineTo(3f)
                    verticalLineToRelative(6f)
                    horizontalLineToRelative(8f)
                    verticalLineToRelative(-6f)
                    horizontalLineToRelative(-3f)
                    verticalLineToRelative(-2f)
                    horizontalLineToRelative(5f)
                    verticalLineTo(9f)
                    horizontalLineToRelative(3f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(-3f)
                    verticalLineToRelative(6f)
                    horizontalLineToRelative(8f)
                    verticalLineToRelative(-6f)
                    horizontalLineToRelative(-5f)
                    verticalLineTo(9f)
                    horizontalLineToRelative(3f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(3f)
                    close()
                }
            }.build()
    }

    val KeyboardReturn: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "KeyboardReturn",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(19f, 7f)
                    verticalLineToRelative(4f)
                    horizontalLineTo(5.83f)
                    lineToRelative(3.58f, -3.59f)
                    lineTo(8f, 6f)
                    lineToRelative(-6f, 6f)
                    lineToRelative(6f, 6f)
                    lineToRelative(1.41f, -1.41f)
                    lineTo(5.83f, 13f)
                    horizontalLineTo(21f)
                    verticalLineTo(7f)
                    horizontalLineToRelative(-2f)
                    close()
                }
            }.build()
    }
}
