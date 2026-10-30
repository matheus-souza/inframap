package com.inframap.frontend.ui.devices

import com.inframap.frontend.data.dto.DeviceDto
import com.inframap.frontend.data.mapper.DeviceMapper
import com.inframap.frontend.domain.model.IpAssignments
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.ip_assignment_reserved
import com.inframap.frontend.generated.resources.ip_assignment_unknown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpAssignmentLabelsTest {
    @Test
    fun eachAssignmentHasALabelAndAnUnexpectedOneReadsAsUnknown() {
        assertEquals(Res.string.ip_assignment_reserved, IpAssignmentLabels.resourceFor(IpAssignments.RESERVED))
        assertEquals(Res.string.ip_assignment_unknown, IpAssignmentLabels.resourceFor("leased"))
        assertEquals(
            IpAssignments.ALL.size,
            IpAssignments.ALL
                .map { IpAssignmentLabels.resourceFor(it) }
                .toSet()
                .size,
        )
    }

    @Test
    fun onlyReservedAndStaticAddressesAreFixed() {
        assertTrue(IpAssignments.isFixed(IpAssignments.RESERVED))
        assertTrue(IpAssignments.isFixed(IpAssignments.STATIC))
        assertFalse(IpAssignments.isFixed(IpAssignments.DYNAMIC))
        assertFalse(IpAssignments.isFixed(IpAssignments.UNKNOWN))
    }

    @Test
    fun aDeviceFromAnOlderServerWithoutAnAssignmentReadsAsUnknown() {
        val device = DeviceMapper.toDomain(DeviceDto(id = "d1", hostname = "nas", deviceType = "server", status = "active"))

        assertEquals(IpAssignments.UNKNOWN, device.ipAssignment)
    }
}
