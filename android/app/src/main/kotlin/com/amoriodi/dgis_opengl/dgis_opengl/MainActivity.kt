@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "INVISIBLE_SETTER")

package com.amoriodi.dgis_opengl.dgis_opengl

import android.util.Log
import android.view.MotionEvent
import android.view.MotionEvent.CLASSIFICATION_NONE
import android.view.MotionEvent.PointerProperties
import android.view.Surface
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import io.flutter.view.TextureRegistry.SurfaceTextureEntry
import ru.dgis.sdk.*
import ru.dgis.sdk.map.*
import ru.dgis.sdk.map.MapRenderer.Companion.create
import ru.dgis.sdk.map.MapSurfaceProvider.Companion.get
import kotlin.collections.Map
import ru.dgis.sdk.map.Map as DgisMap

class MainActivity: FlutterActivity(), MethodChannel.MethodCallHandler {
    private lateinit var surface: Surface
    private lateinit var renderer: MapRenderer
    private lateinit var sdkContext: Context
    private lateinit var map: DgisMap
    private lateinit var surfaceProvider: MapSurfaceProvider
    private lateinit var texture: SurfaceTextureEntry
    private lateinit var gestureRecognitionManager: MapGestureRecognitionManager
    private var gestureRecognitionEngine: MapGestureRecognitionEngineAdapter? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        this.sdkContext = DGis.initialize(
            context,
            logOptions = LogOptions(
                customLevel = LogLevel.VERBOSE,
                customSink = null
            )
        )

        val channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "dgis_map")
        channel.setMethodCallHandler(this);
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method == "touch") {
            val arguments = call.arguments as List<Any>

            val motionEvent = MotionEvent.obtain(
                (arguments[0] as Int).toLong(),
                (arguments[1] as Int).toLong(),
                arguments[2] as Int,
                arguments[3] as Int,
                (arguments[4] as List<List<Int>>).map {
                    val property = PointerProperties()
                    property.id = it[0]
                    property.toolType = it[1]

                    property
                }.toList().toTypedArray(),
                (arguments[5] as List<List<Double>>).map {
                    val coords = MotionEvent.PointerCoords()
                    coords.orientation = it[0].toFloat()
                    coords.pressure = it[1].toFloat()
                    coords.size = it[2].toFloat()
                    coords.toolMajor = it[3].toFloat()
                    coords.toolMinor = it[4].toFloat()
                    coords.touchMajor = it[5].toFloat()
                    coords.touchMinor = it[6].toFloat()
                    coords.x = it[7].toFloat()
                    coords.y = it[8].toFloat()

                    coords
                }.toList().toTypedArray(),
                arguments[6] as Int,
                arguments[7] as Int,
                (arguments[8] as Double).toFloat(),
                (arguments[9] as Double).toFloat(),
                arguments[10] as Int,
                arguments[11] as Int,
                arguments[12] as Int,
                arguments[13] as Int,
            )

            gestureRecognitionEngine?.processMotionEvent(motionEvent)

            result.success(null)
        } else if (call.method == "create") {
            val arguments = call.arguments as Map<String, Number>
            val width = arguments["width"] as Int
            val height = arguments["height"] as Int

            Log.d("DGIS", "Initiating map with width=$width, height=$height")

            val mapBuilder = MapBuilder()

            val sources = listOf(createDefaultSource(sdkContext))
            sources.forEach {
                mapBuilder.addSource(it)
            }

            mapBuilder.createMap(sdkContext).apply {
                onResult {
                    Log.d("DGIS", "Map initialized")

                    val camera = it.camera

                    camera.size = ScreenSize(
                        width = width,
                        height = height,
                    )

                    this@MainActivity.surfaceProvider = get(it)

                    this@MainActivity.texture = flutterEngine!!.renderer.createSurfaceTexture()
                    val surfaceTexture = texture.surfaceTexture()
                    surfaceTexture.setDefaultBufferSize(width, height)

                    this@MainActivity.surface = Surface(surfaceTexture)
                    surfaceProvider.setSurface(surface, ScreenSize(width, height))

                    it.mapVisibilityState = MapVisibilityState.VISIBLE

                    this@MainActivity.renderer = create(it)
                    this@MainActivity.map = it

                    this@MainActivity.gestureRecognitionManager = MapGestureRecognitionManager()
                    gestureRecognitionManager.useDefaultGestureRecognitionEngine()
                    gestureRecognitionManager.attachToMap(map)
                    this@MainActivity.gestureRecognitionEngine = gestureRecognitionManager.gestureRecognitionEngine

                    renderer.waitForLoading().apply {
                        onResult {
                            result.success(texture.id())
                            Log.d("DGIS", "Renderer loaded")
                        }
                        onError {
                            Log.e("DGIS", it.localizedMessage)
                            result.error("DGis", "Failed to load renderer", it.localizedMessage)
                        }
                    }
                }
                onError {
                    Log.e("DGIS", it.localizedMessage)
                    result.error("DGis", "Failed to initialize map", it.localizedMessage)
                }
            }
        }
    }
}
