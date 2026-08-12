package com.inframap.frontend.di

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.repository.AuthRepositoryImpl
import com.inframap.frontend.data.repository.DashboardRepositoryImpl
import com.inframap.frontend.data.repository.DeviceRepositoryImpl
import com.inframap.frontend.data.repository.StagingRepositoryImpl
import com.inframap.frontend.data.repository.SubnetRepositoryImpl
import com.inframap.frontend.data.repository.NetworkRepositoryImpl
import com.inframap.frontend.data.repository.TopologyRepositoryImpl
import com.inframap.frontend.domain.repository.AuthRepository
import com.inframap.frontend.domain.repository.DashboardRepository
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.repository.NetworkRepository
import com.inframap.frontend.domain.repository.StagingRepository
import com.inframap.frontend.domain.repository.SubnetRepository
import com.inframap.frontend.domain.repository.TopologyRepository
import org.koin.dsl.module

fun dataModule(baseUrl: String) =
    module {
        single { ApiClient(baseUrl) }
        single<DeviceRepository> { DeviceRepositoryImpl(get()) }
        single<StagingRepository> { StagingRepositoryImpl(get()) }
        single<SubnetRepository> { SubnetRepositoryImpl(get()) }
        single<NetworkRepository> { NetworkRepositoryImpl(get()) }
        single<AuthRepository> { AuthRepositoryImpl(get()) }
        single<DashboardRepository> { DashboardRepositoryImpl(get()) }
        single<TopologyRepository> { TopologyRepositoryImpl(get()) }
    }
