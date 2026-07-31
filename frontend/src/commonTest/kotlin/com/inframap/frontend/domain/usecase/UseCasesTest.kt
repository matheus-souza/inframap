package com.inframap.frontend.domain.usecase

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.data.dto.CreateDeviceRequest
import com.inframap.frontend.data.dto.CreateSubnetRequest
import com.inframap.frontend.data.dto.LoginRequest
import com.inframap.frontend.data.dto.OnboardRequest
import com.inframap.frontend.data.dto.UpdateDeviceRequest
import com.inframap.frontend.domain.model.Device
import com.inframap.frontend.domain.model.DiscoverySource
import com.inframap.frontend.domain.model.Health
import com.inframap.frontend.domain.model.LoginResult
import com.inframap.frontend.domain.model.OnboardResult
import com.inframap.frontend.domain.model.PaginatedList
import com.inframap.frontend.domain.model.SetupStatus
import com.inframap.frontend.domain.model.StagingDevice
import com.inframap.frontend.domain.model.Subnet
import com.inframap.frontend.domain.model.User
import com.inframap.frontend.domain.repository.AuthRepository
import com.inframap.frontend.domain.repository.DashboardRepository
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.repository.StagingRepository
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.usecase.auth.GetCurrentUserUseCase
import com.inframap.frontend.domain.usecase.auth.GetSetupStatusUseCase
import com.inframap.frontend.domain.usecase.auth.LoginUseCase
import com.inframap.frontend.domain.usecase.auth.OnboardUseCase
import com.inframap.frontend.domain.usecase.dashboard.GetDeviceSummaryUseCase
import com.inframap.frontend.domain.usecase.dashboard.GetDiscoverySourcesUseCase
import com.inframap.frontend.domain.usecase.dashboard.GetHealthUseCase
import com.inframap.frontend.domain.usecase.dashboard.GetStagingSummaryUseCase
import com.inframap.frontend.domain.usecase.device.CreateDeviceUseCase
import com.inframap.frontend.domain.usecase.device.DeleteDeviceUseCase
import com.inframap.frontend.domain.usecase.device.GetDeviceByIdUseCase
import com.inframap.frontend.domain.usecase.device.GetDevicesUseCase
import com.inframap.frontend.domain.usecase.device.UpdateDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.ApproveDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.DismissDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.GetStagingDevicesUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

private class FakeFullRepository :
    DeviceRepository,
    StagingRepository,
    SubnetRepository,
    AuthRepository,
    DashboardRepository {
    override suspend fun getDevices(
        page: Int,
        perPage: Int,
        search: String,
    ) = ApiResult.Success(PaginatedList(emptyList<Device>(), 0, page, perPage), "req-1")

    override suspend fun getDeviceById(id: String) =
        ApiResult.Success(Device(id = id, hostname = "dev-1", deviceType = "server", status = "active"), "req-1")

    override suspend fun createDevice(request: CreateDeviceRequest) =
        ApiResult.Success(Device(id = "d1", hostname = request.hostname, deviceType = request.deviceType, status = "active"), "req-1")

    override suspend fun updateDevice(
        id: String,
        request: UpdateDeviceRequest,
    ) = ApiResult.Success(Device(id = id, hostname = request.hostname ?: "dev", deviceType = "server", status = "active"), "req-1")

    override suspend fun deleteDevice(id: String) = ApiResult.Success(Unit, "req-1")

    override suspend fun getStagingDevices(
        page: Int,
        perPage: Int,
    ) = ApiResult.Success(PaginatedList(emptyList<StagingDevice>(), 0, page, perPage), "req-1")

    override suspend fun approveDevice(id: String) =
        ApiResult.Success(Device(id = id, hostname = "app-1", deviceType = "server", status = "active"), "req-1")

    override suspend fun dismissDevice(id: String) = ApiResult.Success(Unit, "req-1")

    override suspend fun getSubnets() = ApiResult.Success(PaginatedList(emptyList<Subnet>(), 0), "req-1")

    override suspend fun createSubnet(request: CreateSubnetRequest) =
        ApiResult.Success(Subnet(id = "sub-1", name = request.name, cidr = request.cidr), "req-1")

    override suspend fun getSetupStatus() = ApiResult.Success(SetupStatus(true, "inst-1"), "req-1")

    override suspend fun login(request: LoginRequest) = ApiResult.Success(LoginResult("tok", "u1", request.username), "req-1")

    override suspend fun onboard(request: OnboardRequest) = ApiResult.Success(OnboardResult(true, "inst-1", "u1"), "req-1")

    override suspend fun getCurrentUser() = ApiResult.Success(User("u1", "admin"), "req-1")

    override suspend fun getHealth() = ApiResult.Success(Health("ok", "1.0.0"), "req-1")

    override suspend fun getStagingSummary() = ApiResult.Success(PaginatedList(emptyList<StagingDevice>(), 0, 1, 5), "req-1")

    override suspend fun getDiscoverySources() = ApiResult.Success(emptyList<DiscoverySource>(), "req-1")
}

class UseCasesTest {
    private val repo = FakeFullRepository()

    @Test
    fun testDeviceUseCases() =
        runTest {
            assertIs<ApiResult.Success<*>>(GetDevicesUseCase(repo)(GetDevicesUseCase.Params(1, 10, "")))
            assertIs<ApiResult.Success<*>>(GetDeviceByIdUseCase(repo)("d1"))
            assertIs<ApiResult.Success<*>>(CreateDeviceUseCase(repo)(CreateDeviceRequest("srv", deviceType = "server")))
            assertIs<ApiResult.Success<*>>(UpdateDeviceUseCase(repo)(UpdateDeviceUseCase.Params("d1", UpdateDeviceRequest())))
            assertIs<ApiResult.Success<*>>(DeleteDeviceUseCase(repo)("d1"))
        }

    @Test
    fun testStagingUseCases() =
        runTest {
            assertIs<ApiResult.Success<*>>(GetStagingDevicesUseCase(repo)(GetStagingDevicesUseCase.Params(1, 10)))
            assertIs<ApiResult.Success<*>>(ApproveDeviceUseCase(repo)("stg-1"))
            assertIs<ApiResult.Success<*>>(DismissDeviceUseCase(repo)("stg-1"))
        }

    @Test
    fun testSubnetUseCases() =
        runTest {
            assertIs<ApiResult.Success<*>>(GetSubnetsUseCase(repo)())
        }

    @Test
    fun testAuthUseCases() =
        runTest {
            assertIs<ApiResult.Success<*>>(GetSetupStatusUseCase(repo)())
            assertIs<ApiResult.Success<*>>(LoginUseCase(repo)(LoginRequest("admin", "pass")))
            assertIs<ApiResult.Success<*>>(OnboardUseCase(repo)(OnboardRequest("admin", "a@t.com", "pass", "Admin")))
            assertIs<ApiResult.Success<*>>(GetCurrentUserUseCase(repo)())
        }

    @Test
    fun testDashboardUseCases() =
        runTest {
            assertIs<ApiResult.Success<*>>(GetHealthUseCase(repo)())
            assertIs<ApiResult.Success<*>>(GetDeviceSummaryUseCase(repo)())
            assertIs<ApiResult.Success<*>>(GetStagingSummaryUseCase(repo)())
            assertIs<ApiResult.Success<*>>(GetDiscoverySourcesUseCase(repo)())
        }
}
