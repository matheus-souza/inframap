package com.inframap.frontend.ui.discovery

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.fakes.FakeDiscoveryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CreateDiscoverySourceViewModelTest {
    private val createdSource =
        DiscoverySource(
            id = "src-1",
            name = "ICMP Scanner",
            sourceType = "icmp",
            enabled = true,
            lastStatus = "idle",
        )

    private fun makeVm(
        repo: FakeDiscoveryRepository =
            FakeDiscoveryRepository(
                createSourceResult = ApiResult.Success(createdSource, requestId = ""),
            ),
        scope: CoroutineScope? = null,
    ) = CreateDiscoverySourceViewModel(CreateDiscoverySourceUseCase(repo), scope = scope)

    @Test
    fun validationFailsOnEmptyNameAndType() =
        runTest {
            val vm = makeVm(scope = this)

            assertFalse(vm.validate())
            val errors = vm.state.value.validationErrors
            assertTrue(errors.containsKey("name"))
            assertTrue(errors.containsKey("type"))
            vm.clear()
        }

    @Test
    fun validationPassesWithValidFields() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.onSourceTypeChanged("icmp")

            assertTrue(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .isEmpty(),
            )
            vm.clear()
        }

    @Test
    fun validationFailsOnInvalidCidr() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.onSourceTypeChanged("icmp")
            vm.onConfigCidrChanged("not-a-cidr")

            assertFalse(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("cidr"),
            )
            vm.clear()
        }

    @Test
    fun validationAcceptsValidCidr() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.onSourceTypeChanged("icmp")
            vm.onConfigCidrChanged("192.168.1.0/24")

            assertTrue(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .isEmpty(),
            )
            vm.clear()
        }

    @Test
    fun fieldChangesUpdateState() =
        runTest {
            val vm = makeVm(scope = this)

            vm.onNameChanged("Test Source")
            assertEquals("Test Source", vm.state.value.name)

            vm.onSourceTypeChanged("snmp")
            assertEquals("snmp", vm.state.value.sourceType)

            vm.onScheduleCronChanged("0 */6 * * *")
            assertEquals("0 */6 * * *", vm.state.value.scheduleCron)

            vm.onConfigCidrChanged("10.0.0.0/8")
            assertEquals("10.0.0.0/8", vm.state.value.configCidr)

            vm.onEnabledChanged(false)
            assertFalse(vm.state.value.enabled)

            vm.clear()
        }

    @Test
    fun fieldChangeClearsRelatedValidationError() =
        runTest {
            val vm = makeVm(scope = this)

            vm.validate()
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("name"),
            )

            vm.onNameChanged("Fixed")
            assertFalse(
                vm.state.value.validationErrors
                    .containsKey("name"),
            )

            vm.clear()
        }

    @Test
    fun createSourceWorkflowCompletesSuccessfully() =
        runTest {
            var onSuccessCalled = false
            val vm = makeVm(scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.onSourceTypeChanged("icmp")
            vm.onConfigCidrChanged("192.168.1.0/24")
            vm.onScheduleCronChanged("0 */6 * * *")

            vm.state.test {
                skipItems(1)
                vm.createSource { onSuccessCalled = true }
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertTrue(state.isSuccess)
                assertFalse(state.isSubmitting)
                assertNull(state.errorMessage)
                assertTrue(onSuccessCalled)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun createSourceIgnoresReentrantCallsWhenSubmitting() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onNameChanged("ICMP Scanner")
            vm.onSourceTypeChanged("icmp")

            vm.createSource()
            assertTrue(vm.state.value.isSubmitting)

            vm.createSource()
            assertTrue(vm.state.value.isSubmitting)

            advanceUntilIdle()
            vm.clear()
        }

    @Test
    fun createSourceHandlesApiError() =
        runTest {
            val repo =
                FakeDiscoveryRepository(
                    createSourceResult =
                        ApiResult.Error(
                            code = "DUPLICATE_NAME",
                            message = "Source name already exists",
                            requestId = "",
                            httpStatus = 409,
                        ),
                )
            val vm = makeVm(repo = repo, scope = this)
            vm.onNameChanged("ICMP Scanner")
            vm.onSourceTypeChanged("icmp")

            vm.state.test {
                skipItems(1)
                vm.createSource()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertFalse(state.isSubmitting)
                assertFalse(state.isSuccess)
                assertEquals("Source name already exists", state.errorMessage?.asStringAsync())
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun createSourceHandlesNetworkError() =
        runTest {
            val repo =
                FakeDiscoveryRepository(
                    createSourceResult = ApiResult.NetworkError(RuntimeException("timeout")),
                )
            val vm = makeVm(repo = repo, scope = this)
            vm.onNameChanged("ICMP Scanner")
            vm.onSourceTypeChanged("icmp")

            vm.state.test {
                skipItems(1)
                vm.createSource()
                advanceUntilIdle()

                val state = expectMostRecentItem()
                assertFalse(state.isSubmitting)
                assertFalse(state.isSuccess)
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun createSourceSkipsWhenValidationFails() =
        runTest {
            val vm = makeVm(scope = this)

            vm.createSource()
            assertFalse(vm.state.value.isSubmitting)
            assertTrue(
                vm.state.value.validationErrors
                    .isNotEmpty(),
            )

            vm.clear()
        }

    @Test
    fun createSourceWithBlankNameFailsAtUseCaseLevel() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onNameChanged("  ")
            vm.onSourceTypeChanged("icmp")

            assertFalse(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("name"),
            )
            vm.clear()
        }
}
