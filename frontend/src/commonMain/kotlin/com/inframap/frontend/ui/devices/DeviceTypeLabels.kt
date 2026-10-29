package com.inframap.frontend.ui.devices

import androidx.compose.runtime.Composable
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.device_type_access_point
import com.inframap.frontend.generated.resources.device_type_firewall
import com.inframap.frontend.generated.resources.device_type_host
import com.inframap.frontend.generated.resources.device_type_iot
import com.inframap.frontend.generated.resources.device_type_other
import com.inframap.frontend.generated.resources.device_type_router
import com.inframap.frontend.generated.resources.device_type_server
import com.inframap.frontend.generated.resources.device_type_switch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Display names of the device types InfraMap knows; a custom type is shown as typed. */
internal object DeviceTypeLabels {
    fun resourceFor(deviceType: String): StringResource? =
        when (deviceType.trim().lowercase()) {
            "router" -> Res.string.device_type_router
            "switch" -> Res.string.device_type_switch
            "server" -> Res.string.device_type_server
            "firewall" -> Res.string.device_type_firewall
            "access_point" -> Res.string.device_type_access_point
            "iot" -> Res.string.device_type_iot
            "host" -> Res.string.device_type_host
            "other" -> Res.string.device_type_other
            else -> null
        }
}

/** The localized name of [deviceType], or the type itself when it is a custom one. */
@Composable
internal fun deviceTypeLabel(deviceType: String): String =
    DeviceTypeLabels.resourceFor(deviceType)?.let { stringResource(it) } ?: deviceType
