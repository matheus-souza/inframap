package com.inframap.frontend.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@OptIn(ExperimentalTestApi::class)
class LoadingSkeletonTest {
    @Test
    fun skeletonRendersWithoutCrashing() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapLoadingSkeleton(lines = 3)
                }
            }
            waitForIdle()
        }

    @Test
    fun skeletonRendersWithCustomLineCount() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapLoadingSkeleton(lines = 5)
                }
            }
            waitForIdle()
        }

    @Test
    fun skeletonRendersWithSingleLine() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapLoadingSkeleton(lines = 1)
                }
            }
            waitForIdle()
        }

    @Test
    fun dashboardSkeletonRendersWithoutCrashing() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    DashboardLoadingSkeleton()
                }
            }
            waitForIdle()
        }

    @Test
    fun tableSkeletonRendersWithoutCrashing() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTableSkeleton(rows = 3, columns = 4)
                }
            }
            waitForIdle()
        }

    @Test
    fun legacyTableSkeletonRendersWithoutCrashing() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    TableLoadingSkeleton(rows = 2, columns = 3)
                }
            }
            waitForIdle()
        }

    @Test
    fun listSkeletonRendersWithoutCrashing() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapListSkeleton(items = 4)
                }
            }
            waitForIdle()
        }

    @Test
    fun legacyListSkeletonRendersWithoutCrashing() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    ListLoadingSkeleton(items = 3)
                }
            }
            waitForIdle()
        }

    @Test
    fun skeletonThrowsOnInvalidLines() {
        assertThrows<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    InfraMapLoadingSkeleton(lines = 0)
                }
            }
        }
    }

    @Test
    fun skeletonThrowsOnInvalidLineHeight() {
        assertThrows<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    InfraMapLoadingSkeleton(lineHeight = (-4).dp)
                }
            }
        }
    }

    @Test
    fun skeletonThrowsOnNegativeSpacing() {
        assertThrows<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    InfraMapLoadingSkeleton(spacing = (-1).dp)
                }
            }
        }
    }

    @Test
    fun tableSkeletonThrowsOnInvalidRows() {
        assertThrows<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    InfraMapTableSkeleton(rows = 0)
                }
            }
        }
    }

    @Test
    fun tableSkeletonThrowsOnInvalidColumns() {
        assertThrows<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    InfraMapTableSkeleton(columns = 0)
                }
            }
        }
    }

    @Test
    fun listSkeletonThrowsOnInvalidItems() {
        assertThrows<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    InfraMapListSkeleton(items = 0)
                }
            }
        }
    }

    @Test
    fun listSkeletonThrowsOnInvalidItemHeight() {
        assertThrows<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    InfraMapListSkeleton(itemHeight = (-10).dp)
                }
            }
        }
    }

    @Test
    fun tableSkeletonThrowsOnInvalidRowHeight() {
        assertThrows<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    InfraMapTableSkeleton(rowHeight = (-10).dp)
                }
            }
        }
    }

    @Test
    fun tableSkeletonThrowsOnNegativeSpacing() {
        assertThrows<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    InfraMapTableSkeleton(spacing = (-1).dp)
                }
            }
        }
    }

    @Test
    fun listSkeletonThrowsOnNegativeSpacing() {
        assertThrows<IllegalArgumentException> {
            runComposeUiTest {
                setContent {
                    InfraMapListSkeleton(spacing = (-1).dp)
                }
            }
        }
    }
}
