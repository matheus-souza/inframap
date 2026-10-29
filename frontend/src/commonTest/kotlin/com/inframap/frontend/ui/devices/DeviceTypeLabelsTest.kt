package com.inframap.frontend.ui.devices

import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.device_type_host
import com.inframap.frontend.generated.resources.device_type_router
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceTypeLabelsTest {
    @Test
    fun knownTypesMapToTheirLabels() {
        assertEquals(Res.string.device_type_router, DeviceTypeLabels.resourceFor("router"))
        assertEquals(Res.string.device_type_router, DeviceTypeLabels.resourceFor(" Router "))
        // Discovery records devices it cannot classify further as "host".
        assertEquals(Res.string.device_type_host, DeviceTypeLabels.resourceFor("host"))
    }

    @Test
    fun aCustomTypeHasNoLabel() {
        assertNull(DeviceTypeLabels.resourceFor("load_balancer"))
    }
}
