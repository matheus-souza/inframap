package com.inframap.frontend.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class TableTest {
    private val columns =
        listOf(
            TableColumn(header = "Name"),
            TableColumn(header = "Status"),
        )
    private val items = listOf("Server-01", "Server-02")

    @Test
    fun tableRendersHeaders() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTable(
                        columns = columns,
                        items = items,
                    ) { colIndex, item ->
                        Text(if (colIndex == 0) item else "Active")
                    }
                }
            }
            onNodeWithText("Name").assertIsDisplayed()
            onNodeWithText("Status").assertIsDisplayed()
        }

    @Test
    fun tableRendersItems() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTable(
                        columns = columns,
                        items = items,
                    ) { colIndex, item ->
                        Text(if (colIndex == 0) item else "Active")
                    }
                }
            }
            onNodeWithText("Server-01").assertIsDisplayed()
            onNodeWithText("Server-02").assertIsDisplayed()
        }

    @Test
    fun tableRowClickTriggersCallback() =
        runComposeUiTest {
            var clickedItem = ""
            setContent {
                InfraMapTheme {
                    InfraMapTable(
                        columns = columns,
                        items = items,
                        onRowClick = { clickedItem = it },
                    ) { colIndex, item ->
                        Text(if (colIndex == 0) item else "Active")
                    }
                }
            }
            onNodeWithText("Server-01").performClick()
            assertEquals("Server-01", clickedItem)
        }

    @Test
    fun paginationRendersPageInfo() =
        runComposeUiTest {
            setContent {
                InfraMapTheme {
                    InfraMapTablePagination(
                        currentPage = 2,
                        totalPages = 5,
                        onPageChange = {},
                    )
                }
            }
            onNodeWithText("Page 2 of 5").assertIsDisplayed()
            onNodeWithText("Previous").assertIsDisplayed()
            onNodeWithText("Next").assertIsDisplayed()
        }

    @Test
    fun paginationNextTriggersCallback() =
        runComposeUiTest {
            var newPage = 0
            setContent {
                InfraMapTheme {
                    InfraMapTablePagination(
                        currentPage = 2,
                        totalPages = 5,
                        onPageChange = { newPage = it },
                    )
                }
            }
            onNodeWithText("Next").performClick()
            assertEquals(3, newPage)
        }

    @Test
    fun paginationPreviousTriggersCallback() =
        runComposeUiTest {
            var newPage = 0
            setContent {
                InfraMapTheme {
                    InfraMapTablePagination(
                        currentPage = 3,
                        totalPages = 5,
                        onPageChange = { newPage = it },
                    )
                }
            }
            onNodeWithText("Previous").performClick()
            assertEquals(2, newPage)
        }
}
