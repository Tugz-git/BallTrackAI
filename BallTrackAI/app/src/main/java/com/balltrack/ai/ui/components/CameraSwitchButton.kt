package com.balltrack.ai.ui.components

import androidx.camera.core.CameraSelector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Toggles between front and back camera. Used for dribbling/handles mode
 * (face the front camera while practicing) vs. shooting mode (back camera
 * pointed at the hoop, phone on a tripod/mount).
 */
@Composable
fun CameraSwitchButton(
    currentSelector: CameraSelector,
    onSwitch: (CameraSelector) -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {
            val next = if (currentSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            onSwitch(next)
        },
        modifier = modifier
    ) {
        Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
    }
}
