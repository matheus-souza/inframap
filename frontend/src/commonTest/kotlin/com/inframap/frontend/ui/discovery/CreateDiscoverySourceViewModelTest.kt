package com.inframap.frontend.ui.discovery

import app.cash.turbine.test
import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.model.SubnetSummary
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.subnet.ListSubnetsUseCase
import com.inframap.frontend.fakes.FakeDiscoveryRepository
import com.inframap.frontend.fakes.FakeSubnetRepository
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
            sourceType = "icmp_sweep",
            enabled = true,
            lastStatus = "idle",
        )

    private val testSubnet =
        Subnet(
            id = "sub-10",
            name = "Office LAN",
            cidr = "10.10.0.0/24",
            discoveryEnabled = true,
        )

    private fun makeVm(
        discoveryRepo: FakeDiscoveryRepository =
            FakeDiscoveryRepository(
                createSourceResult = ApiResult.Success(createdSource, requestId = ""),
            ),
        subnetRepo: FakeSubnetRepository =
            FakeSubnetRepository(
                getSubnetsResult =
                    ApiResult.Success(
                        PaginatedList(items = listOf(testSubnet), total = 1L, page = 1, perPage = 50),
                        requestId = "",
                    ),
            ),
        scope: CoroutineScope? = null,
    ) = CreateDiscoverySourceViewModel(
        createSourceUseCase = CreateDiscoverySourceUseCase(discoveryRepo),
        listSubnetsUseCase = ListSubnetsUseCase(subnetRepo),
        scope = scope,
    )

    @Test
    fun subnetsLoadedSuccessfullyOnInit() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.isLoadingSubnets)
            assertEquals(1, state.subnets.size)
            assertEquals("Office LAN", state.subnets[0].name)
            assertEquals("10.10.0.0/24", state.subnets[0].cidr)
            vm.clear()
        }

    @Test
    fun subnetsLoadFailureHandledGracefully() =
        runTest {
            val subnetRepo =
                FakeSubnetRepository(
                    getSubnetsResult = ApiResult.NetworkError(RuntimeException("Failed to load")),
                )
            val vm = makeVm(subnetRepo = subnetRepo, scope = this)
            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.isLoadingSubnets)
            assertTrue(state.subnets.isEmpty())
            vm.clear()
        }

    @Test
    fun onSubnetSelectedPrefillsCidrAndDefaultNameWhenNameIsBlank() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            val summary = SubnetSummary(id = "sub-1", name = "Production", cidr = "192.168.10.0/24")
            vm.onSubnetSelected(summary)

            val state = vm.state.value
            assertEquals("Scan Production", state.name)
            assertEquals("192.168.10.0/24", state.configCidr)
            assertFalse(state.validationErrors.containsKey("cidr"))
            assertFalse(state.validationErrors.containsKey("name"))
            vm.clear()
        }

    @Test
    fun onSubnetSelectedPreservesExistingCustomName() =
        runTest {
            val vm = makeVm(scope = this)
            advanceUntilIdle()

            vm.onNameChanged("Custom Dedicated Scanner")
            val summary = SubnetSummary(id = "sub-1", name = "Production", cidr = "192.168.10.0/24")
            vm.onSubnetSelected(summary)

            val state = vm.state.value
            assertEquals("Custom Dedicated Scanner", state.name)
            assertEquals("192.168.10.0/24", state.configCidr)
            vm.clear()
        }

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
            vm.onSourceTypeChanged("icmp_sweep")

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
            vm.onSourceTypeChanged("icmp_sweep")
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
            vm.onSourceTypeChanged("icmp_sweep")
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

            vm.onSourceTypeChanged("arp_sweep")
            assertEquals("arp_sweep", vm.state.value.sourceType)

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
            val discoveryRepo = FakeDiscoveryRepository()
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)

            vm.onNameChanged("ICMP Scanner")
            vm.onSourceTypeChanged("icmp_sweep")
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
                assertEquals("0 */6 * * *", discoveryRepo.lastCreateSourceRequest?.scheduleCron)
                assertEquals("192.168.1.0/24", discoveryRepo.lastCreateSourceRequest?.config?.get("cidr"))
                cancelAndIgnoreRemainingEvents()
            }
            vm.clear()
        }

    @Test
    fun createSourceWith15MinPresetScheduleSubmitsCorrectCron() =
        runTest {
            val discoveryRepo = FakeDiscoveryRepository()
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)

            vm.onNameChanged("Fast Scanner")
            vm.onSourceTypeChanged("arp_sweep")
            vm.onScheduleCronChanged("*/15 * * * *")

            vm.createSource()
            advanceUntilIdle()

            assertEquals("*/15 * * * *", discoveryRepo.lastCreateSourceRequest?.scheduleCron)
            vm.clear()
        }

    @Test
    fun createSourceWithCustomCronScheduleSubmitsCorrectCron() =
        runTest {
            val discoveryRepo = FakeDiscoveryRepository()
            val vm = makeVm(discoveryRepo = discoveryRepo, scope = this)

            vm.onNameChanged("Custom Cron Scanner")
            vm.onSourceTypeChanged("docker")
            vm.onScheduleCronChanged("30 3 * * 1-5")

            vm.createSource()
            advanceUntilIdle()

            assertEquals("30 3 * * 1-5", discoveryRepo.lastCreateSourceRequest?.scheduleCron)
            vm.clear()
        }

    @Test
    fun createSourceIgnoresReentrantCallsWhenSubmitting() =
        runTest {
            val vm = makeVm(scope = this)
            vm.onNameChanged("ICMP Scanner")
            vm.onSourceTypeChanged("icmp_sweep")

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
            val vm = makeVm(discoveryRepo = repo, scope = this)
            vm.onNameChanged("ICMP Scanner")
            vm.onSourceTypeChanged("icmp_sweep")

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
            val vm = makeVm(discoveryRepo = repo, scope = this)
            vm.onNameChanged("ICMP Scanner")
            vm.onSourceTypeChanged("icmp_sweep")

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
            vm.onSourceTypeChanged("icmp_sweep")

            assertFalse(vm.validate())
            assertTrue(
                vm.state.value.validationErrors
                    .containsKey("name"),
            )
            vm.clear()
        }
}
