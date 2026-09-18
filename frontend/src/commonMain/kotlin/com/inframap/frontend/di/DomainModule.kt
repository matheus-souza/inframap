package com.inframap.frontend.di

import com.inframap.frontend.domain.usecase.auth.GetCurrentUserUseCase
import com.inframap.frontend.domain.usecase.auth.GetSetupStatusUseCase
import com.inframap.frontend.domain.usecase.auth.LoginUseCase
import com.inframap.frontend.domain.usecase.auth.OnboardUseCase
import com.inframap.frontend.domain.usecase.command.SearchIndexUseCase
import com.inframap.frontend.domain.usecase.credentials.DeleteCredentialUseCase
import com.inframap.frontend.domain.usecase.credentials.ListCredentialsUseCase
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
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceByIdUseCase
import com.inframap.frontend.domain.usecase.discovery.GetDiscoverySourceDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.discovery.TestDiscoverySourceHealthUseCase
import com.inframap.frontend.domain.usecase.discovery.TriggerDiscoveryRunUseCase
import com.inframap.frontend.domain.usecase.discovery.UpdateDiscoverySourceUseCase
import com.inframap.frontend.domain.usecase.integrations.TestProviderHealthUseCase
import com.inframap.frontend.domain.usecase.network.GetNetworkInterfacesUseCase
import com.inframap.frontend.domain.usecase.staging.ApproveDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.DismissDeviceUseCase
import com.inframap.frontend.domain.usecase.staging.GetStagingDevicesUseCase
import com.inframap.frontend.domain.usecase.subnet.CreateSubnetUseCase
import com.inframap.frontend.domain.usecase.subnet.DeleteSubnetUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetByIdUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetCidrImpactUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetDeletionImpactUseCase
import com.inframap.frontend.domain.usecase.subnet.GetSubnetsUseCase
import com.inframap.frontend.domain.usecase.subnet.ListSubnetsUseCase
import com.inframap.frontend.domain.usecase.subnet.UpdateSubnetUseCase
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
        factory { GetSubnetByIdUseCase(get()) }
        factory { ListSubnetsUseCase(get()) }
        factory { CreateSubnetUseCase(get()) }
        factory { UpdateSubnetUseCase(get()) }
        factory { GetSubnetCidrImpactUseCase(get()) }
        factory { DeleteSubnetUseCase(get()) }
        factory { GetSubnetDeletionImpactUseCase(get()) }

        factory { GetNetworkInterfacesUseCase(get()) }

        factory { GetSetupStatusUseCase(get()) }
        factory { LoginUseCase(get()) }
        factory { OnboardUseCase(get()) }
        factory { GetCurrentUserUseCase(get()) }

        factory { GetHealthUseCase(get()) }
        factory { GetDeviceSummaryUseCase(get()) }
        factory { GetStagingSummaryUseCase(get()) }
        factory { GetDiscoverySourcesUseCase(get()) }

        factory { GetTopologyGraphUseCase(get()) }

        factory { SearchIndexUseCase(get(), get(), get()) }
        factory { GetDiscoverySources(get()) }
        factory { CreateDiscoverySourceUseCase(get()) }
        factory { TestProviderHealthUseCase(get()) }
        factory { ListCredentialsUseCase(get()) }
        factory { DeleteCredentialUseCase(get()) }
        factory { TriggerDiscoveryRunUseCase(get()) }

        factory { DeleteDiscoverySourceUseCase(get()) }
        factory { GetDiscoverySourceDeletionImpactUseCase(get()) }
        factory { GetDiscoverySourceByIdUseCase(get()) }
        factory { UpdateDiscoverySourceUseCase(get()) }
        factory { TestDiscoverySourceHealthUseCase(get()) }
    }
