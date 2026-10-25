extends Node2D
# AI (Kilo) - CAMMO 25/10/2026 - BadGuy: arcade AR in bicicletta, HUD vettoriale, armi, TTS, input cuffie Android

# --- Costanti tasti hardware cuffie Android (Media Keys) ---
const KEY_MEDIA_PLAY_PAUSE: int = KEY_MEDIA_PLAY_PAUSE
const KEY_VOLUME_UP: int = KEY_VOLUME_UP
const KEY_VOLUME_DOWN: int = KEY_VOLUME_DOWN

# --- Nodi ---
@onready var camera_feed: TextureRect = $CameraFeed
@onready var target_box: ColorRect = $HUD/TargetBox
@onready var reticle: ColorRect = $HUD/Reticle
@onready var laser_beam: Line2D = $HUD/LaserBeam
@onready var explosion: CPUParticles2D = $HUD/ExplosionParticles
@onready var simulated_target: ColorRect = $SimulatedTarget
@onready var audio_shoot: AudioStreamPlayer = $Audio/Shoot
@onready var audio_explosion: AudioStreamPlayer = $Audio/Explosion
@onready var audio_lock: AudioStreamPlayer = $Audio/LockOn
@onready var tts_node: Node = $TTS

# --- Stato armi ---
var weapons: Array = []
var current_weapon_index: int = 0

# --- Stato target / lock-on ---
var target_locked: bool = false
var lock_sound_playing: bool = false
var lock_pitch: float = 1.0

func _ready() -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Inizializzazione scena principale
	get_viewport().size = Vector2i(1080, 1920)

	# Definizione armi disponibili (minimo 3 come da specifiche)
	weapons = [
		{"name": "Cannone a Ioni", "color": Color(0, 1, 0, 1), "particle_color": Color(0, 1, 0, 1)},
		{"name": "Laser a Impulsi", "color": Color(1, 0.5, 0, 1), "particle_color": Color(1, 0.5, 0, 1)},
		{"name": "Siluri Protonici", "color": Color(0, 0.5, 1, 1), "particle_color": Color(0, 0.5, 1, 1)},
	]

	# Carica audio da disco se presenti
	_load_audio()

	# Configura sistema particelle esplosione
	_setup_explosion_particles()

	# Posiziona reticle e target box al centro dello schermo
	var center: Vector2 = get_viewport_rect().size / 2
	reticle.position = center
	target_box.position = center

	# Posiziona simulatore target al centro
	if simulated_target:
		simulated_target.position = center

	# Avviso vocale avvio sistema
	if tts_node:
		tts_node.speak("Sistema BadGuy attivato")

	print("BadGuy pronto. Arma attiva: ", weapons[current_weapon_index]["name"])

func _load_audio() -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Carica stream audio da res://assets/audio/ se esistono
	var audio_dir: String = "res://assets/audio/"
	var players: Dictionary = {
		"shoot": audio_shoot,
		"explosion": audio_explosion,
		"lock_on": audio_lock,
	}
	for key in players:
		var path: String = audio_dir + key + ".wav"
		if FileAccess.file_exists(path):
			players[key].stream = load(path)
		else:
			print("Audio mancante (usa placeholder silenzioso): ", path)

func _setup_explosion_particles() -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Configura CPUParticles2D per esplosione bersaglio
	var process_mat: ParticlesProcessMaterial = ParticlesProcessMaterial.new()
	process_mat.lifetime = 0.8
	process_mat.lifetime_randomness = 0.4
	process_mat.emission_shape = ParticlesProcessMaterial.EMISSION_SHAPE_SPHERE
	process_mat.scale = 1.0
	process_mat.scale_randomness = 0.5
	process_mat.initial_velocity = 150.0
	process_mat.initial_velocity_randomness = 0.5
	process_mat.explosiveness = 1.0
	process_mat.gravity = Vector2(0, 300)
	process_mat.radial_acceleration = 100

	var draw_mat: ParticlesMaterial = ParticlesMaterial.new()
	draw_mat.color = Color(1, 0.5, 0, 1)

	explosion.process_material = process_mat
	explosion.draw_pass_1 = draw_mat
	explosion.amount = 80
	explosion.one_shot = true
	explosion.emitting = false
	explosion.visible = false

func _input(event: InputEvent) -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Intercetta tasti hardware cuffie su Android
	# Nota: per bloccare del tutto la variazione volume di sistema serve plugin Android;
	# qui l'evento viene consumato dall'app in foreground.
	if event is InputEventKey and event.pressed:
		match event.keycode:
			KEY_MEDIA_PLAY_PAUSE:
				_fire_weapon()
			KEY_VOLUME_UP:
				_switch_weapon(1)
			KEY_VOLUME_DOWN:
				_switch_weapon(-1)

func _switch_weapon(delta: int) -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Cambia arma ciclicamente e annuncia via TTS
	current_weapon_index = (current_weapon_index + delta + weapons.size()) % weapons.size()
	var weapon: Dictionary = weapons[current_weapon_index]
	var color: Color = weapon["color"]

	# Aggiorna colori HUD vettoriale
	reticle.custom_color = color
	target_box.custom_color = Color(color.r, color.g, color.b, 0.25)
	laser_beam.default_color = color

	# TTS annuncia arma
	if tts_node:
		tts_node.speak_weapon(str(weapon["name"]))

	print("Arma selezionata: ", weapon["name"])

func _fire_weapon() -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Grilletto cuffia: sparo laser + particelle + suono
	var weapon: Dictionary = weapons[current_weapon_index]

	# Suono sparo (bassa latenza)
	if audio_shoot.stream:
		audio_shoot.play()

	# Raggio laser dal basso verso il target agganciato (o centro)
	var center: Vector2 = get_viewport_rect().size / 2
	var target_pos: Vector2 = target_box.position if target_locked else center
	_draw_laser_beam(weapon["color"], target_pos)

	# Esplosione di particelle sul bersaglio
	_emit_explosion(target_pos)

	# Suono esplosione devastante
	if audio_explosion.stream:
		audio_explosion.play()

	# Feedback vocale distruzione
	if tts_node:
		tts_node.speak("Bersaglio eliminato")

	# Reset lock-on dopo distruzione
	target_locked = false
	_stop_lock_sound()

	print("Fuoco: ", weapon["name"], " su ", target_pos)

func _draw_laser_beam(color: Color, target_pos: Vector2) -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Disegna raggio laser dal basso al target
	var center: Vector2 = get_viewport_rect().size / 2
	var start: Vector2 = Vector2(center.x, get_viewport_rect().size.y)
	laser_beam.points = [start, target_pos]
	laser_beam.default_color = color
	laser_beam.visible = true

	# Nasconde raggio dopo 0.2 secondi
	var tween: Tween = get_tree().create_tween()
	tween.tween_property(laser_beam, "modulate/a", 0.0, 0.2)
	tween.tween_callback(Callable.callable(laser_beam, "hide"))

	# Se target agganciato, attiva suono lock-on in loop con pitch crescente
	if target_locked:
		_start_lock_sound()

func _emit_explosion(position: Vector2) -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Emette particelle esplosione sulla posizione
	explosion.position = position
	explosion.restart()
	explosion.emitting = true
	explosion.visible = true

func _start_lock_sound() -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Attiva suono aggancio in loop
	if not audio_lock.stream:
		return
	if not lock_sound_playing:
		audio_lock.play()
		lock_sound_playing = true
	lock_pitch = 1.0

func _stop_lock_sound() -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Ferma suono lock-on
	if audio_lock:
		audio_lock.stop()
	lock_sound_playing = false
	lock_pitch = 1.0

func _process(delta: float) -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Loop principale: simulatore target, lock-on, pitch suono

	# Simula rilevamento target visivo al centro (sostituire con logica camera posteriore reale)
	if simulated_target:
		var offset: Vector2 = Vector2(
			sin(Time.get_ticks_msec() * 0.002) * 100,
			cos(Time.get_ticks_msec() * 0.0015) * 80
		)
		simulated_target.position = get_viewport_rect().size / 2 + offset

		var dist: float = simulated_target.position.distance_to(get_viewport_rect().size / 2)
		if dist < 60 and not target_locked:
			target_locked = true
			_start_lock_sound()
			if tts_node:
				tts_node.speak("Target acquisito")
		elif dist >= 60 and target_locked:
			target_locked = false
			_stop_lock_sound()

	# Aggiorna pitch crescente suono lock-on
	if lock_sound_playing and audio_lock:
		lock_pitch += delta * 1.5
		audio_lock.pitch_scale = clamp(lock_pitch, 1.0, 2.2)
	elif not lock_sound_playing and audio_lock:
		audio_lock.pitch_scale = 1.0

# AI (Kilo) - CAMMO 25/10/2026 - Metodo per iniettare texture camera posteriore reale (richiede plugin Android)
func set_camera_feed(texture: Texture2D) -> void:
	if camera_feed:
		camera_feed.texture = texture
