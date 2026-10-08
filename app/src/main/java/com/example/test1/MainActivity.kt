package com.example.test1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                    ModelViewer()
                }
            }
        }
    }
}

data class AvailableModel(val id: Int, val name: String, val filename: String)

val ALL_MODELS = listOf(
    AvailableModel(1, "Model 1 (Piston Engine)", "model_1.glb"),
    AvailableModel(2, "Model 2 (Turbine Blade)", "model_2.glb"),
    AvailableModel(3, "Model 3 (Gearbox Casing)", "model_3.glb"),
    AvailableModel(4, "Model 4 (Exhaust Manifold)", "model_4.glb"),
    AvailableModel(5, "Model 5 (Crankshaft Assembly)", "model_5.glb")
)

@Composable
fun ModelViewer() {
    val context = LocalContext.current
    val filamentManager = remember { FilamentManager(context) }
    val models = remember { mutableStateListOf<ModelContainerState>() }
    var nextId by remember { mutableIntStateOf(1) }
    var showModelSelector by remember { mutableStateOf(false) }
    
    // To trigger recompositions for labels overlay
    var frameTick by remember { mutableIntStateOf(0) }
    
    DisposableEffect(filamentManager) {
        filamentManager.activeModels = models
        filamentManager.frameTrigger = { frameTick++ }
        filamentManager.start()
        onDispose {
            filamentManager.destroy()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        
        // Render 2D controls and independent TextureView Containers for each model
        models.forEach { state ->
            key(state.id) {
                ModelContainer(
                    state = state,
                    filamentManager = filamentManager,
                    onBringToFront = {
                        if (models.lastOrNull() != state) {
                            models.remove(state)
                            models.add(state)
                        }
                    },
                    onClose = {
                        models.remove(state)
                        // the resource cleanup happens via DisposableEffect inside ModelContainer
                    }
                )
            }
        }
        
        // Global "Add Model" Button
        Button(
            onClick = { showModelSelector = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            enabled = models.size < 5
        ) {
            Text("+ ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Add Model (${models.size}/5)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        
        // Model Selection Dialog
        if (showModelSelector) {
            AlertDialog(
                onDismissRequest = { showModelSelector = false },
                title = { Text("Select 3D Model to Add") },
                text = {
                    LazyColumn {
                        items(ALL_MODELS) { model ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        if (models.size < 5) {
                                            val id = nextId++
                                            val initialSizeDp = 250f
                                            val newState = ModelContainerState(
                                                id = id,
                                                modelPath = model.filename
                                            ).apply {
                                                x = 20f + models.size * 20f
                                                y = 40f + models.size * 20f
                                                width = initialSizeDp
                                                height = initialSizeDp
                                            }
                                            models.add(newState)
                                        }
                                        showModelSelector = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📦 ", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = model.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showModelSelector = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ModelContainer(
    state: ModelContainerState,
    filamentManager: FilamentManager,
    onBringToFront: () -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current.density
    
    // Filament resources tied strictly to this Compose node
    val instance = remember { filamentManager.createInstance(state) }
    
    DisposableEffect(Unit) {
        onDispose {
            filamentManager.removeInstance(state.id)
        }
    }
    
    Box(
        modifier = Modifier
            .offset(state.x.dp, state.y.dp)
            .size(width = state.width.dp, height = state.height.dp)
            .border(
                width = if (state.isInteractionMode) 3.dp else 2.dp,
                color = if (state.isInteractionMode) Color.Red else Color(0xFF2196F3),
                shape = RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) {
                            onBringToFront()
                        }
                    }
                }
            }
            .pointerInput(state.isInteractionMode) {
                if (state.isInteractionMode) {
                    // Interaction mode: 1-finger rotate 3D model, 2-finger zoom 3D model
                    detectTransformGestures { _, pan, zoom, _ ->
                        state.rotationX += pan.y * 0.5f
                        state.rotationY += pan.x * 0.5f
                        state.scale = (state.scale * zoom).coerceIn(0.2f, 5.0f)
                    }
                } else {
                    // Normal mode: 1-finger move container, 2-finger resize container
                    detectTransformGestures { _, pan, zoom, _ ->
                        state.x += pan.x / density
                        state.y += pan.y / density
                        
                        val minDp = 150f
                        val maxDp = 500f
                        
                        val oldWidth = state.width
                        val oldHeight = state.height
                        
                        val newWidth = (state.width * zoom).coerceIn(minDp, maxDp)
                        val newHeight = (state.height * zoom).coerceIn(minDp, maxDp)
                        
                        state.width = newWidth
                        state.height = newHeight
                        
                        // Keep scaling anchored near center
                        state.x -= (newWidth - oldWidth) / 2
                        state.y -= (newHeight - oldHeight) / 2
                    }
                }
            }
            // Draw connector lines for part labels
            .drawBehind {
                state.labels.forEach { label ->
                    val localX = label.screenX
                    val localY = label.screenY
                    
                    drawLine(
                        color = Color.Yellow,
                        start = Offset(localX, localY),
                        end = Offset(localX + 45f, localY - 45f),
                        strokeWidth = 3f
                    )
                }
            }
    ) {
        // Native Android TextureView handles strict viewport clipping,
        // layout sizing, and hardware-accelerated Z-ordering implicitly.
        AndroidView(
            factory = { instance.textureView },
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
        )
        
        // Top Control Bar with 3 mandatory buttons: Interaction Toggle, Label Toggle, Close
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Button 1: Interaction Toggle
            Button(
                onClick = { state.isInteractionMode = !state.isInteractionMode },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isInteractionMode) Color.Red else Color(0xFF1E88E5)
                )
            ) {
                Text(if (state.isInteractionMode) "Normal" else "Interact", fontSize = 11.sp, maxLines = 1)
            }
            
            // Button 2: Label Toggle
            Button(
                onClick = { state.showLabels = !state.showLabels },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.showLabels) Color(0xFF4CAF50) else Color(0xFF757575)
                )
            ) {
                Text(if (state.showLabels) "Hide Lbls" else "Show Lbls", fontSize = 11.sp, maxLines = 1)
            }
            
            // Button 3: Close Button
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(30.dp)
            ) {
                Text("✕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        // Draw 2D Part Labels overlay
        state.labels.forEach { label ->
            val localXDp = label.screenX / density
            val localYDp = label.screenY / density
            
            Text(
                text = label.text,
                color = Color.Black,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset((localXDp + 15).dp, (localYDp - 25).dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Yellow)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
