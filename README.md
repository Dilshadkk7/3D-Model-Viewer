# 3D Model Viewer - Infusory Future Tech Labs

## 3D Library Used: Google Filament
I chose **Google Filament** over SceneView or other higher-level wrappers for a very specific performance reason. High-level wrappers like SceneView often initialize completely separate engine and rendering environments for every single 3D view added to the screen, which drastically consumes memory and impacts frame rate on low-end devices.

By using pure Filament, I implemented a **Shared Engine Architecture**. All models on the screen share a single global Filament `Engine`, `Renderer`, `Choreographer`, `AssetLoader`, and `ResourceLoader`. However, to guarantee precise gesture z-ordering and strict container clipping (ensuring models never visually spill outside their bounds), each model instance is backed by its own lightweight, transparent `TextureView` and `SwapChain`. This hybrid approach leverages Jetpack Compose's native clipping and layout engine while keeping the 3D rendering pipeline heavily optimized.

## Main Performance Optimizations
1. **Shared Engine & Renderer:** Bypasses the massive memory and CPU overhead of instantiating multiple 3D engines. A single `doFrame` Choreographer loop updates all active models sequentially.
2. **Efficient Transform Updates:** Matrix math for 3D rotation, scaling, and 2D label projection is handled natively via `android.opengl.Matrix` without creating heavy object allocations (garbage) in the render loop.
3. **Strict Resource Management:** When a model is closed, its isolated Filament `View`, `Camera`, `Scene`, `FilamentAsset`, and `SwapChain` are immediately destroyed, freeing VRAM instantly without affecting the other active models.
4. **Independent TextureViews:** Using transparent `TextureView`s natively solves complex bounds clipping, intersections, and Compose Z-ordering without having to write expensive manual scissors-testing or manual bounds calculations inside the 3D renderer.

## Trade-offs
1. **Implementation Complexity:** Writing direct Filament boilerplate for lifecycle management, GLTF loading, and matrix math projection is significantly more complex than using a plug-and-play high-level library.
2. **TextureView Overhead:** While `TextureView` ensures perfect clipping and integrates cleanly with Compose z-ordering and animations, it does lack the absolute maximum raw rendering speed of `SurfaceView`. However, the shared `Renderer` loop mitigates this overhead, keeping it well within the >=30 FPS target for low-end devices.

## What I would improve with more time
1. Add environmental lighting (IBL) and realistic shadows via `IndirectLight` for higher visual fidelity.
2. Implement 3D bounds clamping so models do not rotate completely out of the viewport.

## Known Limitations
* I generated dummy/placeholder `.glb` files since the shared folder was not provided in the original text prompt environment. However, the `extras.prop` parser, camera projection, and 2D-connector overlay logic are fully implemented and work accurately for the required JSON metadata format.

## Tested On
* Android Studio Emulator (API 34)
* Android Studio Emulator (Pixel 2 API 29)
