package com.inframap.frontend.di

import com.inframap.frontend.data.api.ApiClient
import com.inframap.frontend.domain.repository.DeviceRepository
import com.inframap.frontend.domain.usecase.device.GetDevicesUseCase
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

class KoinModuleCheckTest {
    @Test
    fun checkDataAndDomainModulesAreSatisfied() {
        val app =
            koinApplication {
                modules(dataModule, domainModule)
            }

        val apiClient = app.koin.get<ApiClient>()
        val deviceRepo = app.koin.get<DeviceRepository>()
        val getDevicesUseCase = app.koin.get<GetDevicesUseCase>()

        assertNotNull(apiClient)
        assertNotNull(deviceRepo)
        assertNotNull(getDevicesUseCase)

        app.close()
    }
}
