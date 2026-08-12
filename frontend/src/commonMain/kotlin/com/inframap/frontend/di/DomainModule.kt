package com.inframap.frontend.di

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
import com.inframap.frontend.domain.usecase.discovery.CreateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.DeleteDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.discovery.TriggerDiscoveryRunUseCase
import com.inframap.frontend.domain.usecase.staging.ApproveDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.DismissDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.GetStagingDevicesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import com.inframap.frontend.domain.usecase.topology.GetTopologyGraphUseCase
import org.koin.dsl.module
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourcesUseCase as GetDiscoverySources

val domainModule =
    module {
        factory { GetDevicesUseCase(get()) }
        factory { GetDeviceByIdUseCase(get()) }
        factory { CreateDeviceUseCase(get()) }
        factory { UpdateDeviceUseCase(get()) }
        factory { DeleteDeviceUseCase(get()) }

        factory { GetStagingDevicesUseCase(get()) }
        factory { ApproveDeviceUseCase(get()) }
        factory { DismissDeviceUseCase(get()) }

        factory { GetSubnetsUseCase(get()) }
        factory { CreateSubnetUseCase(get()) }

        factory { GetSetupStatusUseCase(get()) }
        factory { LoginUseCase(get()) }
        factory { OnboardUseCase(get()) }
        factory { GetCurrentUserUseCase(get()) }

        factory { GetHealthUseCase(get()) }
        factory { GetDeviceSummaryUseCase(get()) }
        factory { GetStagingSummaryUseCase(get()) }
        factory { GetDiscoverySourcesUseCase(get()) }

        factory { GetTopologyGraphUseCase(get()) }

        factory { GetDiscoverySources(get()) }
        factory { CreateDiscoverySourceUseCase(get()) }
        factory { TriggerDiscoveryRunUseCase(get()) }
        factory { DeleteDiscoverySourceUseCase(get()) }
    }
