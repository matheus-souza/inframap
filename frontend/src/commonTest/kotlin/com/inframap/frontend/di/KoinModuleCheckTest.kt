package com.inframap.frontend.di

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.Test
import kotlin.test.assertNotNull

class KoinModuleCheckTest {
    @Test
    fun modulesLoadWithoutErrors() {
        val app =
            startKoin {
                modules(appModules("http://test-host:8080"))
            }
        try {
            assertNotNull(app.koin)
        } finally {
            stopKoin()
        }
    }
}
