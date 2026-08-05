package com.inframap.frontend.di

import com.inframap.frontend.data.sse.SSEClient
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

class KoinModuleCheckTest {
    @Test
    fun verifyAllModulesAreSatisfied() {
        module {
            includes(appModules("http://test-host:8080"))
        }.verify(
            extraTypes = koinParameterTypes,
        )
    }

    companion object {
        val koinParameterTypes =
            listOf(
                String::class,
                SSEClient::class,
            )
    }
}
