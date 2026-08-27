package com.inframap.frontend.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val InfraMapShapeNone = RoundedCornerShape(0.dp)
val InfraMapShapeExtraSmall = RoundedCornerShape(4.dp)
val InfraMapShapeSmall = RoundedCornerShape(8.dp)
val InfraMapShapeMedium = RoundedCornerShape(12.dp)
val InfraMapShapeLarge = RoundedCornerShape(16.dp)
val InfraMapShapeExtraLarge = RoundedCornerShape(28.dp)

val InfraMapShapes =
    Shapes(
        extraSmall = InfraMapShapeExtraSmall,
        small = InfraMapShapeSmall,
        medium = InfraMapShapeMedium,
        large = InfraMapShapeLarge,
        extraLarge = InfraMapShapeExtraLarge,
    )
