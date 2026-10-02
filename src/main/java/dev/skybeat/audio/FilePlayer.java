package dev.skybeat.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.sounds.JOrbisAudioStream;

import dev.skybeat.Skybeat;

/**
 * Plays a local .ogg or .wav file through Java Sound while feeding the visualizer. Decoding
 * and playback happen on one thread; the line's frame counter is the playback clock, so the
 * visuals stay locked to what comes out of the speakers.
 */
public final class FilePlayer implements AudioSource {
	private static final int CHUNK_FRAMES = 2048;

	private final Path file;
	private final String name;
	private final Thread thread;
	private volatile @Nullable PcmTrack track;
	private volatile @Nullable SourceDataLine line;
	private volatile float sourceRate = 44100f;
	private volatile boolean finished;
	private volatile boolean stopped;
	private volatile float volume = 1f;

	public FilePlayer(Path file) {
		this.file = file;
		this.name = GameSoundSource.prettyName(file.getFileName().toString());
		this.thread = new Thread(this::run, "Skybeat file player");
		this.thread.setDaemon(true);
		this.thread.start();
	}

	public void stop() {
		stopped = true;
		thread.interrupt();
	}

	public void setVolume(float volume) {
		this.volume = volume;
	}

	private void run() {
		try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
			if (file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ogg")) {
				playOgg(in);
			} else {
				playJavaSound(in);
			}
		} catch (Exception e) {
			if (!stopped) {
				Skybeat.LOGGER.warn("Could not play {}", file, e);
				Skybeat.showMessage("Can't play " + file.getFileName() + ": " + e.getMessage());
			}
		} finally {
			SourceDataLine l = line;
			if (l != null) {
				if (!stopped) {
					l.drain();
				}
				l.close();
			}
			finished = true;
		}
	}

	private void playOgg(InputStream in) throws Exception {
		try (JOrbisAudioStream stream = new JOrbisAudioStream(in)) {
			AudioFormat format = stream.getFormat();
			int channels = format.getChannels();
			SourceDataLine l = open(format);
			PcmTrack.Writer writer = new PcmTrack.Writer((int) format.getSampleRate(), channels);
			track = writer.track();
			byte[] out = new byte[CHUNK_FRAMES * channels * 2];
			int[] fill = {0};
			boolean more = true;
			while (more && !stopped) {
				more = stream.readChunk(sample -> {
					writer.accept(sample);
					int v = (int) (Math.max(-1f, Math.min(1f, sample)) * 32767f);
					out[fill[0]++] = (byte) v;
					out[fill[0]++] = (byte) (v >> 8);
					if (fill[0] == out.length) {
						write(l, out, out.length);
						fill[0] = 0;
					}
				});
			}
			if (!stopped) {
				write(l, out, fill[0]);
			}
			writer.track().markComplete();
		}
	}

	private void playJavaSound(InputStream in) throws Exception {
		try (AudioInputStream raw = AudioSystem.getAudioInputStream(in)) {
			AudioFormat src = raw.getFormat();
			AudioFormat pcm = new AudioFormat(src.getSampleRate(), 16, src.getChannels(), true, false);
			try (AudioInputStream stream = AudioSystem.getAudioInputStream(pcm, raw)) {
				SourceDataLine l = open(pcm);
				PcmTrack.Writer writer = new PcmTrack.Writer((int) pcm.getSampleRate(), pcm.getChannels());
				track = writer.track();
				byte[] buf = new byte[CHUNK_FRAMES * pcm.getFrameSize()];
				int n;
				while (!stopped && (n = stream.readNBytes(buf, 0, buf.length)) > 0) {
					for (int i = 0; i + 1 < n; i += 2) {
						writer.accept((short) ((buf[i] & 0xFF) | (buf[i + 1] << 8)) / 32768f);
					}
					write(l, buf, n - n % pcm.getFrameSize());
				}
				writer.track().markComplete();
			}
		}
	}

	private SourceDataLine open(AudioFormat format) throws Exception {
		sourceRate = format.getSampleRate();
		SourceDataLine l = AudioSystem.getSourceDataLine(format);
		// ~0.25s of buffer keeps latency low so the visuals feel tight.
		l.open(format, (int) (format.getSampleRate() / 4) * format.getFrameSize());
		applyVolume(l);
		l.start();
		line = l;
		return l;
	}

	private void write(SourceDataLine l, byte[] data, int length) {
		if (stopped) {
			return;
		}
		applyVolume(l);
		int off = 0;
		while (off < length && !stopped) {
			off += l.write(data, off, length - off);
		}
	}

	private void applyVolume(SourceDataLine l) {
		if (l.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			FloatControl gain = (FloatControl) l.getControl(FloatControl.Type.MASTER_GAIN);
			float db = volume <= 0.0001f ? gain.getMinimum() : (float) (20.0 * Math.log10(volume));
			gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
		}
	}

	@Override
	public @Nullable PcmTrack track() {
		return line == null ? null : track;
	}

	@Override
	public double position() {
		SourceDataLine l = line;
		return l == null ? 0.0 : l.getLongFramePosition() / (double) sourceRate;
	}

	@Override
	public float gain() {
		return volume;
	}

	@Override
	public boolean finished() {
		return finished;
	}

	@Override
	public String name() {
		return name;
	}

	public static boolean supported(Path path) {
		String n = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
		return n.endsWith(".ogg") || n.endsWith(".wav") || n.endsWith(".aiff") || n.endsWith(".aif") || n.endsWith(".au");
	}
}
