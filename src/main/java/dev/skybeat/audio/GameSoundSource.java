package dev.skybeat.audio;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import dev.skybeat.Skybeat;

/**
 * Follows a streaming vanilla sound (background music or a jukebox disc). The same .ogg the
 * sound engine is playing gets decoded on a worker thread, and a clock started when the sound
 * started tells us which part of it the player is hearing.
 */
public final class GameSoundSource implements AudioSource {
	private final SoundInstance instance;
	private final Sound sound;
	private final String name;
	private volatile @Nullable PcmTrack track;
	private double position;
	private boolean finished;
	private int inactiveTicks;

	public GameSoundSource(SoundInstance instance, Sound sound, ExecutorService decoder) {
		this.instance = instance;
		this.sound = sound;
		this.name = prettyName(sound.getLocation().getPath());
		Minecraft client = Minecraft.getInstance();
		decoder.submit(() -> {
			Optional<Resource> resource = client.getResourceManager().getResource(sound.getPath());
			if (resource.isEmpty()) {
				finished = true;
				return;
			}
			try (InputStream in = resource.get().open()) {
				PcmTrack.decodeOgg(in, t -> track = t);
			} catch (Exception e) {
				Skybeat.LOGGER.warn("Could not decode {} for the visualizer", sound.getPath(), e);
				finished = true;
			}
		});
	}

	public static boolean wants(SoundInstance instance, @Nullable Sound sound) {
		if (sound == null || !sound.shouldStream()) {
			return false;
		}
		SoundSource source = instance.getSource();
		return source == SoundSource.MUSIC || source == SoundSource.RECORDS;
	}

	public SoundInstance instance() {
		return instance;
	}

	/** Advances the clock; called every frame. */
	public void advance(Minecraft client, double dt) {
		// Jukeboxes pause with the game, background music keeps going.
		if (instance.getSource() == SoundSource.RECORDS && client.isPaused()) {
			return;
		}
		position += dt * instance.getPitch();
	}

	/** Called every client tick. */
	public void tick(Minecraft client) {
		if (client.getSoundManager().isActive(instance)) {
			inactiveTicks = 0;
		} else if (++inactiveTicks > 2) {
			finished = true;
		}
		PcmTrack t = track;
		if (t != null && t.complete() && position * t.sampleRate() > t.length() + t.sampleRate()) {
			finished = true;
		}
	}

	@Override
	public @Nullable PcmTrack track() {
		return track;
	}

	@Override
	public double position() {
		return position;
	}

	@Override
	public float gain() {
		Minecraft client = Minecraft.getInstance();
		float gain = Math.min(1f, instance.getVolume()) * client.options.getFinalSoundSourceVolume(instance.getSource());
		if (!instance.isRelative() && instance.getAttenuation() == SoundInstance.Attenuation.LINEAR && client.gameRenderer != null) {
			Vec3 camera = client.gameRenderer.getMainCamera().position();
			double range = Math.max(instance.getVolume(), 1f) * sound.getAttenuationDistance();
			double dist = camera.distanceTo(new Vec3(instance.getX(), instance.getY(), instance.getZ()));
			gain *= (float) Math.max(0.0, 1.0 - dist / range);
		}
		return gain;
	}

	@Override
	public boolean finished() {
		return finished;
	}

	@Override
	public String name() {
		return name;
	}

	static String prettyName(String path) {
		String base = path.substring(path.lastIndexOf('/') + 1);
		int dot = base.lastIndexOf('.');
		if (dot > 0) {
			base = base.substring(0, dot);
		}
		StringBuilder out = new StringBuilder();
		for (String word : base.replace('-', '_').split("_")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}
}
