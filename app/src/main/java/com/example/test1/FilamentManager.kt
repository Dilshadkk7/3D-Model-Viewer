package com.example.test1

import android.content.Context
import android.opengl.Matrix
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer

class FilamentManager(private val context: Context) : Choreographer.FrameCallback {

    companion object {
        init {
            Filament.init()
            Gltfio.init()
        }
    }

    private var engine: Engine = Engine.create()
    private var renderer: Renderer = engine.createRenderer().apply {
        clearOptions = clearOptions.apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
            discard = true
        }
    }
    private var choreographer: Choreographer = Choreographer.getInstance()
    private val assetLoader = AssetLoader(engine, UbershaderProvider(engine), EntityManager.get())
    private val resourceLoader = ResourceLoader(engine)

    val instances = mutableMapOf<Int, ModelInstance>()
    var activeModels: List<ModelContainerState> = emptyList()
    var frameTrigger: () -> Unit = {}
    private var isRunning = false

    inner class ModelInstance(val state: ModelContainerState) {
        val textureView = TextureView(context).apply {
            isOpaque = false // Transparent background for overlay
        }
        val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        var swapChain: SwapChain? = null

        val asset: FilamentAsset
        val scene: Scene
        val view: View
        val camera: Camera
        val lightEntity: Int

        init {
            uiHelper.renderCallback = object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(surface: Surface) {
                    swapChain?.let { engine.destroySwapChain(it) }
                    swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                }

                override fun onDetachedFromSurface() {
                    swapChain?.let {
                        engine.destroySwapChain(it)
                        engine.flushAndWait()
                        swapChain = null
                    }
                }

                override fun onResized(width: Int, height: Int) {
                    state.viewportWidth = width
                    state.viewportHeight = height
                    view.viewport = Viewport(0, 0, width, height)
                    val aspect = width.toDouble() / height.toDouble()
                    camera.setProjection(45.0, aspect, 0.1, 100.0, Camera.Fov.VERTICAL)
                }
            }
            uiHelper.attachTo(textureView)

            val buffer = context.assets.open(state.modelPath).readBytes()
            val byteBuffer = ByteBuffer.allocateDirect(buffer.size).put(buffer)
            byteBuffer.rewind()

            asset = assetLoader.createAsset(byteBuffer)!!
            resourceLoader.asyncBeginLoad(asset)
            resourceLoader.asyncUpdateLoad()

            scene = engine.createScene()
            scene.addEntities(asset.entities)

            // Add Directional Light
            lightEntity = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.SUN)
                .color(1.0f, 0.95f, 0.9f)
                .intensity(100000.0f)
                .direction(0.0f, -1.0f, -1.0f)
                .build(engine, lightEntity)
            scene.addEntity(lightEntity)

            val skybox = Skybox.Builder().color(0f, 0f, 0f, 0f).build(engine)
            scene.skybox = skybox

            camera = engine.createCamera(EntityManager.get().create())
            view = engine.createView()
            view.scene = scene
            view.camera = camera
            view.blendMode = View.BlendMode.TRANSLUCENT

            val aabb = asset.boundingBox
            val center = aabb.center
            val halfExtent = aabb.halfExtent
            val maxExtent = Math.max(halfExtent[0], Math.max(halfExtent[1], halfExtent[2]))
            val distance = (maxExtent / Math.tan(Math.toRadians(45.0) / 2.0)).toFloat()

            camera.lookAt(
                center[0].toDouble(), center[1].toDouble(), (center[2] + distance * 1.5).toDouble(),
                center[0].toDouble(), center[1].toDouble(), center[2].toDouble(),
                0.0, 1.0, 0.0
            )

            val tm = engine.transformManager
            val root = asset.root
            val identity = FloatArray(16)
            Matrix.setIdentityM(identity, 0)
            tm.setTransform(tm.getInstance(root), identity)
        }

        fun updateTransform() {
            val tm = engine.transformManager
            val root = asset.root
            val instance = tm.getInstance(root)

            val mat = FloatArray(16)
            Matrix.setIdentityM(mat, 0)
            Matrix.scaleM(mat, 0, state.scale, state.scale, state.scale)
            Matrix.rotateM(mat, 0, state.rotationX, 1f, 0f, 0f)
            Matrix.rotateM(mat, 0, state.rotationY, 0f, 1f, 0f)

            tm.setTransform(instance, mat)
        }

        fun extractLabels() {
            if (!state.showLabels) {
                if (state.labels.isNotEmpty()) {
                    state.labels = emptyList()
                }
                return
            }

            val labels = mutableListOf<ModelLabel>()
            val viewProj = FloatArray(16)
            val projD = DoubleArray(16)
            camera.getProjectionMatrix(projD)
            val viewMatF = FloatArray(16)
            camera.getViewMatrix(viewMatF)

            val projF = FloatArray(16) { projD[it].toFloat() }
            Matrix.multiplyMM(viewProj, 0, projF, 0, viewMatF, 0)

            for (entity in asset.entities) {
                val extras = asset.getExtras(entity)
                if (extras != null && extras.contains("prop")) {
                    val name = extras.substringAfter("\"prop\":\"").substringBefore("\"")

                    val tm = engine.transformManager
                    val instance = tm.getInstance(entity)
                    if (instance != 0) {
                        val worldTransform = FloatArray(16)
                        tm.getWorldTransform(instance, worldTransform)

                        val vec = floatArrayOf(
                            worldTransform[12],
                            worldTransform[13],
                            worldTransform[14],
                            1f
                        )
                        val out = FloatArray(4)
                        Matrix.multiplyMV(out, 0, viewProj, 0, vec, 0)

                        if (out[3] != 0f) {
                            val ndcX = out[0] / out[3]
                            val ndcY = out[1] / out[3]

                            val screenX = (ndcX + 1f) / 2f * state.viewportWidth
                            val screenY = (1f - ndcY) / 2f * state.viewportHeight

                            labels.add(ModelLabel(name, screenX, screenY))
                        }
                    }
                }
            }
            state.labels = labels
        }

        fun destroy() {
            uiHelper.detach()
            engine.destroyCameraComponent(camera.entity)
            engine.lightManager.destroy(lightEntity)
            EntityManager.get().destroy(lightEntity)
            EntityManager.get().destroy(camera.entity)
            engine.destroyView(view)
            engine.destroyScene(scene)
            assetLoader.destroyAsset(asset)
            swapChain?.let { engine.destroySwapChain(it) }
        }
    }

    fun createInstance(state: ModelContainerState): ModelInstance {
        val instance = ModelInstance(state)
        instances[state.id] = instance
        return instance
    }

    fun removeInstance(id: Int) {
        instances.remove(id)?.destroy()
    }

    fun start() {
        if (!isRunning) {
            isRunning = true
            choreographer.postFrameCallback(this)
        }
    }

    fun stop() {
        isRunning = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return
        choreographer.postFrameCallback(this)

        var didRender = false
        for (state in activeModels) {
            val instance = instances[state.id] ?: continue
            val sc = instance.swapChain

            if (instance.uiHelper.isReadyToRender && sc != null && state.viewportWidth > 0 && state.viewportHeight > 0) {
                instance.updateTransform()
                instance.extractLabels()

                if (renderer.beginFrame(sc, frameTimeNanos)) {
                    renderer.render(instance.view)
                    renderer.endFrame()
                    didRender = true
                }
            }
        }
        if (didRender) {
            frameTrigger()
        }
    }

    fun destroy() {
        stop()
        instances.values.forEach { it.destroy() }
        instances.clear()

        assetLoader.destroy()
        resourceLoader.destroy()

        engine.destroyRenderer(renderer)
        engine.destroy()
    }
}
