package com.inframap.frontend.domain.model

import com.inframap.frontend.data.dto.SubnetAddressDevice as DtoSubnetAddressDevice
import com.inframap.frontend.data.dto.SubnetAddressEntry as DtoSubnetAddressEntry
import com.inframap.frontend.data.dto.SubnetAddressMapResponse as DtoSubnetAddressMapResponse

data class SubnetAddressDevice(
    val id: String,
    val hostname: String,
    val ipAssignment: String,
)

data class SubnetAddressEntry(
    val address: String,
    val state: String,
    val devices: List<SubnetAddressDevice> = emptyList(),
    val dnsNames: List<String> = emptyList(),
)

data class SubnetAddressMap(
    val subnetId: String,
    val cidr: String,
    val entries: List<SubnetAddressEntry> = emptyList(),
) {
    companion object {
        fun fromDto(dto: DtoSubnetAddressMapResponse): SubnetAddressMap =
            SubnetAddressMap(
                subnetId = dto.subnetId,
                cidr = dto.cidr,
                entries = dto.entries.map { entry ->
                    SubnetAddressEntry(
                        address = entry.address,
                        state = entry.state,
                        devices = entry.devices.map { d ->
                            SubnetAddressDevice(
                                id = d.id,
                                hostname = d.hostname,
                                ipAssignment = d.ipAssignment,
                            )
                        },
                        dnsNames = entry.dnsNames,
                    )
                },
            )
    }
}
