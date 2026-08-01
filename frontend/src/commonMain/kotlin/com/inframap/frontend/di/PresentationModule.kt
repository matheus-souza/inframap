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
import org.koin.dsl.module

val presentationModule =
    module {
        factory { DeviceListViewModel(get(), get()) }
        factory { (deviceId: String) -> DeviceDetailViewModel(deviceId, get(), get()) }
        factory { CreateDeviceViewModel(get()) }
        factory { (deviceId: String) -> EditDeviceViewModel(deviceId, get(), get()) }

        factory { StagingViewModel(get(), get(), get()) }

        factory { SubnetsViewModel(get()) }
        factory { CreateSubnetViewModel(get()) }

        factory { (sseClient: SSEClient?) -> DashboardViewModel(get(), get(), get(), get(), sseClient) }

        factory { LoginViewModel(get()) }
        factory { OnboardingViewModel(get()) }
        factory { SplashViewModel(get(), get()) }
    }
