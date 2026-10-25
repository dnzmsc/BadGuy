extends Node
# AI (Kilo) - CAMMO 25/10/2026 - Interfaccia Text-to-Speech per BadGuy (Android)

var voice_player: AudioStreamPlayer

func _ready() -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Riferimento opzionale a player audio vocale
	voice_player = get_node_or_null("Audio/Voice")

func speak(text: String) -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Annuncia testo.
	# Su Android reale richiede plugin TTS nativo (es. Godot TTS GDExtension).
	print("TTS: ", text)
	if voice_player and voice_player.stream:
		voice_player.play()
	# TODO: integrare API Android TextToSpeech tramite plugin Java/GDExtension qui

func speak_weapon(name: String) -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Annuncia il nome dell'arma selezionata
	speak("Arma: " + str(name))

func speak_status(status: String) -> void:
	# AI (Kilo) - CAMMO 25/10/2026 - Annuncia stato aggancio / distruzione bersaglio
	speak(str(status))
