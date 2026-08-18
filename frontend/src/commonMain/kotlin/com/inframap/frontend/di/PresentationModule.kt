package com.inframap.frontend.di

import com.inframap.frontend.ui.command.CommandPaletteViewModel
import com.inframap.frontend.ui.dashboard.AutoSetupCoordinator
import com.inframap.frontend.ui.dashboard.DashboardViewModel
import com.inframap.frontend.ui.devices.CreateDeviceViewModel
import com.inframap.frontend.ui.devices.DeviceDetailViewModel
import com.inframap.frontend.ui.devices.DeviceListViewModel
import com.inframap.frontend.ui.devices.EditDeviceViewModel
import com.inframap.frontend.ui.discovery.CreateDiscoverySourceViewModel
import com.inframap.frontend.ui.discovery.DiscoveryListViewModel
import com.inframap.frontend.ui.login.LoginViewModel
import com.inframap.frontend.ui.onboarding.OnboardingCoordinator
import com.inframap.frontend.ui.onboarding.OnboardingViewModel
import com.inframap.frontend.ui.splash.SplashViewModel
import com.inframap.frontend.ui.staging.StagingViewModel
import com.inframap.frontend.ui.subnets.CreateSubnetViewModel
import com.inframap.frontend.ui.subnets.SubnetsViewModel
import com.inframap.frontend.ui.topology.TopologyViewModel
import com.inframap.frontend.ui.tour.ProductTourViewModel
import com.inframap.frontend.ui.wizard.SetupWizardViewModel
import org.koin.dsl.module

val presentationModule =
    module {
        factory { DeviceListViewModel(get(), get()) }
        factory { (deviceId: String) -> DeviceDetailViewModel(deviceId, get(), get()) }
        factory { CreateDeviceViewModel(get()) }
        factory { (deviceId: String) -> EditDeviceViewModel(deviceId, get(), get()) }

        factory { StagingViewModel(get(), get(), get()) }

        factory { SubnetsViewModel(get(), get()) }
        factory { (prefilledCidr: String?, prefilledName: String?) ->
            CreateSubnetViewModel(get(), get(), prefilledCidr, prefilledName)
        }

        factory { DiscoveryListViewModel(get(), get(), get()) }
        factory { CreateDiscoverySourceViewModel(get()) }

        single { AutoSetupCoordinator(get(), get(), get(), get(), get(), get(), get()) }

        factory { DashboardViewModel(get(), get(), get(), get(), get(), get(), getOrNull()) }

        factory { SetupWizardViewModel(get(), get(), get(), get(), get(), get(), get()) }

        factory { ProductTourViewModel(get()) }

        factory { TopologyViewModel(get(), getOrNull()) }

        factory { CommandPaletteViewModel(get()) }

        single { OnboardingCoordinator(get()) }

        factory { LoginViewModel(get()) }
        factory { OnboardingViewModel(get()) }
        factory { SplashViewModel(get(), get()) }
    }
