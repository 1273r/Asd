package dev.skybeat.audio;

/**
 * Turns a window of PCM samples into smoothed, log-spaced frequency bands plus a few
 * derived signals (bass/mid/treble energy, beat pulses, waveform) for the renderer.
 * All smoothing is time-based so it looks the same at any frame rate.
 */
public final class Spectrum {
	public static final int FFT_SIZE = 2048;
	public static final int BANDS = 64;
	public static final int WAVE_POINTS = 128;

	private static final float MIN_FREQ = 30f;
	private static final float MAX_FREQ = 16000f;

	private final float[] window = new float[FFT_SIZE];
	private final float[] re = new float[FFT_SIZE];
	private final float[] im = new float[FFT_SIZE];
	private final float[] raw = new float[BANDS];
	private final float[] peak = new float[BANDS];
	private final float[] waveRaw = new float[WAVE_POINTS];

	/** Smoothed band levels in [0, 1]. */
	public final float[] bands = new float[BANDS];
	/** Slow-falling peak markers in [0, 1]. */
	public final float[] peaks = new float[BANDS];
	/** Waveform samples in [-1, 1]. */
	public final float[] wave = new float[WAVE_POINTS];

	public float bass, mid, treble, loudness;
	/** Jumps to 1 on a detected beat and decays towards 0. */
	public float beat;
	/** Counts beats; handy for alternating effects. */
	public int beatCount;

	private float bassAverage;
	private float sinceBeat = 1f;

	public Spectrum() {
		for (int i = 0; i < FFT_SIZE; i++) {
			window[i] = 0.5f * (1f - (float) Math.cos(2 * Math.PI * i / (FFT_SIZE - 1)));
		}
		for (int i = 0; i < BANDS; i++) {
			peak[i] = 0.05f;
		}
	}

	/**
	 * @param track decoded audio, may be null for silence
	 * @param seconds playback position the listener is currently hearing
	 * @param gain how loud the source is for the listener (volume settings, distance)
	 * @param dt seconds since the last update
	 */
	public void update(PcmTrack track, double seconds, float gain, float dt) {
		dt = Math.min(dt, 0.1f);
		boolean silent = track == null || gain <= 0.001f;

		if (!silent) {
			int center = (int) (seconds * track.sampleRate());
			int start = center - FFT_SIZE / 2;
			for (int i = 0; i < FFT_SIZE; i++) {
				re[i] = track.sample(start + i) * window[i];
				im[i] = 0f;
			}
			fft(re, im);
			computeBands(track.sampleRate(), gain);

			// Each point averages 8 samples (a gentle low-pass), then neighbours are blended so
			// the ribbon reads as one smooth line instead of jagged noise.
			int waveStart = center - WAVE_POINTS * 4;
			for (int i = 0; i < WAVE_POINTS; i++) {
				float sum = 0f;
				for (int k = 0; k < 8; k++) {
					sum += track.sample(waveStart + i * 8 + k);
				}
				waveRaw[i] = sum / 8f;
			}
			float follow = 1f - (float) Math.exp(-dt * 25f);
			for (int i = 0; i < WAVE_POINTS; i++) {
				float a = waveRaw[Math.max(0, i - 1)], b = waveRaw[i], c = waveRaw[Math.min(WAVE_POINTS - 1, i + 1)];
				float s = (a + 2f * b + c) * 0.25f;
				wave[i] = lerp(wave[i], Math.max(-1f, Math.min(1f, s * gain * 2.2f)), follow);
			}
		} else {
			java.util.Arrays.fill(raw, 0f);
			for (int i = 0; i < WAVE_POINTS; i++) {
				wave[i] *= (float) Math.exp(-dt * 8f);
			}
		}

		// Fast attack, softer release: bars snap up on hits and glide back down.
		float attack = 1f - (float) Math.exp(-dt * 40f);
		float release = 1f - (float) Math.exp(-dt * 7f);
		for (int i = 0; i < BANDS; i++) {
			float target = raw[i];
			bands[i] = lerp(bands[i], target, target > bands[i] ? attack : release);
			if (bands[i] >= peaks[i]) {
				peaks[i] = bands[i];
			} else {
				peaks[i] = Math.max(0f, peaks[i] - dt * 0.35f);
			}
		}

		float b = average(0, 6), m = average(6, 30), t = average(30, BANDS);
		bass = lerp(bass, b, b > bass ? attack : release);
		mid = lerp(mid, m, m > mid ? attack : release);
		treble = lerp(treble, t, t > treble ? attack : release);
		loudness = lerp(loudness, (b + m + t) / 3f, release);

		// Beat: bass rising well above its recent average, with a short refractory period.
		float instant = average(0, 5);
		sinceBeat += dt;
		if (!silent && instant > 0.18f && instant > bassAverage * 1.35f + 0.04f && sinceBeat > 0.22f) {
			beat = 1f;
			beatCount++;
			sinceBeat = 0f;
		}
		bassAverage = lerp(bassAverage, instant, 1f - (float) Math.exp(-dt * 2.5f));
		beat *= (float) Math.exp(-dt * 6f);
	}

	private void computeBands(int sampleRate, float gain) {
		int bins = FFT_SIZE / 2;
		float binHz = (float) sampleRate / FFT_SIZE;
		double logMin = Math.log(MIN_FREQ), logMax = Math.log(Math.min(MAX_FREQ, sampleRate / 2f));
		for (int band = 0; band < BANDS; band++) {
			float lo = (float) Math.exp(logMin + (logMax - logMin) * band / BANDS);
			float hi = (float) Math.exp(logMin + (logMax - logMin) * (band + 1) / BANDS);
			int a = Math.max(1, (int) (lo / binHz));
			int z = Math.min(bins - 1, Math.max(a, (int) (hi / binHz)));
			float max = 0f;
			for (int k = a; k <= z; k++) {
				float mag = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]);
				max = Math.max(max, mag);
			}
			// Tilt so highs are visible: real music has far less energy up there.
			float tilt = 1f + band / (float) BANDS * 3f;
			float level = max * tilt / (FFT_SIZE / 4f);
			// Per-band auto gain: track a slowly decaying peak so quiet songs still fill the sky.
			peak[band] = Math.max(peak[band] * 0.9993f, Math.max(level, 0.02f));
			float norm = level / peak[band];
			float db = (float) (Math.log10(1e-4 + norm) * 20.0);
			raw[band] = clamp01((db + 30f) / 30f) * Math.min(1f, gain);
		}
	}

	private float average(int from, int to) {
		float sum = 0f;
		for (int i = from; i < to; i++) {
			sum += raw[i];
		}
		return sum / (to - from);
	}

	private static void fft(float[] re, float[] im) {
		int n = re.length;
		for (int i = 1, j = 0; i < n; i++) {
			int bit = n >> 1;
			for (; (j & bit) != 0; bit >>= 1) {
				j ^= bit;
			}
			j ^= bit;
			if (i < j) {
				float t = re[i]; re[i] = re[j]; re[j] = t;
				t = im[i]; im[i] = im[j]; im[j] = t;
			}
		}
		for (int len = 2; len <= n; len <<= 1) {
			double ang = -2 * Math.PI / len;
			float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
			for (int i = 0; i < n; i += len) {
				float cr = 1f, ci = 0f;
				for (int k = 0; k < len / 2; k++) {
					int u = i + k, v = i + k + len / 2;
					float vr = re[v] * cr - im[v] * ci;
					float vi = re[v] * ci + im[v] * cr;
					re[v] = re[u] - vr; im[v] = im[u] - vi;
					re[u] += vr; im[u] += vi;
					float ncr = cr * wr - ci * wi;
					ci = cr * wi + ci * wr;
					cr = ncr;
				}
			}
		}
	}

	static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	static float clamp01(float v) {
		return v < 0f ? 0f : Math.min(1f, v);
	}
}
