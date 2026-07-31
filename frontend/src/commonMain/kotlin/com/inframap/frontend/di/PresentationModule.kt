package com.inframap.frontend.di

import com.inframap.frontend.data.sse.SSEClient
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
import org.koin.dsl.module

val presentationModule =
    module {
        factory { (scope: CoroutineScope) -> DeviceListViewModel(get(), scope) }
        factory { (deviceId: String, scope: CoroutineScope) -> DeviceDetailViewModel(deviceId, get(), scope) }
        factory { (scope: CoroutineScope) -> CreateDeviceViewModel(get(), scope) }
        factory { (deviceId: String, scope: CoroutineScope) -> EditDeviceViewModel(deviceId, get(), scope) }

        factory { (scope: CoroutineScope) -> StagingViewModel(get(), scope) }

        factory { (scope: CoroutineScope) -> SubnetsViewModel(get(), scope) }
        factory { (scope: CoroutineScope) -> CreateSubnetViewModel(get(), scope) }

        factory { (sseClient: SSEClient?, scope: CoroutineScope) -> DashboardViewModel(get(), sseClient, scope) }

        factory { (scope: CoroutineScope) -> LoginViewModel(get(), scope) }
        factory { (scope: CoroutineScope) -> OnboardingViewModel(get(), scope) }
        factory { (scope: CoroutineScope) -> SplashViewModel(get(), scope) }
    }
