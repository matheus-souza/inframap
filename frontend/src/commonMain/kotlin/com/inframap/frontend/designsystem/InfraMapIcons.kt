package com.inframap.frontend.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("LargeClass")
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

    val Timer: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Timer",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(15f, 1f)
                    horizontalLineTo(9f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(6f)
                    verticalLineTo(1f)
                    close()
                    moveTo(11f, 14f)
                    horizontalLineToRelative(2f)
                    verticalLineTo(8f)
                    horizontalLineToRelative(-2f)
                    verticalLineToRelative(6f)
                    close()
                    moveTo(19.03f, 7.39f)
                    lineToRelative(1.42f, -1.42f)
                    curveToRelative(-0.43f, -0.51f, -0.9f, -0.99f, -1.41f, -1.41f)
                    lineToRelative(-1.42f, 1.42f)
                    curveTo(16.07f, 4.74f, 14.12f, 4f, 12f, 4f)
                    curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                    curveToRelative(0f, 4.97f, 4.02f, 9f, 9f, 9f)
                    reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
                    curveToRelative(0f, -2.12f, -0.74f, -4.07f, -1.97f, -5.61f)
                    close()
                    moveTo(12f, 20f)
                    curveToRelative(-3.87f, 0f, -7f, -3.13f, -7f, -7f)
                    reflectiveCurveToRelative(3.13f, -7f, 7f, -7f)
                    reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
                    reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
                    close()
                }
            }.build()
    }

    val Schedule: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Schedule",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(11.99f, 2f)
                    curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                    reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
                    curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                    reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
                    close()
                    moveTo(12f, 20f)
                    curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
                    reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
                    reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
                    reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
                    close()
                    moveTo(12.5f, 7f)
                    horizontalLineTo(11f)
                    verticalLineToRelative(6f)
                    lineToRelative(5.25f, 3.15f)
                    lineToRelative(0.75f, -1.23f)
                    lineToRelative(-4.5f, -2.67f)
                    verticalLineTo(7f)
                    close()
                }
            }.build()
    }

    val NightsStay: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "NightsStay",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(11.1f, 12.08f)
                    curveToRelative(-2.33f, -4.51f, -0.5f, -8.48f, 0.53f, -10.07f)
                    curveToRelative(-0.19f, -0.01f, -0.36f, -0.01f, -0.55f, -0.01f)
                    curveToRelative(-5.52f, 0f, -10f, 4.48f, -10f, 10f)
                    curveToRelative(0f, 5.52f, 4.48f, 10f, 10f, 10f)
                    curveToRelative(3.96f, 0f, 7.37f, -2.3f, 8.99f, -5.61f)
                    curveToRelative(-3.87f, 0.77f, -7.53f, -1.04f, -8.97f, -4.31f)
                    close()
                }
                path(fill = SolidColor(Color.Black)) {
                    moveTo(19.5f, 7f)
                    lineToRelative(0.62f, -1.38f)
                    lineTo(21.5f, 5f)
                    lineToRelative(-1.38f, -0.62f)
                    lineTo(19.5f, 3f)
                    lineToRelative(-0.62f, 1.38f)
                    lineTo(17.5f, 5f)
                    lineToRelative(1.38f, 0.62f)
                    close()
                }
            }.build()
    }

    val PauseCircle: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "PauseCircle",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12f, 2f)
                    curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                    reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                    reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                    reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                    close()
                    moveTo(11f, 16f)
                    horizontalLineTo(9f)
                    verticalLineTo(8f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(8f)
                    close()
                    moveTo(15f, 16f)
                    horizontalLineToRelative(-2f)
                    verticalLineTo(8f)
                    horizontalLineToRelative(2f)
                    verticalLineToRelative(8f)
                    close()
                }
            }.build()
    }

    val NetworkCheck: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "NetworkCheck",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(15.9f, 5f)
                    curveToRelative(-0.17f, 0f, -0.32f, 0.09f, -0.41f, 0.23f)
                    lineToRelative(-0.07f, 0.15f)
                    lineToRelative(-5.18f, 11.65f)
                    curveToRelative(-0.16f, 0.29f, -0.24f, 0.61f, -0.24f, 0.97f)
                    curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                    reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                    curveToRelative(0f, -0.35f, -0.09f, -0.68f, -0.24f, -0.96f)
                    lineToRelative(2.21f, -4.98f)
                    curveToRelative(0.04f, 0.01f, 0.08f, 0.02f, 0.12f, 0.02f)
                    curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                    reflectiveCurveToRelative(-2.24f, -5f, -5f, -5f)
                    close()
                    moveTo(12f, 2f)
                    curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                    curveToRelative(0f, 2.85f, 1.2f, 5.41f, 3.11f, 7.24f)
                    lineToRelative(1.44f, -1.44f)
                    curveTo(4.99f, 16.32f, 4f, 14.28f, 4f, 12f)
                    curveToRelative(0f, -4.41f, 3.59f, -8f, 8f, -8f)
                    reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
                    curveToRelative(0f, 1.16f, -0.26f, 2.25f, -0.71f, 3.24f)
                    lineToRelative(1.5f, 1.5f)
                    curveTo(21.46f, 15.35f, 22f, 13.74f, 22f, 12f)
                    curveToRelative(0f, -5.52f, -4.48f, -10f, -10f, -10f)
                    close()
                }
            }.build()
    }

    val NetworkPing: ImageVector by lazy {
        NetworkCheck
    }

    val Cloud: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Cloud",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(19.35f, 10.04f)
                    curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                    curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
                    curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
                    curveToRelative(0f, 3.31f, 2.69f, 6f, 6f, 6f)
                    horizontalLineToRelative(13f)
                    curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                    curveToRelative(0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
                    close()
                }
            }.build()
    }

    val ViewInAr: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "ViewInAr",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(18.99f, 4.47f)
                    lineTo(12f, 0.44f)
                    lineTo(5.01f, 4.47f)
                    lineTo(12f, 8.5f)
                    lineToRelative(6.99f, -4.03f)
                    close()
                    moveTo(4f, 6.2f)
                    verticalLineToRelative(7.61f)
                    lineToRelative(7f, 4.03f)
                    verticalLineTo(10.23f)
                    lineTo(4f, 6.2f)
                    close()
                    moveTo(20f, 6.2f)
                    lineToRelative(-7f, 4.03f)
                    verticalLineToRelative(7.61f)
                    lineToRelative(7f, -4.03f)
                    verticalLineTo(6.2f)
                    close()
                }
            }.build()
    }

    val Layers: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Layers",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(11.99f, 18.54f)
                    lineToRelative(-7.37f, -5.73f)
                    lineTo(3f, 14.07f)
                    lineToRelative(9f, 7f)
                    lineToRelative(9f, -7f)
                    lineToRelative(-1.63f, -1.27f)
                    lineToRelative(-7.38f, 5.74f)
                    close()
                    moveTo(12f, 16f)
                    lineToRelative(7.36f, -5.73f)
                    lineTo(21f, 9f)
                    lineToRelative(-9f, -7f)
                    lineToRelative(-9f, 7f)
                    lineToRelative(1.63f, 1.27f)
                    lineTo(12f, 16f)
                    close()
                }
            }.build()
    }

    val Wifi: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Wifi",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(1f, 9f)
                    lineToRelative(2f, 2f)
                    curveToRelative(4.97f, -4.97f, 13.03f, -4.97f, 18f, 0f)
                    lineToRelative(2f, -2f)
                    curveTo(16.93f, 2.93f, 7.08f, 2.93f, 1f, 9f)
                    close()
                    moveTo(5f, 13f)
                    lineToRelative(2f, 2f)
                    curveToRelative(2.76f, -2.76f, 7.24f, -2.76f, 10f, 0f)
                    lineToRelative(2f, -2f)
                    curveTo(15.14f, 9.14f, 8.87f, 9.14f, 5f, 13f)
                    close()
                    moveTo(9f, 17f)
                    lineToRelative(3f, 3f)
                    lineToRelative(3f, -3f)
                    curveToRelative(-1.65f, -1.66f, -4.34f, -1.66f, -6f, 0f)
                    close()
                }
            }.build()
    }

    val Tune: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Tune",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(3f, 17f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(6f)
                    verticalLineToRelative(-2f)
                    horizontalLineTo(3f)
                    close()
                    moveTo(3f, 5f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(10f)
                    verticalLineTo(5f)
                    horizontalLineTo(3f)
                    close()
                    moveTo(13f, 21f)
                    verticalLineToRelative(-2f)
                    horizontalLineToRelative(8f)
                    verticalLineToRelative(-2f)
                    horizontalLineToRelative(-8f)
                    verticalLineToRelative(-2f)
                    horizontalLineToRelative(-2f)
                    verticalLineToRelative(6f)
                    horizontalLineToRelative(2f)
                    close()
                    moveTo(7f, 9f)
                    verticalLineToRelative(2f)
                    horizontalLineTo(3f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(4f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(2f)
                    verticalLineTo(9f)
                    horizontalLineTo(7f)
                    close()
                    moveTo(21f, 13f)
                    verticalLineToRelative(-2f)
                    horizontalLineTo(11f)
                    verticalLineToRelative(2f)
                    horizontalLineToRelative(10f)
                    close()
                    moveTo(17f, 9f)
                    horizontalLineToRelative(2f)
                    verticalLineTo(7f)
                    horizontalLineToRelative(2f)
                    verticalLineTo(5f)
                    horizontalLineToRelative(-2f)
                    verticalLineTo(3f)
                    horizontalLineToRelative(-2f)
                    verticalLineToRelative(6f)
                    close()
                }
            }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Settings",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(19.14f, 12.94f)
                    curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
                    curveToRelative(0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
                    lineToRelative(2.03f, -1.58f)
                    curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
                    lineToRelative(-1.92f, -3.32f)
                    curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
                    lineToRelative(-2.39f, 0.96f)
                    curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
                    lineTo(14.4f, 2.81f)
                    curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
                    horizontalLineToRelative(-3.84f)
                    curveToRelative(-0.24f, 0f, -0.43f, 0.17f, -0.47f, 0.41f)
                    lineToRelative(-0.36f, 2.54f)
                    curveToRelative(-0.59f, 0.24f, -1.13f, 0.57f, -1.62f, 0.94f)
                    lineToRelative(-2.39f, -0.96f)
                    curveToRelative(-0.22f, -0.08f, -0.47f, 0f, -0.59f, 0.22f)
                    lineTo(2.74f, 8.87f)
                    curveToRelative(-0.12f, 0.21f, -0.08f, 0.47f, 0.12f, 0.61f)
                    lineToRelative(2.03f, 1.58f)
                    curveToRelative(-0.05f, 0.3f, -0.09f, 0.63f, -0.09f, 0.94f)
                    reflectiveCurveToRelative(0.02f, 0.64f, 0.07f, 0.94f)
                    lineToRelative(-2.03f, 1.58f)
                    curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
                    lineToRelative(1.92f, 3.32f)
                    curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
                    lineToRelative(2.39f, -0.96f)
                    curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
                    lineToRelative(0.36f, 2.54f)
                    curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
                    horizontalLineToRelative(3.84f)
                    curveToRelative(0.24f, 0f, 0.44f, -0.17f, 0.47f, -0.41f)
                    lineToRelative(0.36f, -2.54f)
                    curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
                    lineToRelative(2.39f, 0.96f)
                    curveToRelative(0.22f, 0.08f, 0.47f, 0f, 0.59f, -0.22f)
                    lineToRelative(1.92f, -3.32f)
                    curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
                    lineToRelative(-2.01f, -1.58f)
                    close()
                    moveTo(12f, 15.6f)
                    curveToRelative(-1.98f, 0f, -3.6f, -1.62f, -3.6f, -3.6f)
                    reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
                    reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
                    reflectiveCurveToRelative(-1.62f, 3.6f, -3.6f, 3.6f)
                    close()
                }
            }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Check",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(9f, 16.17f)
                    lineTo(4.83f, 12f)
                    lineToRelative(-1.42f, 1.41f)
                    lineTo(9f, 19f)
                    lineTo(21f, 7f)
                    lineToRelative(-1.41f, -1.41f)
                    close()
                }
            }.build()
    }

    val Router: ImageVector by lazy {
        Lan
    }

    val Security: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Security",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12f, 1f)
                    lineTo(3f, 5f)
                    verticalLineToRelative(6f)
                    curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
                    curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f)
                    verticalLineTo(5f)
                    lineToRelative(-9f, -4f)
                    close()
                }
            }.build()
    }

    val CheckCircle: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "CheckCircle",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(12f, 2f)
                    curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                    reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                    reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                    reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                    close()
                    moveTo(10f, 17f)
                    lineToRelative(-5f, -5f)
                    lineToRelative(1.41f, -1.41f)
                    lineTo(10f, 14.17f)
                    lineToRelative(7.59f, -7.59f)
                    lineTo(19f, 8f)
                    lineToRelative(-9f, 9f)
                    close()
                }
            }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Close",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(19f, 6.41f)
                    lineTo(17.59f, 5f)
                    lineTo(12f, 10.59f)
                    lineTo(6.41f, 5f)
                    lineTo(5f, 6.41f)
                    lineTo(10.59f, 12f)
                    lineTo(5f, 17.59f)
                    lineTo(6.41f, 19f)
                    lineTo(12f, 13.41f)
                    lineTo(17.59f, 19f)
                    lineTo(19f, 17.59f)
                    lineTo(13.41f, 12f)
                    close()
                }
            }.build()
    }
}
