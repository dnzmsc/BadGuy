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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
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

    var targetDistance = mutableStateOf(10)
        private set

    var speedKmH = mutableStateOf(24)
        private set

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
     * 1st Trigger: Locks the Central Selection Rectangle on the object in view ("Bersaglio tracciato").
     * 2nd Trigger: Fires missiles, detonating directly inside the Central Selection Rectangle ("Bersaglio distrutto!").
     */
    fun handleActionTrigger() {
        when (workflowState) {
            WorkflowState.SEARCHING -> {
                workflowState = WorkflowState.LOCKED
                playCameraLockSound()
                speak("Bersaglio tracciato")
            }
            WorkflowState.LOCKED -> {
                workflowState = WorkflowState.FIRING
                fireEventCount.value++
                playCinematicExplosionSound()
                speak("Bersaglio distrutto!")
                android.os.Handler(mainLooper).postDelayed({
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
    }
}

@Composable
fun BadGuyARApp(
    currentWeapon: String,
    workflowState: WorkflowState,
    fireCount: Int,
    distance: Int,
    speed: Int,
    onActionTrigger: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

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

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (exc: Exception) {
                    exc.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }

        CentralSelectionHUD(
            currentWeapon = currentWeapon,
            workflowState = workflowState,
            fireCount = fireCount,
            distance = distance,
            speed = speed,
            onActionTrigger = onActionTrigger,
            onDoubleTap = onDoubleTap
        )
    }
}

@Composable
fun CentralSelectionHUD(
    currentWeapon: String,
    workflowState: WorkflowState,
    fireCount: Int,
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

    val infiniteTransition = rememberInfiniteTransition(label = "central_selection_hud")
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

            // --- 2. Central Selection Rectangle (Exclusively Fixed at Center) ---
            val rectW = 180.dp.toPx()
            val rectH = 180.dp.toPx()
            val tLeft = centerX - rectW / 2f
            val tTop = centerY - rectH / 2f
            val tRight = centerX + rectW / 2f
            val tBottom = centerY + rectH / 2f
            val cornerLen = 30f

            when (workflowState) {
                WorkflowState.SEARCHING -> {
                    // Central Selection Box in Glowing Gold
                    val boxColor = ironManGold.copy(alpha = pulseAlpha)

                    drawLine(boxColor, Offset(tLeft, tTop), Offset(tLeft + cornerLen, tTop), 5f)
                    drawLine(boxColor, Offset(tLeft, tTop), Offset(tLeft, tTop + cornerLen), 5f)

                    drawLine(boxColor, Offset(tRight, tTop), Offset(tRight - cornerLen, tTop), 5f)
                    drawLine(boxColor, Offset(tRight, tTop), Offset(tRight, tTop + cornerLen), 5f)

                    drawLine(boxColor, Offset(tLeft, tBottom), Offset(tLeft + cornerLen, tBottom), 5f)
                    drawLine(boxColor, Offset(tLeft, tBottom), Offset(tLeft, tBottom - cornerLen), 5f)

                    drawLine(boxColor, Offset(tRight, tBottom), Offset(tRight - cornerLen, tBottom), 5f)
                    drawLine(boxColor, Offset(tRight, tBottom), Offset(tRight, tBottom - cornerLen), 5f)

                    // Reticle center dot
                    drawCircle(ironManGold, 4f, Offset(centerX, centerY))
                }
                WorkflowState.LOCKED, WorkflowState.FIRING -> {
                    // Locked Central Selection Box in Bright Red
                    val lockColor = hudRed.copy(alpha = pulseAlpha)

                    drawLine(lockColor, Offset(tLeft, tTop), Offset(tLeft + cornerLen, tTop), 7f)
                    drawLine(lockColor, Offset(tLeft, tTop), Offset(tLeft, tTop + cornerLen), 7f)

                    drawLine(lockColor, Offset(tRight, tTop), Offset(tRight - cornerLen, tTop), 7f)
                    drawLine(lockColor, Offset(tRight, tTop), Offset(tRight, tTop + cornerLen), 7f)

                    drawLine(lockColor, Offset(tLeft, tBottom), Offset(tLeft + cornerLen, tBottom), 7f)
                    drawLine(lockColor, Offset(tLeft, tBottom), Offset(tLeft, tBottom - cornerLen), 7f)

                    drawLine(lockColor, Offset(tRight, tBottom), Offset(tRight - cornerLen, tBottom), 7f)
                    drawLine(lockColor, Offset(tRight, tBottom), Offset(tRight, tBottom - cornerLen), 7f)

                    // Target crosshairs inside Central Selection Box
                    drawLine(lockColor.copy(alpha = 0.4f), Offset(tLeft, centerY), Offset(tRight, centerY), 2f)
                    drawLine(lockColor.copy(alpha = 0.4f), Offset(centerX, tTop), Offset(centerX, tBottom), 2f)
                }
            }

            // --- 3. Cinematic Fire & Spark Explosions DIRECTLY INSIDE CENTRAL SELECTION RECTANGLE ---
            if (showExplosion.value) {
                drawCircle(color = Color.White, radius = 60f, center = Offset(centerX, centerY))
                drawCircle(color = fireOrange, radius = shockwaveRadius.value, center = Offset(centerX, centerY), style = Stroke(width = 8f))
                drawCircle(color = fireYellow, radius = shockwaveRadius.value * 0.7f, center = Offset(centerX, centerY), style = Stroke(width = 4f))
            }

            explosionParticles.value.forEach { p ->
                val dist = p.speed * 35f
                val px = centerX + (cos(p.angle) * dist).toFloat()
                val py = centerY + (sin(p.angle) * dist).toFloat()
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
                text = "JARVIS // CENTRAL TARGETING",
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
                    WorkflowState.SEARCHING -> "STATO: [INQUADRA AL CENTRO]"
                    WorkflowState.LOCKED -> "STATO: [BERSAGLIO TRACCIATO]"
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
                text = "AREA SELEZIONE: CENTRO SCHERMO",
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
                    WorkflowState.SEARCHING -> "INQUADRA L'OGGETTO O LA PERSONA DENTRO IL RETTANGOLO CENTRALE"
                    WorkflowState.LOCKED -> "BERSAGLIO CENTRALE TRACCIATO // DISTANZA: ${distance}M"
                    WorkflowState.FIRING -> "ESPLOSIONE NEL RETTANGOLO CENTRALE IN CORSO..."
                },
                color = if (workflowState == WorkflowState.LOCKED) hudRed else ironManGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (workflowState) {
                    WorkflowState.SEARCHING -> "[ 1° CLICK CUFFIA O TAP = LOCKA IL RETTANGOLO CENTRALE ]"
                    WorkflowState.LOCKED -> "[ 2° CLICK CUFFIA O TAP = SPARO ] | [ DOPPIO TAP = SBLOCCA ]"
                    WorkflowState.FIRING -> "MISSILE IMPATTATO..."
                },
                color = hudDimGold,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
