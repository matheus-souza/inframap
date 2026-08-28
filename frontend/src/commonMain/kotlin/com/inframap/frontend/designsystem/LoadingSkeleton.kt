package com.inframap.frontend.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultSkeletonLineHeight = 16.dp
private val DefaultSkeletonSpacing = 12.dp
private val DefaultSkeletonCardRadius = 12.dp
private val DefaultSkeletonCardWidth = 260.dp
private val DefaultSkeletonCardHeight = 124.dp
private val DefaultSkeletonHeroHeight = 130.dp
private val DefaultTableRowHeight = 40.dp
private val DefaultTableSpacing = 8.dp
private val DefaultListItemHeight = 64.dp
private const val LAST_LINE_WIDTH_FRACTION = 0.6f

@Composable
fun InfraMapLoadingSkeleton(
    modifier: Modifier = Modifier,
    lines: Int = 3,
    lineHeight: Dp = DefaultSkeletonLineHeight,
    spacing: Dp = DefaultSkeletonSpacing,
    shape: Shape = RoundedCornerShape(4.dp),
) {
    require(lines > 0) { "lines must be positive" }
    require(lineHeight.value > 0f) { "lineHeight must be positive" }
    require(spacing.value >= 0f) { "spacing must be non-negative" }

    Column(modifier = modifier) {
        repeat(lines) { index ->
            val widthFraction = if (index == lines - 1 && lines > 1) LAST_LINE_WIDTH_FRACTION else 1.0f
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(widthFraction)
                        .height(lineHeight)
                        .m3Shimmer(shape = shape),
            )
            if (index < lines - 1) {
                Spacer(modifier = Modifier.height(spacing))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Quick Setup / Hero banner placeholder
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(DefaultSkeletonHeroHeight)
                    .m3Shimmer(shape = RoundedCornerShape(DefaultSkeletonCardRadius)),
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 4 KPI Metric Cards placeholders in FlowRow
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            maxItemsInEachRow = 4,
        ) {
            repeat(4) {
                Box(
                    modifier =
                        Modifier
                            .width(DefaultSkeletonCardWidth)
                            .height(DefaultSkeletonCardHeight)
                            .m3Shimmer(shape = RoundedCornerShape(DefaultSkeletonCardRadius)),
                )
            }
        }
    }
}

@Composable
fun InfraMapTableSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
    columns: Int = 4,
    rowHeight: Dp = DefaultTableRowHeight,
    spacing: Dp = DefaultTableSpacing,
) {
    require(rows > 0) { "rows must be positive" }
    require(columns > 0) { "columns must be positive" }
    require(rowHeight.value > 0f) { "rowHeight must be positive" }
    require(spacing.value >= 0f) { "spacing cannot be negative" }

    Column(modifier = modifier.fillMaxWidth()) {
        // Table Header Skeleton
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(columns) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(DefaultSkeletonLineHeight)
                            .m3Shimmer(shape = RoundedCornerShape(4.dp)),
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing))

        // Table Rows Skeleton
        repeat(rows) { index ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(columns) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(rowHeight)
                                .m3Shimmer(shape = RoundedCornerShape(6.dp)),
                    )
                }
            }
            if (index < rows - 1) {
                Spacer(modifier = Modifier.height(spacing))
            }
        }
    }
}

@Composable
fun TableLoadingSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
    columns: Int = 4,
    rowHeight: Dp = DefaultTableRowHeight,
    spacing: Dp = DefaultTableSpacing,
) = InfraMapTableSkeleton(
    modifier = modifier,
    rows = rows,
    columns = columns,
    rowHeight = rowHeight,
    spacing = spacing,
)

@Composable
fun InfraMapListSkeleton(
    modifier: Modifier = Modifier,
    items: Int = 4,
    itemHeight: Dp = DefaultListItemHeight,
    spacing: Dp = DefaultSkeletonSpacing,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    require(items > 0) { "items must be positive" }
    require(itemHeight.value > 0f) { "itemHeight must be positive" }
    require(spacing.value >= 0f) { "spacing cannot be negative" }

    Column(modifier = modifier.fillMaxWidth()) {
        repeat(items) { index ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .m3Shimmer(shape = shape),
            )
            if (index < items - 1) {
                Spacer(modifier = Modifier.height(spacing))
            }
        }
    }
}

@Composable
fun ListLoadingSkeleton(
    modifier: Modifier = Modifier,
    items: Int = 4,
    itemHeight: Dp = DefaultListItemHeight,
    spacing: Dp = DefaultSkeletonSpacing,
    shape: Shape = RoundedCornerShape(8.dp),
) = InfraMapListSkeleton(
    modifier = modifier,
    items = items,
    itemHeight = itemHeight,
    spacing = spacing,
    shape = shape,
)
