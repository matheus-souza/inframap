package com.inframap.frontend.ui.devices

import androidx.compose.runtime.Composable
import com.inframap.frontend.domain.model.IpAssignments
import com.inframap.frontend.generated.resources.Res
import com.inframap.frontend.generated.resources.ip_assignment_dynamic
import com.inframap.frontend.generated.resources.ip_assignment_reserved
import com.inframap.frontend.generated.resources.ip_assignment_static
import com.inframap.frontend.generated.resources.ip_assignment_unknown
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Display names of the IP assignments; an unexpected value reads as unknown. */
internal object IpAssignmentLabels {
    fun resourceFor(assignment: String): StringResource =
        when (assignment) {
            IpAssignments.DYNAMIC -> Res.string.ip_assignment_dynamic
            IpAssignments.RESERVED -> Res.string.ip_assignment_reserved
            IpAssignments.STATIC -> Res.string.ip_assignment_static
            else -> Res.string.ip_assignment_unknown
        }
}

@Composable
internal fun ipAssignmentLabel(assignment: String): String = stringResource(IpAssignmentLabels.resourceFor(assignment))
