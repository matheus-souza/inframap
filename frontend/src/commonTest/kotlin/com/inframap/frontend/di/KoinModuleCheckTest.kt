package com.inframap.frontend.di

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.sse.SSEClient
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
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import com.inframap.frontend.ui.dashboard.DashboardViewModel
import com.inframap.frontend.ui.devices.CreateDeviceViewModel
import com.inframap.frontend.ui.devices.DeviceDetailViewModel
import com.inframap.frontend.ui.devices.DeviceListViewModel
import com.inframap.frontend.ui.devices.EditDeviceViewModel
import com.inframap.frontend.ui.login.LoginViewModel
import com.inframap.frontend.ui.onboarding.OnboardingViewModel
import com.inframap.frontend.ui.splash.SplashViewModel
import com.inframap.frontend.ui.staging.StagingViewModel
import com.inframap.frontend.ui.subnets.CreateSubnetViewModel
import com.inframap.frontend.ui.subnets.SubnetsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

class KoinModuleCheckTest {
    @Test
    fun checkDataAndDomainModulesAreSatisfied() {
        val app =
            koinApplication {
                modules(appModules)
            }

        val koin = app.koin

        assertNotNull(koin.get<ApiClient>())
        assertNotNull(koin.get<DeviceRepository>())
        assertNotNull(koin.get<StagingRepository>())
        assertNotNull(koin.get<SubnetRepository>())
        assertNotNull(koin.get<AuthRepository>())
        assertNotNull(koin.get<DashboardRepository>())

        assertNotNull(koin.get<GetDevicesUseCase>())
        assertNotNull(koin.get<GetDeviceByIdUseCase>())
        assertNotNull(koin.get<CreateDeviceUseCase>())
        assertNotNull(koin.get<UpdateDeviceUseCase>())
        assertNotNull(koin.get<DeleteDeviceUseCase>())
        assertNotNull(koin.get<GetStagingDevicesUseCase>())
        assertNotNull(koin.get<ApproveDeviceUseCase>())
        assertNotNull(koin.get<DismissDeviceUseCase>())
        assertNotNull(koin.get<GetSubnetsUseCase>())
        assertNotNull(koin.get<CreateSubnetUseCase>())
        assertNotNull(koin.get<GetSetupStatusUseCase>())
        assertNotNull(koin.get<LoginUseCase>())
        assertNotNull(koin.get<OnboardUseCase>())
        assertNotNull(koin.get<GetCurrentUserUseCase>())
        assertNotNull(koin.get<GetHealthUseCase>())
        assertNotNull(koin.get<GetDeviceSummaryUseCase>())
        assertNotNull(koin.get<GetStagingSummaryUseCase>())
        assertNotNull(koin.get<GetDiscoverySourcesUseCase>())

        val scope = CoroutineScope(Dispatchers.Unconfined)
        assertNotNull(koin.get<DeviceListViewModel> { parametersOf(scope) })
        assertNotNull(koin.get<DeviceDetailViewModel> { parametersOf("dev-1", scope) })
        assertNotNull(koin.get<CreateDeviceViewModel> { parametersOf(scope) })
        assertNotNull(koin.get<EditDeviceViewModel> { parametersOf("dev-1", scope) })
        assertNotNull(koin.get<StagingViewModel> { parametersOf(scope) })
        assertNotNull(koin.get<SubnetsViewModel> { parametersOf(scope) })
        assertNotNull(koin.get<CreateSubnetViewModel> { parametersOf(scope) })
        assertNotNull(koin.get<DashboardViewModel> { parametersOf(null as SSEClient?, scope) })
        assertNotNull(koin.get<LoginViewModel> { parametersOf(scope) })
        assertNotNull(koin.get<OnboardingViewModel> { parametersOf(scope) })
        assertNotNull(koin.get<SplashViewModel> { parametersOf(scope) })

        app.close()
    }

    @Test
    fun testModuleDeclarations() {
        assertNotNull(dataModule)
        assertNotNull(domainModule)
        assertNotNull(presentationModule)
        kotlin.test.assertEquals(3, appModules.size)
    }
}
