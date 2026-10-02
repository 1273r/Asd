package dev.skybeat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;

import dev.skybeat.audio.AudioSource;
import dev.skybeat.audio.FilePlayer;
import dev.skybeat.audio.GameSoundSource;
import dev.skybeat.audio.Spectrum;
import dev.skybeat.render.SkyVisualizer;
import dev.skybeat.render.Theme;

public class Skybeat implements ClientModInitializer {
	public static final String MOD_ID = "skybeat";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

	private final ExecutorService decoder = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Skybeat decoder");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	private static Skybeat instance;

	private final List<GameSoundSource> gameSources = new ArrayList<>();
	private final List<AudioSource> extraSources = new ArrayList<>();
	private final Spectrum spectrum = new Spectrum();
	private final SkyVisualizer visualizer = new SkyVisualizer();
	private final Path configFile = FabricLoader.getInstance().getConfigDir().resolve("skybeat.properties");
	private final Path musicDir = FabricLoader.getInstance().getConfigDir().resolve("skybeat").resolve("music");

	private KeyMapping toggleKey, themeKey, darkenKey, nextTrackKey, stopTrackKey;
	private FilePlayer filePlayer;
	private int trackIndex = -1;
	private AudioSource lastPrimary;

	private boolean enabled = true;
	private boolean darken = true;
	private Theme theme = Theme.SYNTHWAVE;

	private float visibility;
	private float time;
	private long lastFrame;

	@Override
	public void onInitializeClient() {
		instance = this;
		loadConfig();
		try {
			Files.createDirectories(musicDir);
		} catch (IOException e) {
			LOGGER.warn("Could not create {}", musicDir, e);
		}

		toggleKey = register("toggle", GLFW.GLFW_KEY_K);
		themeKey = register("theme", GLFW.GLFW_KEY_J);
		darkenKey = register("darken", GLFW.GLFW_KEY_H);
		nextTrackKey = register("next_track", GLFW.GLFW_KEY_N);
		stopTrackKey = register("stop_track", GLFW.GLFW_KEY_M);

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> client.getSoundManager().addListener(new SoundEventListener() {
			@Override
			public void onPlaySound(SoundInstance instance, WeighedSoundEvents events, float range) {
				if (GameSoundSource.wants(instance, instance.getSound())) {
					gameSources.removeIf(s -> s.instance() == instance);
					gameSources.add(new GameSoundSource(instance, instance.getSound(), decoder));
				}
			}
		}));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		WorldRenderEvents.START_MAIN.register(context -> renderFrame());
	}

	private static KeyMapping register(String name, int key) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.skybeat." + name, InputConstants.Type.KEYSYM, key, CATEGORY));
	}

	private void tick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			enabled = !enabled;
			showMessage("Sky visualizer " + (enabled ? "on" : "off"));
			saveConfig();
		}
		while (themeKey.consumeClick()) {
			theme = theme.next();
			showMessage("Theme: " + theme.label);
			saveConfig();
		}
		while (darkenKey.consumeClick()) {
			darken = !darken;
			showMessage("Sky dimming " + (darken ? "on" : "off"));
			saveConfig();
		}
		while (nextTrackKey.consumeClick()) {
			playNextTrack();
		}
		while (stopTrackKey.consumeClick()) {
			if (filePlayer != null) {
				filePlayer.stop();
				filePlayer = null;
				showMessage("Stopped");
			}
		}

		for (GameSoundSource source : gameSources) {
			source.tick(client);
		}
		gameSources.removeIf(GameSoundSource::finished);

		if (filePlayer != null) {
			filePlayer.setVolume(client.options.getFinalSoundSourceVolume(SoundSource.MUSIC));
			if (filePlayer.finished()) {
				filePlayer = null;
				playNextTrack();
			} else {
				// Don't let the vanilla soundtrack talk over the user's music.
				client.getMusicManager().stopPlaying();
			}
		}
	}

	private void playNextTrack() {
		List<Path> tracks;
		try (Stream<Path> files = Files.list(musicDir)) {
			tracks = files.filter(Files::isRegularFile).filter(FilePlayer::supported)
					.sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
					.toList();
		} catch (IOException e) {
			tracks = List.of();
		}
		if (tracks.isEmpty()) {
			showMessage("Put .ogg or .wav files in config/skybeat/music to play your own songs");
			return;
		}
		if (filePlayer != null) {
			filePlayer.stop();
		}
		trackIndex = (trackIndex + 1) % tracks.size();
		Path track = tracks.get(trackIndex);
		filePlayer = new FilePlayer(track);
		filePlayer.setVolume(Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.MUSIC));
		Minecraft.getInstance().getMusicManager().stopPlaying();
		showMessage("♫ " + filePlayer.name());
	}

	private void renderFrame() {
		Minecraft client = Minecraft.getInstance();
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1.0e9f);
		lastFrame = now;
		time += dt;

		for (GameSoundSource source : gameSources) {
			source.advance(client, dt);
		}

		AudioSource primary = pickPrimary();
		if (primary != lastPrimary) {
			if (primary != null && primary != filePlayer && enabled) {
				showMessage("♫ " + primary.name());
			}
			lastPrimary = primary;
		}
		if (primary != null) {
			spectrum.update(primary.track(), primary.position(), primary.gain(), dt);
		} else {
			spectrum.update(null, 0.0, 0f, dt);
		}

		float target = enabled && primary != null && primary.gain() > 0.01f ? 1f : 0f;
		float speed = target > visibility ? 0.8f : 0.5f;
		visibility = target > visibility ? Math.min(target, visibility + dt * speed) : Math.max(target, visibility - dt * speed);
		if (visibility <= 0.002f || client.level == null) {
			return;
		}
		float eased = visibility * visibility * (3f - 2f * visibility);
		visualizer.render(spectrum, eased, theme, darken, time, dt);
	}

	/** Lets other mods (and the game test) feed audio to the visualizer. Call on the client thread. */
	public static void addSource(AudioSource source) {
		instance.extraSources.add(source);
	}

	private AudioSource pickPrimary() {
		if (filePlayer != null && !filePlayer.finished()) {
			return filePlayer;
		}
		extraSources.removeIf(AudioSource::finished);
		AudioSource best = null;
		float bestGain = -1f;
		for (AudioSource source : extraSources) {
			if (source.track() != null && source.gain() > bestGain) {
				best = source;
				bestGain = source.gain();
			}
		}
		for (GameSoundSource source : gameSources) {
			if (source.track() == null || source.finished()) {
				continue;
			}
			float gain = source.gain();
			if (gain > bestGain) {
				best = source;
				bestGain = gain;
			}
		}
		return best;
	}

	public static void showMessage(String text) {
		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			if (client.gui != null) {
				client.gui.setOverlayMessage(Component.literal(text), false);
			}
		});
	}

	private void loadConfig() {
		if (!Files.exists(configFile)) {
			saveConfig();
			return;
		}
		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(configFile)) {
			props.load(in);
			enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
			darken = Boolean.parseBoolean(props.getProperty("dimSky", "true"));
			theme = Theme.valueOf(props.getProperty("theme", Theme.SYNTHWAVE.name()).toUpperCase(Locale.ROOT));
		} catch (Exception e) {
			LOGGER.warn("Could not read {}", configFile, e);
		}
	}

	private void saveConfig() {
		Properties props = new Properties();
		props.setProperty("enabled", Boolean.toString(enabled));
		props.setProperty("dimSky", Boolean.toString(darken));
		props.setProperty("theme", theme.name());
		try (OutputStream out = Files.newOutputStream(configFile)) {
			props.store(out, "Skybeat settings. Themes: RAINBOW, SYNTHWAVE, AURORA, EMBER, OCEAN, CANDY");
		} catch (IOException e) {
			LOGGER.warn("Could not save {}", configFile, e);
		}
	}
}
