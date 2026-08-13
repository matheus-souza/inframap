package com.inframap.frontend.fakes

import com.inframap.frontend.data.api.ApiResult
import com.inframap.frontend.domain.model.NetworkInterface
import com.inframap.frontend.domain.repository.NetworkRepository

class FakeNetworkRepository(
    var getInterfacesResult: ApiResult<List<NetworkInterface>> =
        ApiResult.Success(
            listOf(DEFAULT_INTERFACE),
            requestId = "",
        ),
) : NetworkRepository {
    override suspend fun getInterfaces() = getInterfacesResult

    companion object {
        val DEFAULT_INTERFACE =
            NetworkInterface(
                name = "eth0",
                ip = "192.168.18.5",
                cidr = "192.168.18.0/24",
                mac = "aa:bb:cc:dd:ee:ff",
                gateway = "192.168.18.1",
            )
    }
}
