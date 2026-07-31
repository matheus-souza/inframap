package com.inframap.frontend.di

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.data.repository.AuthRepositoryImpl
import com.inframap.frontend.data.repository.DashboardRepositoryImpl
import com.inframap.frontend.data.repository.DeviceRepositoryImpl
import com.inframap.frontend.data.repository.StagingRepositoryImpl
import com.inframap.frontend.data.repository.SubnetRepositoryImpl
import com.inframap.frontend.domain.repository.AuthRepository
import com.inframap.frontend.domain.repository.DashboardRepository
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.repository.StagingRepository
import com.inframap.frontend.domain.repository.SubnetRepository
import org.koin.dsl.module

val dataModule =
    module {
        single { ApiClient("http://localhost:8080") }
        single<DeviceRepository> { DeviceRepositoryImpl(get()) }
        single<StagingRepository> { StagingRepositoryImpl(get()) }
        single<SubnetRepository> { SubnetRepositoryImpl(get()) }
        single<AuthRepository> { AuthRepositoryImpl(get()) }
        single<DashboardRepository> { DashboardRepositoryImpl(get()) }
    }
