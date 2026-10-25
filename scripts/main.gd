extends Node2D
# AI (Kilo) - CAMMO 25/10/2026 - BadGuy AR

@onready var camera_feed: TextureRect = $CameraFeed
@onready var bg_fallback: ColorRect = $BgFallback
@onready var status_label: Label = $StatusLabel
@onready var target_box: ColorRect = $HUD/TargetBox
@onready var reticle_top: ColorRect = $HUD/ReticleTop
@onready var reticle_bottom: ColorRect = $HUD/ReticleBottom
@onready var reticle_left: ColorRect = $HUD/ReticleLeft
@onready var reticle_right: ColorRect = $HUD/ReticleRight
@onready var laser_left: Line2D = $HUD/LaserLeft
@onready var laser_right: Line2D = $HUD/LaserRight
@onready var explosion: CPUParticles2D = $HUD/ExplosionParticles
@onready var simulated_target: ColorRect = $SimulatedTarget
@onready var audio_shoot: AudioStreamPlayer = $Audio/Shoot
@onready var audio_explosion: AudioStreamPlayer = $Audio/Explosion
@onready var audio_lock: AudioStreamPlayer = $Audio/LockOn
@onready var tts_node: Node = $TTS

var weapons: Array = []
var current_weapon_index: int = 0
var target_locked: bool = false
var lock_sound_playing: bool = false
var lock_pitch: float = 1.0
var camera_attempts: int = 0
var camera_ok: bool = false

func _ready() -> void:
	get_viewport().transparent_bg = false

	if camera_feed:
		camera_feed.anchor_left = 0; camera_feed.anchor_top = 0
		camera_feed.anchor_right = 1; camera_feed.anchor_bottom = 1
		camera_feed.offset_left = 0; camera_feed.offset_top = 0
		camera_feed.offset_right = 0; camera_feed.offset_bottom = 0
		camera_feed.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		camera_feed.stretch_mode = TextureRect.STRETCH_SCALE
		camera_feed.z_index = -5
		camera_feed.size = get_viewport_rect().size

	if bg_fallback:
		bg_fallback.anchor_left = 0; bg_fallback.anchor_top = 0
		bg_fallback.anchor_right = 1; bg_fallback.anchor_bottom = 1
		bg_fallback.offset_left = 0; bg_fallback.offset_top = 0
		bg_fallback.offset_right = 0; bg_fallback.offset_bottom = 0
		bg_fallback.color = Color(0.05, 0.08, 0.12, 1)
		bg_fallback.z_index = -10

	if status_label:
		status_label.anchor_left = 0; status_label.anchor_top = 0
		status_label.anchor_right = 1; status_label.anchor_bottom = 0
		status_label.offset_left = 10; status_label.offset_top = 10
		status_label.offset_right = -10; status_label.offset_bottom = 40
		status_label.text = "BADGUY AR - Inizializzazione camera..."
		status_label.modulate = Color(0, 1, 0, 1)

	CameraServer.set_monitoring_feeds(true)
	_init_camera_feed()
	print("CAMERA_FEED_SIZE: ", camera_feed.size if camera_feed else null, " TEX: ", camera_feed.texture if camera_feed else null)

	weapons = [
		{"name": "Cannone a Ioni", "color": Color(0, 1, 0, 1)},
		{"name": "Laser a Impulsi", "color": Color(1, 0.5, 0, 1)},
		{"name": "Siluri Protonici", "color": Color(0, 0.5, 1, 1)},
	]
	_load_audio()
	_setup_explosion_particles()
	_layout_hud()

	if tts_node:
		tts_node.speak("Sistema BadGuy attivato")
	print("BadGuy pronto. Arma: ", weapons[current_weapon_index]["name"])

func _layout_hud() -> void:
	var center: Vector2 = get_viewport_rect().size / 2
	var green = Color(0, 1, 0, 1)
	# Reticolo crosshair fisso al centro (4 barre)
	if reticle_top:
		reticle_top.size = Vector2(2, 40); reticle_top.color = green
		reticle_top.position = center - Vector2(1, 50)
	if reticle_bottom:
		reticle_bottom.size = Vector2(2, 40); reticle_bottom.color = green
		reticle_bottom.position = center + Vector2(-1, 10)
	if reticle_left:
		reticle_left.size = Vector2(40, 2); reticle_left.color = green
		reticle_left.position = center - Vector2(50, 1)
	if reticle_right:
		reticle_right.size = Vector2(40, 2); reticle_right.color = green
		reticle_right.position = center + Vector2(10, -1)
	if target_box:
		target_box.size = Vector2(100, 100)
		target_box.color = Color(0, 1, 0, 0.3)
	if simulated_target:
		simulated_target.size = Vector2(24, 24)
		simulated_target.color = Color(1, 1, 0, 0.9)
	if laser_left:
		laser_left.visible = false; laser_left.width = 3
	if laser_right:
		laser_right.visible = false; laser_right.width = 3

func _init_camera_feed() -> void:
	if OS.get_name() == "Android":
		OS.request_permission("android.permission.CAMERA")
	var count = CameraServer.get_feed_count()
	print("Camera feeds: ", count)
	var rear_feed = null
	for i in range(count):
		var f = CameraServer.get_feed(i)
		var fname = str(f.get_name()).to_lower()
		print("Feed ", i, ": ", fname)
		if rear_feed == null:
			rear_feed = f
		if fname.find("back") >= 0 or fname.find("rear") >= 0 or fname.find("0") >= 0:
			rear_feed = f
	if rear_feed:
		rear_feed.set_format(0, {})
		rear_feed.set_active(true)
		if camera_feed:
			var ct = CameraTexture.new()
			ct.camera_feed_id = rear_feed.get_id()
			ct.camera_is_active = true
			ct.which_feed = 1
			camera_feed.texture = ct
			camera_ok = true
			if status_label:
				status_label.text = "BADGUY AR - Camera OK"
			print("Camera texture assegnata")
		print("Camera feed attivo: ", rear_feed.get_name())
	else:
		print("Nessun camera feed")
		if status_label:
			status_label.text = "BADGUY AR - Camera non disponibile"

func _load_audio() -> void:
	var audio_dir: String = "res://assets/audio/"
	var players: Dictionary = {"shoot": audio_shoot, "explosion": audio_explosion, "lock_on": audio_lock}
	for key in players:
		var path: String = audio_dir + key + ".wav"
		if FileAccess.file_exists(path):
			players[key].stream = load(path)
		else:
			print("Audio mancante: ", path)

func _setup_explosion_particles() -> void:
	if not explosion:
		return
	explosion.amount = 80
	explosion.one_shot = true
	explosion.emitting = false
	explosion.visible = false
	explosion.lifetime = 0.8
	explosion.lifetime_randomness = 0.4
	explosion.speed_scale = 1.0
	explosion.explosiveness = 1.0
	explosion.randomness = 0.5
	explosion.spread = 180.0
	explosion.gravity = Vector2(0, 300)
	explosion.initial_velocity_min = 150.0
	explosion.initial_velocity_max = 300.0
	explosion.angular_velocity_min = 0.0
	explosion.angular_velocity_max = 90.0
	explosion.color = Color(1, 0.5, 0, 1)
	explosion.color_ramp = null

func _input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed:
		match event.keycode:
			85, Key.KEY_PAUSE, Key.KEY_SPACE, Key.KEY_ENTER:
				_fire_weapon()
			24:
				_switch_weapon(1)
			25:
				_switch_weapon(-1)

func _switch_weapon(delta: int) -> void:
	current_weapon_index = (current_weapon_index + delta + weapons.size()) % weapons.size()
	var weapon: Dictionary = weapons[current_weapon_index]
	var color: Color = weapon["color"]
	if target_box:
		target_box.color = Color(color.r, color.g, color.b, 0.3)
	for r in [reticle_top, reticle_bottom, reticle_left, reticle_right]:
		if r:
			r.color = color
	if tts_node:
		tts_node.speak_weapon(str(weapon["name"]))
	print("Arma: ", weapon["name"])

func _fire_weapon() -> void:
	var weapon: Dictionary = weapons[current_weapon_index]
	if audio_shoot and audio_shoot.stream:
		audio_shoot.play()

	var center: Vector2 = get_viewport_rect().size / 2
	var target_pos: Vector2 = center
	if target_box and target_locked:
		target_pos = target_box.position + target_box.size / 2

	if laser_left:
		laser_left.points = PackedVector2Array([Vector2(0, get_viewport_rect().size.y), target_pos])
		laser_left.default_color = weapon["color"]
		laser_left.visible = true
	if laser_right:
		laser_right.points = PackedVector2Array([Vector2(get_viewport_rect().size.x, get_viewport_rect().size.y), target_pos])
		laser_right.default_color = weapon["color"]
		laser_right.visible = true

	_emit_explosion(target_pos)

	if audio_explosion and audio_explosion.stream:
		audio_explosion.play()
	if tts_node:
		tts_node.speak("Bersaglio eliminato")

	target_locked = false
	_stop_lock_sound()

	var tween: Tween = get_tree().create_tween()
	tween.tween_interval(0.3)
	tween.tween_callback(func():
		if laser_left: laser_left.visible = false
		if laser_right: laser_right.visible = false
	)

func _emit_explosion(position: Vector2) -> void:
	if not explosion:
		return
	explosion.position = position
	explosion.restart()
	explosion.emitting = true
	explosion.visible = true

func _start_lock_sound() -> void:
	if not audio_lock or not audio_lock.stream:
		return
	if not lock_sound_playing:
		audio_lock.play()
		lock_sound_playing = true
	lock_pitch = 1.0

func _stop_lock_sound() -> void:
	if audio_lock:
		audio_lock.stop()
	lock_sound_playing = false
	lock_pitch = 1.0

func _process(delta: float) -> void:
	# Riprova camera feed se texture non assegnata
	if camera_feed and camera_feed.texture == null and camera_attempts < 60:
		camera_attempts += 1
		var count = CameraServer.get_feed_count()
		if count > 0:
			var f = CameraServer.get_feed(0)
			f.set_format(0, {})
			f.set_active(true)
			var ct = CameraTexture.new()
			ct.camera_feed_id = f.get_id()
			ct.camera_is_active = true
			ct.which_feed = 1
			camera_feed.texture = ct
			camera_ok = true
			if status_label:
				status_label.text = "BADGUY AR - Camera OK"
			print("Camera texture assegnata (tentativo ", camera_attempts, ")")

	if simulated_target:
		var offset: Vector2 = Vector2(
			sin(Time.get_ticks_msec() * 0.002) * 120,
			cos(Time.get_ticks_msec() * 0.0015) * 90
		)
		var center: Vector2 = get_viewport_rect().size / 2
		simulated_target.position = center - simulated_target.size / 2 + offset

		var dist: float = (simulated_target.position + simulated_target.size / 2).distance_to(center)
		if dist < 80 and not target_locked:
			target_locked = true
			_start_lock_sound()
			if tts_node:
				tts_node.speak("Target acquisito")
		elif dist >= 80 and target_locked:
			target_locked = false
			_stop_lock_sound()

		if target_box:
			target_box.position = simulated_target.position - (target_box.size - simulated_target.size) / 2

	if lock_sound_playing and audio_lock:
		lock_pitch += delta * 1.5
		audio_lock.pitch_scale = clamp(lock_pitch, 1.0, 2.2)
	elif audio_lock:
		audio_lock.pitch_scale = 1.0

func set_camera_feed(texture: Texture2D) -> void:
	if camera_feed:
		camera_feed.texture = texture
