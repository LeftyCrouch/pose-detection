package com.crouch.cameratest

import android.Manifest
import android.graphics.Color.BLACK
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.crouch.cameratest.ui.theme.CameraTestTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CameraTestTheme {
                CameraPermissions()
                Camera()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissions() {
    val cameraPermissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    val permissions = rememberMultiplePermissionsState(
        cameraPermissions
    )
    LaunchedEffect(permissions) {
        if (!permissions.allPermissionsGranted) {
            if (permissions.shouldShowRationale) {
                // Show a rationale for the permissions
            } else {
                permissions.launchMultiplePermissionRequest()
            }
        }
    }
}

@Composable
fun Camera() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                CameraController.VIDEO_CAPTURE or
                CameraController.IMAGE_ANALYSIS
            )
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }
    }
    val poseDetector = PoseDetection
    AndroidView(
        factory = {
            PreviewView(it).apply {
                controller.videoCaptureQualitySelector = QualitySelector.from(Quality.SD)
                controller.setImageAnalysisAnalyzer(
                    ContextCompat.getMainExecutor(context)
                ) { imageProxy ->
                    poseDetector.processImageProxy(imageProxy)
                }
                this.controller = controller
                controller.bindToLifecycle(lifecycleOwner)
                setBackgroundColor(BLACK)
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_START
            }
        },
        modifier = Modifier
            .fillMaxSize()
    )
    CameraCanvas(
        renderer = poseDetector
    )
}

@Composable
fun CameraCanvas(
    renderer: Renderer
) {
    val invalidate = remember {
        mutableIntStateOf(0)
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize(),
        onDraw = {
            renderer.setSize(size.width, size.height)
            invalidate.let { inv ->
                drawIntoCanvas {
                    renderer.onDraw(it)
                }
                inv.intValue++
            }
        }
    )
}

interface Renderer {
    fun onDraw(canvas: Canvas)

    fun setSize(width: Float, height: Float)
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
object PoseDetection: Renderer {
    private val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()

    private val poseDetector = PoseDetection.getClient(options)

    private var pose: Pose? = null

    private val paint = Paint()

    init {
        paint.color = Color.Green
    }

    fun processImageProxy(image: ImageProxy) {
        image.image?.let {
            val rotationDegrees = image.imageInfo.rotationDegrees
            if (rotationDegrees == 0 || rotationDegrees == 180) {
                imageWidth = it.width.toFloat()
                imageHeight = it.height.toFloat()
            } else {
                imageWidth = it.height.toFloat()
                imageHeight = it.width.toFloat()
            }
            val inputImage = InputImage.fromMediaImage(it, image.imageInfo.rotationDegrees)
            poseDetector.process(inputImage)
                .addOnSuccessListener { results ->
                    results?.let {
                        pose = results
                    }
                    image.close()
                }
                .addOnFailureListener {
                    image.close()
                }
        }
    }

    private var screenHeight = 0f
    private var screenWidth = 0f

    private var imageHeight = 0f
    private var imageWidth = 0f

    private var viewAspectRatio = 0f
    private var imageAspectRatio = 0f

    private var scaleFactor = 0f
    private var postScaleWidthOffset = 0f
    private var postScaleHeightOffset = 0f

    override fun onDraw(canvas: Canvas) {
        viewAspectRatio = screenWidth / screenHeight
        imageAspectRatio = imageWidth / imageHeight
        if (viewAspectRatio > imageAspectRatio) {
            // The image needs to be vertically cropped to be displayed in this view.
            scaleFactor = screenWidth / imageWidth
            postScaleHeightOffset = (screenWidth / imageAspectRatio - screenHeight) / 2
        } else {
            // The image needs to be horizontally cropped to be displayed in this view.
            scaleFactor = screenHeight / imageHeight
            postScaleWidthOffset = (screenHeight * imageAspectRatio - screenWidth) / 2
        }

        pose?.let { pose ->
            pose.allPoseLandmarks.forEach { landmark ->
                val point = landmark.position
                canvas.drawCircle(
                    center = Offset(
                        x = screenWidth - ((point.x * scaleFactor) - postScaleWidthOffset),
                        y = (point.y * scaleFactor) - postScaleHeightOffset,
                    ),
                    radius = 10f,
                    paint = paint
                )
            }
        }
    }

    override fun setSize(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
    }
}