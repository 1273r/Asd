package dev.skybeat.render;

/** Color palettes. Each one maps a cyclic position t to a color. */
public enum Theme {
	RAINBOW("Rainbow", null),
	SYNTHWAVE("Synthwave", new int[] {0xFF2E97, 0xB026FF, 0x2DE2E6, 0x5B3CFF}),
	AURORA("Aurora", new int[] {0x22FF99, 0x00C2FF, 0x8A2BE2, 0x2BFFD4}),
	EMBER("Ember", new int[] {0xFF3B1F, 0xFF8A00, 0xFFD23F, 0xFF4F6D}),
	OCEAN("Ocean", new int[] {0x00E5FF, 0x0077FF, 0x00FFC6, 0x3A5BFF}),
	CANDY("Candy", new int[] {0xFF7EB6, 0xFFD36E, 0x7AF0FF, 0xC59BFF});

	public final String label;
	private final int[] stops;

	Theme(String label, int[] stops) {
		this.label = label;
		this.stops = stops;
	}

	public Theme next() {
		Theme[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	/** Writes r, g, b in [0, 1] into out. */
	public void color(float t, float[] out) {
		t -= (float) Math.floor(t);
		if (stops == null) {
			hsv(t, 0.85f, 1f, out);
			return;
		}
		float f = t * stops.length;
		int i = (int) f;
		float k = f - i;
		k = k * k * (3f - 2f * k);
		int a = stops[i % stops.length], b = stops[(i + 1) % stops.length];
		out[0] = mix((a >> 16) & 0xFF, (b >> 16) & 0xFF, k) / 255f;
		out[1] = mix((a >> 8) & 0xFF, (b >> 8) & 0xFF, k) / 255f;
		out[2] = mix(a & 0xFF, b & 0xFF, k) / 255f;
	}

	private static float mix(int a, int b, float k) {
		return a + (b - a) * k;
	}

	static void hsv(float h, float s, float v, float[] out) {
		float r = Math.abs(h * 6f - 3f) - 1f;
		float g = 2f - Math.abs(h * 6f - 2f);
		float b = 2f - Math.abs(h * 6f - 4f);
		out[0] = v * (1f - s + s * clamp(r));
		out[1] = v * (1f - s + s * clamp(g));
		out[2] = v * (1f - s + s * clamp(b));
	}

	private static float clamp(float v) {
		return v < 0f ? 0f : Math.min(1f, v);
	}
}
