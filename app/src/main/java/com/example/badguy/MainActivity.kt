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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var isTtsReady = false
    private lateinit var mediaSession: MediaSession

    // Weapons list
    private val weapons = listOf("01: Ion Blaster", "02: Plasma Cannon", "03: Photon Torpedo", "04: EMP Disruptor")
    private var currentWeaponIndex by mutableStateOf(0)

    // Game state triggers shared with Compose
    var fireEventCount = mutableStateOf(0)
        private set

    var targetLockedState = mutableStateOf(false)
        private set

    var targetBoundsState = mutableStateOf<Rect?>(null)
        private set

    var targetDistance = mutableStateOf(42)
        private set

    var speedKmH = mutableStateOf(28)
        private set

    private var lastSpokenTargetId = -1
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
                                runOnUiThread { triggerFire() }
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
                fireCount = fireEventCount.value,
                targetLocked = targetLockedState.value,
                targetBounds = targetBoundsState.value,
                distance = targetDistance.value,
                speed = speedKmH.value,
                onScreenTap = { triggerFire() }
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
        playBeepSound()
    }

    fun triggerFire() {
        fireEventCount.value++
        playExplosionSound()
        speak("Bersaglio colpito!")
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
                triggerFire()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    fun updateTargetDetection(hasTarget: Boolean, bounds: Rect?, trackingId: Int?) {
        targetLockedState.value = hasTarget
        targetBoundsState.value = bounds

        if (hasTarget && trackingId != null && trackingId != lastSpokenTargetId) {
            lastSpokenTargetId = trackingId
            speak("Bersaglio acquisito")
        }
    }

    private fun playExplosionSound() {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = sampleRate / 2 // ~500ms explosion noise + rumble
                val samples = ShortArray(numSamples)
                val random = java.util.Random()
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    val envelope = 1.0 - (i.toDouble() / numSamples)
                    val noise = (random.nextDouble() * 2.0 - 1.0)
                    val rumble = sin(2.0 * Math.PI * 60.0 * t) * 0.5
                    samples[i] = ((noise * 0.7 + rumble * 0.3) * 32767 * envelope).toInt().toShort()
                }
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
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
                Thread.sleep(550)
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun playBeepSound() {
        Thread {
            try {
                val sampleRate = 44100
                val numSamples = sampleRate / 10
                val samples = ShortArray(numSamples)
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    samples[i] = (sin(2.0 * Math.PI * 523.25 * t) * 16383).toInt().toShort()
                }
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
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
                Thread.sleep(120)
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
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
    fireCount: Int,
    targetLocked: Boolean,
    targetBounds: Rect?,
    distance: Int,
    speed: Int,
    onScreenTap: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? MainActivity

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val objectDetector = remember {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()
        ObjectDetection.getClient(options)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Camera Feed with Object Analysis
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
                    processImageProxy(objectDetector, imageProxy) { hasTarget, bounds, trackingId ->
                        activity?.runOnUiThread {
                            activity.updateTargetDetection(hasTarget, bounds, trackingId)
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

        // 2. Advanced Military HUD & Real Fire Explosions
        MilitaryHUDOverlay(
            currentWeapon = currentWeapon,
            fireCount = fireCount,
            targetLocked = targetLocked,
            targetBounds = targetBounds,
            distance = distance,
            speed = speed,
            onScreenTap = onScreenTap
        )
    }
}

@Suppress("DEPRECATION")
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImageProxy(
    detector: com.google.mlkit.vision.objects.ObjectDetector,
    imageProxy: ImageProxy,
    onResult: (Boolean, Rect?, Int?) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        detector.process(image)
            .addOnSuccessListener { objects ->
                if (objects.isNotEmpty()) {
                    val obj = objects[0]
                    val bb = obj.boundingBox
                    val rect = Rect(bb.left.toFloat(), bb.top.toFloat(), bb.right.toFloat(), bb.bottom.toFloat())
                    onResult(true, rect, obj.trackingId)
                } else {
                    onResult(false, null, null)
                }
            }
            .addOnFailureListener {
                onResult(false, null, null)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@Composable
fun MilitaryHUDOverlay(
    currentWeapon: String,
    fireCount: Int,
    targetLocked: Boolean,
    targetBounds: Rect?,
    distance: Int,
    speed: Int,
    onScreenTap: () -> Unit
) {
    val neonGreen = Color(0xFF00FF66)
    val hudDimGreen = Color(0x8800FF66)
    val hudRed = Color(0xFFFF3333)
    val fireOrange = Color(0xFFFF9900)
    val fireYellow = Color(0xFFFFEE00)

    val infiniteTransition = rememberInfiniteTransition(label = "hud")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Advanced Fire & Particle Explosion state
    var explosionParticles by remember { mutableStateOf(listOf<FireParticle>()) }
    var shockwaveRadius by remember { mutableStateOf(0f) }
    var showExplosionFlash by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(fireCount) {
        if (fireCount > 0) {
            showExplosionFlash = true
            shockwaveRadius = 20f
            
            val newParticles = (0..60).map {
                FireParticle(
                    angle = Random.nextDouble(0.0, 2.0 * Math.PI),
                    speed = Random.nextFloat() * 25f + 8f,
                    size = Random.nextFloat() * 10f + 3f,
                    color = listOf(fireOrange, fireYellow, hudRed, Color.White).random()
                )
            }
            explosionParticles = newParticles

            scope.launch {
                repeat(15) {
                    delay(30)
                    shockwaveRadius += 15f
                }
                showExplosionFlash = false
                delay(200)
                explosionParticles = emptyList()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onScreenTap)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            val centerY = height / 2f

            // --- 1. Starfighter Crosshair / Reticle ---
            val reticleRadius = 60.dp.toPx() * pulseScale
            drawCircle(
                color = hudDimGreen,
                radius = reticleRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2f)
            )

            drawLine(neonGreen, Offset(centerX - reticleRadius - 15f, centerY), Offset(centerX - reticleRadius + 10f, centerY), 3f)
            drawLine(neonGreen, Offset(centerX + reticleRadius - 10f, centerY), Offset(centerX + reticleRadius + 15f, centerY), 3f)
            drawLine(neonGreen, Offset(centerX, centerY - reticleRadius - 15f), Offset(centerX, centerY - reticleRadius + 10f), 3f)
            drawLine(neonGreen, Offset(centerX, centerY + reticleRadius - 10f), Offset(centerX, centerY + reticleRadius + 15f), 3f)

            drawCircle(neonGreen, 3f, Offset(centerX, centerY))

            // --- 2. Dynamic Tracking Target Box (Following recognized shape) ---
            if (targetLocked && targetBounds != null) {
                val scaleX = width / 1080f
                val scaleY = height / 1920f
                val tLeft = targetBounds.left * scaleX
                val tTop = targetBounds.top * scaleY
                val tRight = targetBounds.right * scaleX
                val tBottom = targetBounds.bottom * scaleY
                val cornerLen = 20f

                val boxColor = hudRed
                drawLine(boxColor, Offset(tLeft, tTop), Offset(tLeft + cornerLen, tTop), 4f)
                drawLine(boxColor, Offset(tLeft, tTop), Offset(tLeft, tTop + cornerLen), 4f)

                drawLine(boxColor, Offset(tRight, tTop), Offset(tRight - cornerLen, tTop), 4f)
                drawLine(boxColor, Offset(tRight, tTop), Offset(tRight, tTop + cornerLen), 4f)

                drawLine(boxColor, Offset(tLeft, tBottom), Offset(tLeft + cornerLen, tBottom), 4f)
                drawLine(boxColor, Offset(tLeft, tBottom), Offset(tLeft, tBottom - cornerLen), 4f)

                drawLine(boxColor, Offset(tRight, tBottom), Offset(tRight - cornerLen, tBottom), 4f)
                drawLine(boxColor, Offset(tRight, tBottom), Offset(tRight, tBottom - cornerLen), 4f)
            } else {
                val targetBoxSize = 120.dp.toPx()
                val tLeft = centerX - targetBoxSize / 2f
                val tTop = centerY - targetBoxSize / 2f
                val cornerLen = 25f
                val boxColor = neonGreen

                drawLine(boxColor, Offset(tLeft, tTop), Offset(tLeft + cornerLen, tTop), 4f)
                drawLine(boxColor, Offset(tLeft, tTop), Offset(tLeft, tTop + cornerLen), 4f)
                drawLine(boxColor, Offset(tLeft + targetBoxSize, tTop), Offset(tLeft + targetBoxSize - cornerLen, tTop), 4f)
                drawLine(boxColor, Offset(tLeft + targetBoxSize, tTop), Offset(tLeft + targetBoxSize, tTop + cornerLen), 4f)
                drawLine(boxColor, Offset(tLeft, tTop + targetBoxSize), Offset(tLeft + cornerLen, tTop + targetBoxSize), 4f)
                drawLine(boxColor, Offset(tLeft, tTop + targetBoxSize), Offset(tLeft, tTop + targetBoxSize - cornerLen), 4f)
                drawLine(boxColor, Offset(tLeft + targetBoxSize, tTop + targetBoxSize), Offset(tLeft + targetBoxSize - cornerLen, tTop + targetBoxSize), 4f)
                drawLine(boxColor, Offset(tLeft + targetBoxSize, tTop + targetBoxSize), Offset(tLeft + targetBoxSize, tTop + targetBoxSize - cornerLen), 4f)
            }

            // Screen corner military brackets
            val bracketLen = 110f
            drawLine(hudDimGreen, Offset(40f, 40f), Offset(40f + bracketLen, 40f), 2f)
            drawLine(hudDimGreen, Offset(40f, 40f), Offset(40f, 40f + bracketLen), 2f)

            drawLine(hudDimGreen, Offset(width - 40f, 40f), Offset(width - 40f - bracketLen, 40f), 2f)
            drawLine(hudDimGreen, Offset(width - 40f, 40f), Offset(width - 40f, 40f + bracketLen), 2f)

            drawLine(hudDimGreen, Offset(40f, height - 40f), Offset(40f + bracketLen, height - 40f), 2f)
            drawLine(hudDimGreen, Offset(40f, height - 40f), Offset(40f, height - 40f - bracketLen), 2f)

            drawLine(hudDimGreen, Offset(width - 40f, height - 40f), Offset(width - 40f - bracketLen, height - 40f), 2f)
            drawLine(hudDimGreen, Offset(width - 40f, height - 40f), Offset(width - 40f, height - 40f - bracketLen), 2f)

            // --- 3. Advanced Realistic Fire & Particle Explosions ---
            if (showExplosionFlash) {
                drawCircle(color = Color.White, radius = 45f, center = Offset(centerX, centerY))
                drawCircle(color = fireOrange, radius = shockwaveRadius, center = Offset(centerX, centerY), style = Stroke(width = 6f))
                drawCircle(color = fireYellow, radius = shockwaveRadius * 0.7f, center = Offset(centerX, centerY), style = Stroke(width = 3f))
            }

            explosionParticles.forEach { p ->
                val dist = p.speed * 30f
                val px = centerX + (cos(p.angle) * dist).toFloat()
                val py = centerY + (sin(p.angle) * dist).toFloat()
                drawCircle(color = p.color, radius = p.size, center = Offset(px, py))
            }
        }

        // --- 4. HUD Telemetry & Text Overlays ---
        Column(
            modifier = Modifier
                .padding(60.dp, 30.dp)
                .align(Alignment.TopStart)
        ) {
            Text(
                text = "SISTEMA D'ARMA",
                color = hudDimGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = currentWeapon.uppercase(),
                color = neonGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "STATO: [ONLINE - PRONTO]",
                color = neonGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Column(
            modifier = Modifier
                .padding(60.dp, 30.dp)
                .align(Alignment.TopEnd),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "VELOCITÀ: $speed KM/H",
                color = neonGreen,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "SCUDI: 100% | SCAFO: 100%",
                color = hudDimGreen,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "AI OBJECT TRACKING: ATTIVO",
                color = neonGreen,
                fontSize = 10.sp,
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
                text = if (targetLocked) "BERSAGLIO ACQUISITO // DISTANZA: ${distance}M" else "RICERCA BERSAGLI IN CORSO...",
                color = if (targetLocked) hudRed else neonGreen,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "[CUFFIE PLAY/PAUSE = SPARO] | [VOLUME +/- = CAMBIO ARMA]",
                color = hudDimGreen,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

data class FireParticle(
    val angle: Double,
    val speed: Float,
    val size: Float,
    val color: Color
)
