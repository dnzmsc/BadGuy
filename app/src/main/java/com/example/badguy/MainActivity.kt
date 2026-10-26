package com.example.badguy

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.session.MediaSession
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class WorkflowState {
    SEARCHING,
    LOCKED,
    FIRING
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isTtsReady = false
    private lateinit var mediaSession: MediaSession

    // Weapons list
    private val weapons = listOf("01: Ion Blaster", "02: Plasma Cannon", "03: Photon Torpedo", "04: EMP Disruptor")
    private var currentWeaponIndex by mutableStateOf(0)

    // Workflow & game state
    var workflowState by mutableStateOf(WorkflowState.SEARCHING)
        private set

    var isObjectInCenter by mutableStateOf(false)
        private set

    var fireEventCount = mutableStateOf(0)
        private set

    var currentDetectedBounds = mutableStateOf<Rect?>(null)
        private set

    var lockedBoundsState = mutableStateOf<Rect?>(null)
        private set

    var targetDistance = mutableStateOf(12)
        private set

    var speedKmH = mutableStateOf(24)
        private set

    var cameraFrameWidth by mutableStateOf(1280f)
        private set

    var cameraFrameHeight by mutableStateOf(720f)
        private set

    private var lastCenterBeepTime = 0L
    private var framesWithoutTarget = 0
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            speak("È necessario il permesso della fotocamera per la realtà aumentata.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        tts = TextToSpeech(this, this)

        mediaSession = MediaSession(this, "BadGuyARSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onMediaButtonEvent(mediaButtonIntent: android.content.Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(android.content.Intent.EXTRA_KEY_EVENT)
                    if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                        when (event.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_HEADSETHOOK,
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                runOnUiThread { handleActionTrigger() }
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }
            })
            isActive = true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            BadGuyARApp(
                currentWeapon = weapons[currentWeaponIndex],
                workflowState = workflowState,
                isObjectInCenter = isObjectInCenter,
                fireCount = fireEventCount.value,
                detectedBounds = currentDetectedBounds.value,
                lockedBounds = lockedBoundsState.value,
                distance = targetDistance.value,
                speed = speedKmH.value,
                frameWidth = cameraFrameWidth,
                frameHeight = cameraFrameHeight,
                onActionTrigger = { handleActionTrigger() },
                onDoubleTap = { handleDoubleTap() }
            )
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.ITALIAN)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
                speak("Computer di bordo attivo. Armamento: ${weapons[currentWeaponIndex]}")
            }
        }
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun cycleWeapon(direction: Int) {
        currentWeaponIndex = (currentWeaponIndex + direction + weapons.size) % weapons.size
        val weaponName = weapons[currentWeaponIndex]
        speak("Arma cambiata: $weaponName")
        playBeepSound(400.0, 100)
    }

    fun handleActionTrigger() {
        when (workflowState) {
            WorkflowState.SEARCHING -> {
                val targetToLock = currentDetectedBounds.value ?: Rect(
                    cameraFrameWidth * 0.35f,
                    cameraFrameHeight * 0.35f,
                    cameraFrameWidth * 0.65f,
                    cameraFrameHeight * 0.65f
                )
                lockedBoundsState.value = targetToLock
                workflowState = WorkflowState.LOCKED
                framesWithoutTarget = 0
                playCameraLockSound()
                speak("Bersaglio tracciato")
            }
            WorkflowState.LOCKED -> {
                workflowState = WorkflowState.FIRING
                fireEventCount.value++
                playMissileLaunchSound()
                speak("Lancio missili!")
                android.os.Handler(mainLooper).postDelayed({
                    playCinematicExplosionSound()
                    speak("Bersaglio distrutto!")
                    lockedBoundsState.value = null
                    workflowState = WorkflowState.SEARCHING
                }, 2200)
            }
            WorkflowState.FIRING -> {
                // Already firing
            }
        }
    }

    fun handleDoubleTap() {
        if (workflowState == WorkflowState.LOCKED) {
            lockedBoundsState.value = null
            workflowState = WorkflowState.SEARCHING
            playBeepSound(600.0, 80)
            speak("Bersaglio sbloccato")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                cycleWeapon(1)
                true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                cycleWeapon(-1)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                handleActionTrigger()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    fun updateTargetDetection(
        bounds: Rect?,
        inCenter: Boolean,
        distanceMeters: Int,
        imgWidth: Float,
        imgHeight: Float
    ) {
        currentDetectedBounds.value = bounds
        isObjectInCenter = inCenter
        targetDistance.value = distanceMeters
        cameraFrameWidth = imgWidth
        cameraFrameHeight = imgHeight

        if (workflowState == WorkflowState.SEARCHING && inCenter) {
            val now = System.currentTimeMillis()
            if (now - lastCenterBeepTime > 2000) {
                lastCenterBeepTime = now
                playCameraFocusSound()
            }
        }

        if (workflowState == WorkflowState.LOCKED) {
            if (bounds != null) {
                lockedBoundsState.value = bounds
                framesWithoutTarget = 0
            } else {
                framesWithoutTarget++
                if (framesWithoutTarget > 25) {
                    speak("Bersaglio perso")
                    lockedBoundsState.value = null
                    workflowState = WorkflowState.SEARCHING
                    framesWithoutTarget = 0
                }
            }
        }
    }

    private fun playCameraFocusSound() {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = sampleRate / 15
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    samples[i] = (sin(2.0 * Math.PI * 1046.5 * i / sampleRate) * 6000).toInt().toShort()
                }
                playAudioSamples(samples)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun playCameraLockSound() {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = sampleRate / 5
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = if ((i / (sampleRate / 20)) % 2 == 0) 1200.0 else 1800.0
                    samples[i] = (sin(2.0 * Math.PI * freq * t) * 16383).toInt().toShort()
                }
                playAudioSamples(samples)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun playMissileLaunchSound() {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = (sampleRate * 0.35).toInt()
                val samples = ShortArray(numSamples)
                val random = java.util.Random()
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val freq = 300.0 + (i * 4.0)
                    val noise = (random.nextDouble() * 2.0 - 1.0) * 0.4
                    val whoosh = sin(2.0 * Math.PI * freq * t) * 0.6
                    val envelope = sin(Math.PI * (i.toDouble() / numSamples))
                    samples[i] = ((whoosh + noise) * 32767 * envelope).toInt().toShort()
                }
                playAudioSamples(samples)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun playBeepSound(freq: Double, durationMs: Int) {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = (sampleRate * durationMs) / 1000
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    samples[i] = (sin(2.0 * Math.PI * freq * t) * 16383).toInt().toShort()
                }
                playAudioSamples(samples)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun playCinematicExplosionSound() {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = (sampleRate * 0.7).toInt()
                val samples = ShortArray(numSamples)
                val random = java.util.Random()
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val envelope = 1.0 - (i.toDouble() / numSamples)
                    val subBass = sin(2.0 * Math.PI * 45.0 * t) * Math.exp(-t * 2.5)
                    val noise = (random.nextDouble() * 2.0 - 1.0)
                    val crackle = if (random.nextDouble() > 0.94) (random.nextDouble() * 2.0 - 1.0) * 0.9 else 0.0
                    val mixed = (subBass * 0.5 + noise * 0.4 + crackle * 0.1)
                    samples[i] = (mixed * 32767 * envelope).toInt().toShort()
                }
                playAudioSamples(samples)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun playAudioSamples(samples: ShortArray) {
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            audioTrack.write(samples, 0, samples.size)
            audioTrack.play()
            Thread.sleep((samples.size * 1000L / 44100) + 50)
            audioTrack.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        mediaSession.release()
        analysisExecutor.shutdown()
    }
}

@Composable
fun BadGuyARApp(
    currentWeapon: String,
    workflowState: WorkflowState,
    isObjectInCenter: Boolean,
    fireCount: Int,
    detectedBounds: Rect?,
    lockedBounds: Rect?,
    distance: Int,
    speed: Int,
    frameWidth: Float,
    frameHeight: Float,
    onActionTrigger: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? MainActivity

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val objectDetector = remember {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { previewView ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    processImageProxy(objectDetector, imageProxy) { bounds, inCenter, dist, w, h ->
                        activity?.runOnUiThread {
                            activity.updateTargetDetection(bounds, inCenter, dist, w, h)
                        }
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }

        CameraWorkflowHUD(
            currentWeapon = currentWeapon,
            workflowState = workflowState,
            isObjectInCenter = isObjectInCenter,
            fireCount = fireCount,
            detectedBounds = detectedBounds,
            lockedBounds = lockedBounds,
            distance = distance,
            speed = speed,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            onActionTrigger = onActionTrigger,
            onDoubleTap = onDoubleTap
        )
    }
}

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    detector: com.google.mlkit.vision.objects.ObjectDetector,
    imageProxy: ImageProxy,
    onResult: (Rect?, Boolean, Int, Float, Float) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val imgW = if (rotationDegrees == 90 || rotationDegrees == 270) mediaImage.height.toFloat() else mediaImage.width.toFloat()
        val imgH = if (rotationDegrees == 90 || rotationDegrees == 270) mediaImage.width.toFloat() else mediaImage.height.toFloat()

        detector.process(image)
            .addOnSuccessListener { objects ->
                val frameCenterX = imgW / 2f
                val frameCenterY = imgH / 2f
                val centerZoneRadius = max(imgW, imgH) * 0.25f

                if (objects.isNotEmpty()) {
                    val centerObj = objects.minByOrNull { obj ->
                        val bb = obj.boundingBox
                        val dx = bb.centerX().toFloat() - frameCenterX
                        val dy = bb.centerY().toFloat() - frameCenterY
                        (dx * dx + dy * dy)
                    }

                    if (centerObj != null) {
                        val bb = centerObj.boundingBox
                        val rect = Rect(bb.left.toFloat(), bb.top.toFloat(), bb.right.toFloat(), bb.bottom.toFloat())
                        val objCenterX = bb.centerX().toFloat()
                        val objCenterY = bb.centerY().toFloat()

                        val inCenter = abs(objCenterX - frameCenterX) <= centerZoneRadius &&
                                abs(objCenterY - frameCenterY) <= centerZoneRadius

                        val areaRatio = (bb.width() * bb.height()).toFloat() / (imgW * imgH)
                        val estimatedDistance = maxOf(2f, (20f * (1f - areaRatio))).toInt()
                        onResult(rect, inCenter, estimatedDistance, imgW, imgH)
                    } else {
                        onResult(null, false, 12, imgW, imgH)
                    }
                } else {
                    onResult(null, false, 12, imgW, imgH)
                }
            }
            .addOnFailureListener {
                onResult(null, false, 12, imgW, imgH)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@Composable
fun CameraWorkflowHUD(
    currentWeapon: String,
    workflowState: WorkflowState,
    isObjectInCenter: Boolean,
    fireCount: Int,
    detectedBounds: Rect?,
    lockedBounds: Rect?,
    distance: Int,
    speed: Int,
    frameWidth: Float,
    frameHeight: Float,
    onActionTrigger: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val ironManGold = Color(0xFFFFCC00)
    val hudDimGold = Color(0x66FFCC00)
    val hudRed = Color(0xFFFF2222)
    val fireOrange = Color(0xFFFF8800)
    val fireYellow = Color(0xFFFFEE00)

    val infiniteTransition = rememberInfiniteTransition(label = "workflow_hud")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Coil ImageLoader configured for animated GIF playback
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (VERSION.SDK_INT >= VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    val showExplosion = remember { mutableStateOf(false) }
    val missileProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    var targetCenterXPx by remember { mutableFloatStateOf(0f) }
    var targetCenterYPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(fireCount) {
        if (fireCount > 0) {
            missileProgress.snapTo(0f)
            
            // Launch Homing Missiles from bottom corners
            missileProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutLinearInEasing)
            )

            // Missile Impact at 100% -> Show User's New Explosion GIF Asset for 2.2s
            showExplosion.value = true
            scope.launch {
                delay(2200)
                showExplosion.value = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onActionTrigger() },
                    onDoubleTap = { onDoubleTap() }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f

            val scaleX = width / max(1f, frameWidth)
            val scaleY = height / max(1f, frameHeight)

            val currentEpicenterX = if (lockedBounds != null) {
                val b = lockedBounds
                (b.left * scaleX + b.right * scaleX) / 2f
            } else {
                centerX
            }

            val currentEpicenterY = if (lockedBounds != null) {
                val b = lockedBounds
                (b.top * scaleY + b.bottom * scaleY) / 2f
            } else {
                centerY
            }

            targetCenterXPx = currentEpicenterX
            targetCenterYPx = currentEpicenterY

            // --- 1. Iron Man Helmet Outer Corners ---
            val bracketLen = 120f
            val strokeW = 3f
            drawLine(hudDimGold, Offset(50f, 50f), Offset(50f + bracketLen, 50f), strokeW)
            drawLine(hudDimGold, Offset(50f, 50f), Offset(50f, 50f + bracketLen), strokeW)

            drawLine(hudDimGold, Offset(width - 50f, 50f), Offset(width - 50f - bracketLen, 50f), strokeW)
            drawLine(hudDimGold, Offset(width - 50f, 50f), Offset(width - 50f, 50f + bracketLen), strokeW)

            drawLine(hudDimGold, Offset(50f, height - 50f), Offset(50f + bracketLen, height - 50f), strokeW)
            drawLine(hudDimGold, Offset(50f, height - 50f), Offset(50f, height - 50f - bracketLen), strokeW)

            drawLine(hudDimGold, Offset(width - 50f, height - 50f), Offset(width - 50f - bracketLen, height - 50f), strokeW)
            drawLine(hudDimGold, Offset(width - 50f, height - 50f), Offset(width - 50f, height - 50f - bracketLen), strokeW)

            // --- 2. Central Reticle & Space-Movie Object Tracking ---
            when (workflowState) {
                WorkflowState.SEARCHING -> {
                    val reticleW = 160.dp.toPx()
                    val reticleH = 160.dp.toPx()
                    val retLeft = centerX - reticleW / 2f
                    val retTop = centerY - reticleH / 2f
                    val retRight = centerX + reticleW / 2f
                    val retBottom = centerY + reticleH / 2f
                    val cornerLen = 25f

                    val reticleColor = if (isObjectInCenter) hudRed.copy(alpha = pulseAlpha) else ironManGold.copy(alpha = pulseAlpha)

                    drawLine(reticleColor, Offset(retLeft, retTop), Offset(retLeft + cornerLen, retTop), 5f)
                    drawLine(reticleColor, Offset(retLeft, retTop), Offset(retLeft, retTop + cornerLen), 5f)

                    drawLine(reticleColor, Offset(retRight, retTop), Offset(retRight - cornerLen, retTop), 5f)
                    drawLine(reticleColor, Offset(retRight, retTop), Offset(retRight, retTop + cornerLen), 5f)

                    drawLine(reticleColor, Offset(retLeft, retBottom), Offset(retLeft + cornerLen, retBottom), 5f)
                    drawLine(reticleColor, Offset(retLeft, retBottom), Offset(retLeft, retBottom - cornerLen), 5f)

                    drawLine(reticleColor, Offset(retRight, retBottom), Offset(retRight - cornerLen, retBottom), 5f)
                    drawLine(reticleColor, Offset(retRight, retBottom), Offset(retRight, retBottom - cornerLen), 5f)

                    drawCircle(reticleColor, 4f, Offset(centerX, centerY))
                }
                WorkflowState.LOCKED, WorkflowState.FIRING -> {
                    val bounds = lockedBounds ?: Rect(frameWidth * 0.35f, frameHeight * 0.35f, frameWidth * 0.65f, frameHeight * 0.65f)

                    val tLeft = bounds.left * scaleX
                    val tTop = bounds.top * scaleY
                    val tRight = bounds.right * scaleX
                    val tBottom = bounds.bottom * scaleY
                    val cornerSize = 25f

                    val lockColor = hudRed.copy(alpha = pulseAlpha)

                    // Tracked Red Reticle Brackets following object dynamically
                    drawLine(lockColor, Offset(tLeft, tTop), Offset(tLeft + cornerSize, tTop), 6f)
                    drawLine(lockColor, Offset(tLeft, tTop), Offset(tLeft, tTop + cornerSize), 6f)

                    drawLine(lockColor, Offset(tRight, tTop), Offset(tRight - cornerSize, tTop), 6f)
                    drawLine(lockColor, Offset(tRight, tTop), Offset(tRight, tTop + cornerSize), 6f)

                    drawLine(lockColor, Offset(tLeft, tBottom), Offset(tLeft + cornerSize, tBottom), 6f)
                    drawLine(lockColor, Offset(tLeft, tBottom), Offset(tLeft, tBottom - cornerSize), 6f)

                    drawLine(lockColor, Offset(tRight, tBottom), Offset(tRight - cornerSize, tBottom), 6f)
                    drawLine(lockColor, Offset(tRight, tBottom), Offset(tRight, tBottom - cornerSize), 6f)

                    drawLine(lockColor.copy(alpha = 0.3f), Offset(tLeft, (tTop + tBottom) / 2f), Offset(tRight, (tTop + tBottom) / 2f), 1.5f)
                    drawLine(lockColor.copy(alpha = 0.3f), Offset((tLeft + tRight) / 2f, tTop), Offset((tLeft + tRight) / 2f, tBottom), 1.5f)
                }
            }

            // --- 3. DUAL ROCKETS Launching from Bottom Corners ---
            val p = missileProgress.value
            if (p > 0f && p < 1f) {
                val m1StartX = 0f
                val m1StartY = height
                val m1ControlX = currentEpicenterX * 0.3f
                val m1ControlY = height * 0.5f

                val m1X = (1 - p) * (1 - p) * m1StartX + 2 * (1 - p) * p * m1ControlX + p * p * currentEpicenterX
                val m1Y = (1 - p) * (1 - p) * m1StartY + 2 * (1 - p) * p * m1ControlY + p * p * currentEpicenterY

                val m2StartX = width
                val m2StartY = height
                val m2ControlX = width - (width - currentEpicenterX) * 0.3f
                val m2ControlY = height * 0.5f

                val m2X = (1 - p) * (1 - p) * m2StartX + 2 * (1 - p) * p * m2ControlX + p * p * currentEpicenterX
                val m2Y = (1 - p) * (1 - p) * m2StartY + 2 * (1 - p) * p * m2ControlY + p * p * currentEpicenterY

                // Rocket exhaust plumes
                val m1Brush = Brush.linearGradient(
                    colors = listOf(Color.White, fireYellow, fireOrange, Color(0x66333333)),
                    start = Offset(m1X, m1Y),
                    end = Offset(m1StartX, m1StartY)
                )
                val m2Brush = Brush.linearGradient(
                    colors = listOf(Color.White, fireYellow, fireOrange, Color(0x66333333)),
                    start = Offset(m2X, m2Y),
                    end = Offset(m2StartX, m2StartY)
                )

                drawLine(brush = m1Brush, start = Offset(m1StartX, m1StartY), end = Offset(m1X, m1Y), strokeWidth = 14f)
                drawLine(brush = m2Brush, start = Offset(m2StartX, m2StartY), end = Offset(m2X, m2Y), strokeWidth = 14f)

                // Metallic rocket warheads
                drawCircle(color = Color.White, radius = 12f, center = Offset(m1X, m1Y))
                drawCircle(color = hudRed, radius = 20f, center = Offset(m1X, m1Y), style = Stroke(width = 4f))

                drawCircle(color = Color.White, radius = 12f, center = Offset(m2X, m2Y))
                drawCircle(color = hudRed, radius = 20f, center = Offset(m2X, m2Y), style = Stroke(width = 4f))
            }
        }

        // --- 4. RENDER THE USER'S NEW REAL ANIMATED EXPLOSION GIF (R.drawable.explosion) AT IMPACT ---
        if (showExplosion.value) {
            val gifSizeDp = 340.dp
            val halfSizePx = with(density) { gifSizeDp.toPx() / 2f }

            val offsetXDp = with(density) { (targetCenterXPx - halfSizePx).toDp() }
            val offsetYDp = with(density) { (targetCenterYPx - halfSizePx).toDp() }

            Box(
                modifier = Modifier
                    .offset(x = offsetXDp, y = offsetYDp)
                    .size(gifSizeDp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(R.drawable.explosion)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = "User Real Explosion GIF",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // --- 5. HUD Telemetry & Instructions ---
        Column(
            modifier = Modifier
                .padding(70.dp, 35.dp)
                .align(Alignment.TopStart)
        ) {
            Text(
                text = "JARVIS // USER GIF EXPLOSION ENGINE",
                color = hudDimGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = currentWeapon.uppercase(),
                color = ironManGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = when (workflowState) {
                    WorkflowState.SEARCHING -> if (isObjectInCenter) "STATO: [BERSAGLIO AL CENTRO!]" else "STATO: [SCANSIONE CENTRALE...]"
                    WorkflowState.LOCKED -> "STATO: [BERSAGLIO TRACCIATO IN MOVIMENTO]"
                    WorkflowState.FIRING -> "STATO: [LANCIO MISSILI IN CORSO!]"
                },
                color = if (workflowState == WorkflowState.LOCKED || isObjectInCenter) hudRed else ironManGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(
            modifier = Modifier
                .padding(70.dp, 35.dp)
                .align(Alignment.TopEnd),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "VELOCITÀ: $speed KM/H",
                color = ironManGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "NEW USER GIF ASSET: LOADED",
                color = hudDimGold,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(
            modifier = Modifier
                .padding(bottom = 24.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (workflowState) {
                    WorkflowState.SEARCHING -> if (isObjectInCenter) "[ 1° CLICK CUFFIA: LOCKA BERSAGLIO ]" else "PORTA L'OGGETTO NEL MIRINO CENTRALE..."
                    WorkflowState.LOCKED -> "BERSAGLIO TRACCIATO IN MOVIMENTO // DISTANZA: ${distance}M"
                    WorkflowState.FIRING -> "MISSILI IN VOLO DAI MANUBRI..."
                },
                color = if (workflowState == WorkflowState.LOCKED || isObjectInCenter) hudRed else ironManGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (workflowState) {
                    WorkflowState.SEARCHING -> "[ MIRINO ROSSO = BERSAGLIO NELL'AREA ]"
                    WorkflowState.LOCKED -> "[ 1° CLICK CUFFIA = LANCIA MISSILI ] | [ DOPPIO TAP = SBLOCCA ]"
                    WorkflowState.FIRING -> "ESPLOSIONE NUOVA GIF REALE IN CORSO..."
                },
                color = hudDimGold,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
