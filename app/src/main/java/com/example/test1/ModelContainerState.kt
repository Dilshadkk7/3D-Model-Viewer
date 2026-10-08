package com.example.test1

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class ModelContainerState(
    val id: Int,
    val modelPath: String
) {
    var x by mutableStateOf(50f)
    var y by mutableStateOf(50f)
    var width by mutableStateOf(250f)
    var height by mutableStateOf(250f)
    
    var viewportWidth by mutableIntStateOf(0)
    var viewportHeight by mutableIntStateOf(0)
    var isInteractionMode by mutableStateOf(false)
    var showLabels by mutableStateOf(false)
    
    // For 3D interaction mode
    var rotationX by mutableStateOf(0f)
    var rotationY by mutableStateOf(0f)
    var scale by mutableStateOf(1f)
    
    // Extracted labels dynamically filled by Filament render loop
    var labels by mutableStateOf<List<ModelLabel>>(emptyList())
}

data class ModelLabel(
    val text: String,
    val screenX: Float,
    val screenY: Float
)
