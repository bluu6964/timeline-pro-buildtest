package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LayerType
import com.example.model.SpaceOps
import com.example.model.TimelineClip
import com.example.ui.theme.AmAccent
import com.example.ui.theme.AmTextDim
import com.example.ui.theme.AmTextPrimary
import kotlin.math.roundToInt

/**
 * Build 262 — the 3D Space panel (Sarowar's feature): the space's own camera
 * (orbit pitch / yaw, dolly, zoom, pan — the space clip's transform fields,
 * written through the same keyframed path as the Transform panel, so the
 * camera can be keyframed), the scene's grid (on/off + floor size), the
 * extrusion depth (the space default, plus a per-layer override for each
 * TEXT / SHAPE child), and a Reset view button. A project Camera layer does
 * NOT drive the space — this camera is the space's own.
 */
@Composable
fun SpaceEditView(
    space: TimelineClip,
    children: List<TimelineClip>,
    onCamera: (TimelineClip) -> Unit,
    onSpaceField: (TimelineClip) -> Unit,
    onChildExtrude: (childId: String, value: Float) -> Unit,
    onResetView: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("space_edit_view")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("3D Space", color = AmTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(space.name, color = AmTextDim, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onResetView) {
                Text("Reset view", color = AmAccent, fontSize = 13.sp)
            }
        }
        SpaceSliderRow("Orbit X (pitch)", space.rotation3dX, -60f, 60f, "%.0f°") {
            onCamera(space.copy(rotation3dX = it))
        }
        SpaceSliderRow("Orbit Y (yaw)", space.rotation3dY, -60f, 60f, "%.0f°") {
            onCamera(space.copy(rotation3dY = it))
        }
        SpaceSliderRow("Dolly (distance)", space.transformZ, -800f, 800f, "%.0f") {
            onCamera(space.copy(transformZ = it))
        }
        SpaceSliderRow("Zoom", space.scalePercent, 25f, 400f, "%.0f%%") {
            onCamera(space.copy(scalePercent = it))
        }
        SpaceSliderRow("Pan X", space.transformX, -1000f, 1000f, "%.0f") {
            onCamera(space.copy(transformX = it))
        }
        SpaceSliderRow("Pan Y", space.transformY, -1000f, 1000f, "%.0f") {
            onCamera(space.copy(transformY = it))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Grid floor", color = AmTextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = space.spaceGridOn,
                onCheckedChange = { onSpaceField(space.copy(spaceGridOn = it)) },
                modifier = Modifier.testTag("space_grid_switch")
            )
        }
        if (space.spaceGridOn) {
            SpaceSliderRow("Grid size", space.spaceGridSize, 400f, 4000f, "%.0f") {
                onSpaceField(space.copy(spaceGridSize = it))
            }
        }
        SpaceSliderRow("Extrude depth", space.spaceExtrude, 0f, 400f, "%.0f") {
            onSpaceField(space.copy(spaceExtrude = it))
        }
        val extrudables = children.filter { it.type == LayerType.TEXT || it.type == LayerType.SHAPE }
        if (extrudables.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Layer extrusion", color = AmTextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            for (child in extrudables) {
                val own = child.spaceExtrudeSelf
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        child.name, color = AmTextPrimary, fontSize = 12.sp,
                        modifier = Modifier.width(96.dp), maxLines = 1
                    )
                    Slider(
                        value = SpaceOps.extrudeFor(space, child),
                        onValueChange = { onChildExtrude(child.id, it) },
                        valueRange = 0f..400f,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (own < 0f) "default" else "${own.roundToInt()}",
                        color = AmTextDim, fontSize = 11.sp,
                        modifier = Modifier.width(44.dp)
                    )
                    if (own >= 0f) {
                        TextButton(onClick = { onChildExtrude(child.id, -1f) }) {
                            Text("Reset", color = AmAccent, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Layers inside the space are placed by this camera in 3D. A Camera layer in the project does not drive the space.",
            color = AmTextDim, fontSize = 11.sp
        )
    }
}

@Composable
private fun SpaceSliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    format: String,
    onChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, color = AmTextPrimary, fontSize = 13.sp, modifier = Modifier.width(118.dp), maxLines = 1)
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = onChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f)
        )
        Text(String.format(format, value), color = AmTextDim, fontSize = 12.sp, modifier = Modifier.width(46.dp))
    }
}
