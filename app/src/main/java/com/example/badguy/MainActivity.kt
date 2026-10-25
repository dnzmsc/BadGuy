package com.example.badguy

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.session.MediaSession
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class WorkflowState {
    SEARCHING,
    LOCKED,
    FIRING
}

data class CinematicSpark(
    val angle: Double,
    val speed: Float,
    val size: Float,
    val color: Color
)

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
                fireCount = fireEventCount.value,
                detectedBounds = currentDetectedBounds.value,
                lockedBounds = lockedBoundsState.value,
                distance = targetDistance.value,
                speed = speedKmH.value,
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

    /**
     * Action Trigger (Headphone Play/Pause button or screen tap):
     * 1. SEARCHING: Locks onto the live detected object in the aiming zone ("Bersaglio tracciato").
     * 2. LOCKED: Fires missiles at the dynamically tracked target ("Bersaglio distrutto!").
     */
    fun handleActionTrigger() {
        when (workflowState) {
            WorkflowState.SEARCHING -> {
                val targetToLock = currentDetectedBounds.value ?: Rect(280f, 550f, 800f, 1370f)
                lockedBoundsState.value = targetToLock
                workflowState = WorkflowState.LOCKED
                framesWithoutTarget = 0
                playCameraLockSound()
                speak("Bersaglio tracciato")
            }
            WorkflowState.LOCKED -> {
                workflowState = WorkflowState.FIRING
                fireEventCount.value++
                playCinematicExplosionSound()
                speak("Bersaglio distrutto!")
                android.os.Handler(mainLooper).postDelayed({
                    lockedBoundsState.value = null
                    workflowState = WorkflowState.SEARCHING
                }, 800)
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

    fun updateTargetDetection(bounds: Rect?, distanceMeters: Int) {
        currentDetectedBounds.value = bounds
        targetDistance.value = distanceMeters

        // Dynamic frame-by-frame target tracking in LOCKED state
        if (workflowState == WorkflowState.LOCKED) {
            if (bounds != null) {
                // Update locked bounds to follow the target across the screen as the phone/camera moves!
                lockedBoundsState.value = bounds
                framesWithoutTarget = 0
            } else {
                framesWithoutTarget++
                if (framesWithoutTarget > 25) { // ~0.8s without target
                    speak("Bersaglio perso")
                    lockedBoundsState.value = null
                    workflowState = WorkflowState.SEARCHING
                    framesWithoutTarget = 0
                }
            }
        }
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
                    val subBass = sin(2.0 * Math.PI * 50.0 * t) * Math.exp(-t * 3.0)
                    val noise = (random.nextDouble() * 2.0 - 1.0)
                    val crackle = if (random.nextDouble() > 0.95) (random.nextDouble() * 2.0 - 1.0) * 0.8 else 0.0
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
    fireCount: Int,
    detectedBounds: Rect?,
    lockedBounds: Rect?,
    distance: Int,
    speed: Int,
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
                    processImageProxy(objectDetector, imageProxy) { bounds, dist ->
                        activity?.runOnUiThread {
                            activity.updateTargetDetection(bounds, dist)
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
            fireCount = fireCount,
            detectedBounds = detectedBounds,
            lockedBounds = lockedBounds,
            distance = distance,
            speed = speed,
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
    onResult: (Rect?, Int) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { objects ->
                val frameCenterX = mediaImage.width / 2f
                val frameCenterY = mediaImage.height / 2f

                if (objects.isNotEmpty()) {
                    // Find object closest to central camera aiming zone
                    val centerObj = objects.minByOrNull { obj ->
                        val bb = obj.boundingBox
                        val dx = bb.centerX().toFloat() - frameCenterX
                        val dy = bb.centerY().toFloat() - frameCenterY
                        (dx * dx + dy * dy)
                    }

                    if (centerObj != null) {
                        val bb = centerObj.boundingBox
                        val rect = Rect(bb.left.toFloat(), bb.top.toFloat(), bb.right.toFloat(), bb.bottom.toFloat())
                        val areaRatio = (bb.width() * bb.height()).toFloat() / (mediaImage.width * mediaImage.height).toFloat()
                        val estimatedDistance = maxOf(2f, (20f * (1f - areaRatio))).toInt()
                        onResult(rect, estimatedDistance)
                    } else {
                        onResult(null, 12)
                    }
                } else {
                    onResult(null, 12)
                }
            }
            .addOnFailureListener {
                onResult(null, 12)
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
    fireCount: Int,
    detectedBounds: Rect?,
    lockedBounds: Rect?,
    distance: Int,
    speed: Int,
    onActionTrigger: () -> Unit,
    onDoubleTap: () -> Unit
) {
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

    val explosionParticles = remember { mutableStateOf(listOf<CinematicSpark>()) }
    val shockwaveRadius = remember { mutableStateOf(0f) }
    val showExplosion = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(fireCount) {
        if (fireCount > 0) {
            showExplosion.value = true
            shockwaveRadius.value = 10f

            val sparks = (0..90).map {
                CinematicSpark(
                    angle = Random.nextDouble(0.0, 2.0 * Math.PI),
                    speed = Random.nextFloat() * 30f + 10f,
                    size = Random.nextFloat() * 12f + 4f,
                    color = listOf(fireOrange, fireYellow, hudRed, Color.White, ironManGold).random()
                )
            }
            explosionParticles.value = sparks

            scope.launch {
                repeat(20) {
                    delay(25)
                    shockwaveRadius.value += 18f
                }
                showExplosion.value = false
                delay(250)
                explosionParticles.value = emptyList()
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

            // --- 1. Iron Man Helmet Corners ---
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

            // --- 2. Live Dynamic Target Highlights & Locked Tracking ---
            when (workflowState) {
                WorkflowState.SEARCHING -> {
                    // Central aiming reticle
                    drawCircle(color = hudDimGold, radius = 80.dp.toPx(), center = Offset(centerX, centerY), style = Stroke(width = 1.5f))
                    drawCircle(color = ironManGold.copy(alpha = pulseAlpha), radius = 6.dp.toPx(), center = Offset(centerX, centerY))

                    // Prominent visual highlight when an object is detected in the central zone
                    if (detectedBounds != null) {
                        val scaleX = width / 1080f
                        val scaleY = height / 1920f
                        val dLeft = detectedBounds.left * scaleX
                        val dTop = detectedBounds.top * scaleY
                        val dRight = detectedBounds.right * scaleX
                        val dBottom = detectedBounds.bottom * scaleY
                        val cornerSize = 25f
                        val highlightColor = ironManGold.copy(alpha = pulseAlpha)

                        drawLine(highlightColor, Offset(dLeft, dTop), Offset(dLeft + cornerSize, dTop), 5f)
                        drawLine(highlightColor, Offset(dLeft, dTop), Offset(dLeft, dTop + cornerSize), 5f)
                        drawLine(highlightColor, Offset(dRight, dTop), Offset(dRight - cornerSize, dTop), 5f)
                        drawLine(highlightColor, Offset(dRight, dTop), Offset(dRight, dTop + cornerSize), 5f)
                        drawLine(highlightColor, Offset(dLeft, dBottom), Offset(dLeft + cornerSize, dBottom), 5f)
                        drawLine(highlightColor, Offset(dLeft, dBottom), Offset(dLeft, dBottom - cornerSize), 5f)
                        drawLine(highlightColor, Offset(dRight, dBottom), Offset(dRight - cornerSize, dBottom), 5f)
                        drawLine(highlightColor, Offset(dRight, dBottom), Offset(dRight, dBottom - cornerSize), 5f)
                    }
                }
                WorkflowState.LOCKED, WorkflowState.FIRING -> {
                    // LIVE DYNAMIC TRACKING BOX (Updates frame-by-frame as phone/camera moves!)
                    val bounds = lockedBounds ?: Rect(centerX - 100f, centerY - 100f, centerX + 100f, centerY + 100f)
                    val scaleX = width / 1080f
                    val scaleY = height / 1920f
                    val tLeft = bounds.left * scaleX
                    val tTop = bounds.top * scaleY
                    val tRight = bounds.right * scaleX
                    val tBottom = bounds.bottom * scaleY
                    val cornerSize = 25f

                    val lockColor = hudRed.copy(alpha = pulseAlpha)

                    // Bright red Iron Man tracking brackets moving dynamically across the screen
                    drawLine(lockColor, Offset(tLeft, tTop), Offset(tLeft + cornerSize, tTop), 6f)
                    drawLine(lockColor, Offset(tLeft, tTop), Offset(tLeft, tTop + cornerSize), 6f)

                    drawLine(lockColor, Offset(tRight, tTop), Offset(tRight - cornerSize, tTop), 6f)
                    drawLine(lockColor, Offset(tRight, tTop), Offset(tRight, tTop + cornerSize), 6f)

                    drawLine(lockColor, Offset(tLeft, tBottom), Offset(tLeft + cornerSize, tBottom), 6f)
                    drawLine(lockColor, Offset(tLeft, tBottom), Offset(tLeft, tBottom - cornerSize), 6f)

                    drawLine(lockColor, Offset(tRight, tBottom), Offset(tRight - cornerSize, tBottom), 6f)
                    drawLine(lockColor, Offset(tRight, tBottom), Offset(tRight, tBottom - cornerSize), 6f)

                    drawLine(lockColor.copy(alpha = 0.3f), Offset(tLeft, (tTop + tBottom) / 2f), Offset(tRight, (tTop + tBottom) / 2f), 1f)
                    drawLine(lockColor.copy(alpha = 0.3f), Offset((tLeft + tRight) / 2f, tTop), Offset((tLeft + tRight) / 2f, tBottom), 1f)
                }
            }

            // --- 3. Cinematic Explosions AT LIVE TRACKED TARGET EPICENTER ---
            val epicenterX = if (lockedBounds != null) {
                val scaleX = width / 1080f
                (lockedBounds.left * scaleX + lockedBounds.right * scaleX) / 2f
            } else {
                centerX
            }

            val epicenterY = if (lockedBounds != null) {
                val scaleY = height / 1920f
                (lockedBounds.top * scaleY + lockedBounds.bottom * scaleY) / 2f
            } else {
                centerY
            }

            if (showExplosion.value) {
                drawCircle(color = Color.White, radius = 60f, center = Offset(epicenterX, epicenterY))
                drawCircle(color = fireOrange, radius = shockwaveRadius.value, center = Offset(epicenterX, epicenterY), style = Stroke(width = 8f))
                drawCircle(color = fireYellow, radius = shockwaveRadius.value * 0.7f, center = Offset(epicenterX, epicenterY), style = Stroke(width = 4f))
            }

            explosionParticles.value.forEach { p ->
                val dist = p.speed * 35f
                val px = epicenterX + (cos(p.angle) * dist).toFloat()
                val py = epicenterY + (sin(p.angle) * dist).toFloat()
                drawCircle(color = p.color, radius = p.size, center = Offset(px, py))
            }
        }

        // --- 4. HUD Telemetry & Instructions ---
        Column(
            modifier = Modifier
                .padding(70.dp, 35.dp)
                .align(Alignment.TopStart)
        ) {
            Text(
                text = "JARVIS // LIVE TARGET TRACKING",
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
                    WorkflowState.SEARCHING -> if (detectedBounds != null) "STATO: [BERSAGLIO RILEVATO]" else "STATO: [SCANSIONE IN CORSO...]"
                    WorkflowState.LOCKED -> "STATO: [BERSAGLIO TRACCIATO IN MOVIMENTO]"
                    WorkflowState.FIRING -> "STATO: [FUOCO!]"
                },
                color = if (workflowState == WorkflowState.LOCKED) hudRed else ironManGold,
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
                text = "AI STREAM TRACKER: ATTIVO",
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
                    WorkflowState.SEARCHING -> if (detectedBounds != null) "[ 1° CLICK CUFFIE: LOCKA IL BERSAGLIO RILEVATO ]" else "INQUADRA UN BERSAGLIO AL CENTRO..."
                    WorkflowState.LOCKED -> "BERSAGLIO TRACCIATO IN MOVIMENTO // DISTANZA: ${distance}M"
                    WorkflowState.FIRING -> "ESPLOSIONE SUL BERSAGLIO TRACCIATO..."
                },
                color = if (workflowState == WorkflowState.LOCKED) hudRed else ironManGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (workflowState) {
                    WorkflowState.SEARCHING -> "[ 2° CLICK O DOPPIO TAP = SBLOCCA ]"
                    WorkflowState.LOCKED -> "[ 1° CLICK CUFFIA = SPARO ] | [ DOPPIO TAP = SBLOCCA ]"
                    WorkflowState.FIRING -> "MISSILE IMPATTATO..."
                },
                color = hudDimGold,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
