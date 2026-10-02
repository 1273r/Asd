package dev.skybeat.audio;

import java.io.IOException;
import java.io.InputStream;

import net.minecraft.client.sounds.JOrbisAudioStream;

/**
 * Mono 16-bit PCM used only for analysis. It is filled progressively by a decoder thread
 * while the render thread reads from it, so readers must tolerate a partially decoded track.
 */
public final class PcmTrack {
	private volatile short[] data = new short[1 << 16];
	private volatile int length;
	private volatile boolean complete;
	private final int sampleRate;

	public PcmTrack(int sampleRate) {
		this.sampleRate = sampleRate;
	}

	public int sampleRate() {
		return sampleRate;
	}

	public int length() {
		return length;
	}

	public boolean complete() {
		return complete;
	}

	public void markComplete() {
		complete = true;
	}

	public float sample(int index) {
		short[] d = data;
		if (index < 0 || index >= length || index >= d.length) {
			return 0f;
		}
		return d[index] / 32768f;
	}

	/** Called only from the decoder thread. */
	void append(float value) {
		short[] d = data;
		int n = length;
		if (n == d.length) {
			short[] grown = new short[d.length * 2];
			System.arraycopy(d, 0, grown, 0, n);
			data = d = grown;
		}
		d[n] = (short) (Math.max(-1f, Math.min(1f, value)) * 32767f);
		length = n + 1;
	}

	/**
	 * Downmixes interleaved float frames to mono and halves high sample rates to keep memory low.
	 */
	public static final class Writer {
		private final PcmTrack track;
		private final int channels;
		private final int decimate;
		private int channel;
		private float frameSum;
		private int frameCount;
		private float decimateSum;

		public Writer(int sourceRate, int channels) {
			this.channels = Math.max(1, channels);
			this.decimate = sourceRate >= 32000 ? 2 : 1;
			this.track = new PcmTrack(sourceRate / decimate);
		}

		public PcmTrack track() {
			return track;
		}

		public void accept(float sample) {
			frameSum += sample;
			if (++channel < channels) {
				return;
			}
			channel = 0;
			float mono = frameSum / channels;
			frameSum = 0f;
			decimateSum += mono;
			if (++frameCount >= decimate) {
				track.append(decimateSum / decimate);
				decimateSum = 0f;
				frameCount = 0;
			}
		}
	}

	/** Decodes a whole Ogg Vorbis stream, publishing samples as they are produced. */
	public static void decodeOgg(InputStream in, java.util.function.Consumer<PcmTrack> onStart) throws IOException {
		try (JOrbisAudioStream stream = new JOrbisAudioStream(in)) {
			Writer writer = new Writer((int) stream.getFormat().getSampleRate(), stream.getFormat().getChannels());
			onStart.accept(writer.track());
			while (stream.readChunk(writer::accept)) {
				if (Thread.currentThread().isInterrupted()) {
					return;
				}
			}
			writer.track().markComplete();
		}
	}
}
