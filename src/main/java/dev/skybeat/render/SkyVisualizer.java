package dev.skybeat.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import dev.skybeat.audio.Spectrum;

/**
 * Draws the visualizer as part of the sky. Everything is centered on the camera and drawn
 * right after the vanilla sky with depth testing and depth writes off, so terrain, entities,
 * water and clouds all render in front of it, exactly like a skybox. Because nothing writes
 * depth, the actual radius only matters for staying inside the far plane.
 */
public final class SkyVisualizer {
	private static final RenderPipeline GLOW_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("skybeat", "pipeline/sky_glow"))
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withBlend(BlendFunction.LIGHTNING)
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withCull(false)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
			.build();
	private static final RenderPipeline SHADE_PIPELINE = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath("skybeat", "pipeline/sky_shade"))
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withBlend(BlendFunction.TRANSLUCENT)
			.withDepthWrite(false)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withCull(false)
			.withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
			.build();
	private static final RenderType GLOW = RenderType.create("skybeat_glow",
			RenderSetup.builder(GLOW_PIPELINE).bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());
	private static final RenderType SHADE = RenderType.create("skybeat_shade",
			RenderSetup.builder(SHADE_PIPELINE).bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup());

	private static final float R = 60f;
	private static final float ZENITH = 42f;
	private static final int BARS = Spectrum.BANDS * 2;
	private static final int STARS = 180;
	private static final float TAU = (float) (Math.PI * 2);

	private final float[] starDir = new float[STARS * 3];
	private final float[] starPhase = new float[STARS];
	private final int[] starBand = new int[STARS];
	private final List<float[]> shockwaves = new ArrayList<>();
	private final float[] rgb = new float[3];
	private int lastBeat;
	private float spin;

	public SkyVisualizer() {
		Random random = new Random(0x5B3A7L);
		for (int i = 0; i < STARS; i++) {
			// Uniform over the upper part of the sphere.
			float y = 0.12f + random.nextFloat() * 0.88f;
			float a = random.nextFloat() * TAU;
			float r = (float) Math.sqrt(1f - y * y);
			starDir[i * 3] = r * (float) Math.cos(a);
			starDir[i * 3 + 1] = y;
			starDir[i * 3 + 2] = r * (float) Math.sin(a);
			starPhase[i] = random.nextFloat() * TAU;
			starBand[i] = 20 + random.nextInt(Spectrum.BANDS - 20);
		}
	}

	public void render(Spectrum s, float vis, Theme theme, boolean darken, float time, float dt) {
		if (s.beatCount != lastBeat) {
			lastBeat = s.beatCount;
			if (shockwaves.size() < 10) {
				shockwaves.add(new float[] {0f, s.beatCount});
			}
		}
		for (int i = shockwaves.size() - 1; i >= 0; i--) {
			float[] w = shockwaves.get(i);
			w[0] += dt;
			if (w[0] > 1.6f) {
				shockwaves.remove(i);
			}
		}
		spin += dt * (0.03f + s.loudness * 0.05f + s.beat * 0.12f);

		MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		if (darken) {
			shade(buffers.getBuffer(SHADE), s, vis, theme, time);
			buffers.endBatch(SHADE);
		}
		VertexConsumer vc = buffers.getBuffer(GLOW);
		horizonGlow(vc, s, vis, theme, time);
		stars(vc, s, vis, theme, time);
		spectrumRing(vc, s, vis, theme, time);
		waveRibbon(vc, s, vis, theme, time);
		zenith(vc, s, vis, theme, time);
		buffers.endBatch(GLOW);
	}

	/** A translucent dome that dims the sky so the colors pop, even at noon. */
	private void shade(VertexConsumer vc, Spectrum s, float vis, Theme theme, float time) {
		int slices = 32, stacks = 10;
		theme.color(time * 0.02f, rgb);
		float r = rgb[0] * 0.06f, g = rgb[1] * 0.06f, b = rgb[2] * 0.08f + 0.02f;
		for (int j = 0; j < stacks; j++) {
			float lat0 = -0.35f + 1.35f * j / stacks, lat1 = -0.35f + 1.35f * (j + 1) / stacks;
			float a0 = vis * shadeAlpha(lat0, s), a1 = vis * shadeAlpha(lat1, s);
			float y0 = (float) Math.sin(lat0 * Math.PI / 2) * R, y1 = (float) Math.sin(lat1 * Math.PI / 2) * R;
			float c0 = (float) Math.cos(lat0 * Math.PI / 2) * R, c1 = (float) Math.cos(lat1 * Math.PI / 2) * R;
			for (int i = 0; i < slices; i++) {
				float p0 = TAU * i / slices, p1 = TAU * (i + 1) / slices;
				v(vc, sin(p0) * c0, y0, cos(p0) * c0, r, g, b, a0);
				v(vc, sin(p1) * c0, y0, cos(p1) * c0, r, g, b, a0);
				v(vc, sin(p1) * c1, y1, cos(p1) * c1, r, g, b, a1);
				v(vc, sin(p0) * c1, y1, cos(p0) * c1, r, g, b, a1);
			}
		}
	}

	private static float shadeAlpha(float lat, Spectrum s) {
		float h = Math.max(0f, lat);
		return 0.45f + 0.35f * h - s.beat * 0.08f;
	}

	/** Soft light along the horizon that breathes with the bass. */
	private void horizonGlow(VertexConsumer vc, Spectrum s, float vis, Theme theme, float time) {
		int segments = 64;
		float a = (0.10f + s.bass * 0.35f + s.beat * 0.25f) * vis;
		float top = 10f + s.bass * 14f + s.beat * 6f;
		float rr = R * 1.02f;
		for (int i = 0; i < segments; i++) {
			float p0 = TAU * i / segments, p1 = TAU * (i + 1) / segments;
			theme.color(i / (float) segments * 2f + time * 0.04f, rgb);
			float x0 = sin(p0) * rr, z0 = cos(p0) * rr, x1 = sin(p1) * rr, z1 = cos(p1) * rr;
			v(vc, x0, -6f, z0, rgb[0], rgb[1], rgb[2], 0f);
			v(vc, x1, -6f, z1, rgb[0], rgb[1], rgb[2], 0f);
			v(vc, x1, 0f, z1, rgb[0], rgb[1], rgb[2], a);
			v(vc, x0, 0f, z0, rgb[0], rgb[1], rgb[2], a);
			v(vc, x0, 0f, z0, rgb[0], rgb[1], rgb[2], a);
			v(vc, x1, 0f, z1, rgb[0], rgb[1], rgb[2], a);
			v(vc, x1, top, z1, rgb[0], rgb[1], rgb[2], 0f);
			v(vc, x0, top, z0, rgb[0], rgb[1], rgb[2], 0f);
		}
	}

	/** The main event: a full circle of spectrum bars standing on the horizon. */
	private void spectrumRing(VertexConsumer vc, Spectrum s, float vis, Theme theme, float time) {
		float half = TAU / BARS * 0.5f;
		float base = -1f;
		float punch = 1f + s.beat * 0.12f;
		for (int i = 0; i < BARS; i++) {
			// Mirror the bands so the ring is seamless: bass meets bass, treble meets treble.
			int band = i < Spectrum.BANDS ? i : BARS - 1 - i;
			float level = s.bands[band];
			float h = (1.2f + (float) Math.pow(level, 1.6) * 30f) * punch;
			float peakH = 1.2f + (float) Math.pow(s.peaks[band], 1.6) * 30f;
			float phi = spin + TAU * (i + 0.5f) / BARS;

			theme.color(band / (float) Spectrum.BANDS * 0.85f + time * 0.03f, rgb);
			float r = rgb[0], g = rgb[1], b = rgb[2];
			float hot = Math.max(0f, level - 0.6f) * 2.5f;

			// Wide soft glow behind the bar.
			tangentQuad(vc, phi, half * 2.4f, R * 1.005f, base, base + h * 1.1f,
					r, g, b, 0.08f * vis * (0.5f + level), r, g, b, 0f);
			// The bar itself, darker at the root and white-hot at the tip when loud.
			float tr = mix(r, 1f, hot * 0.3f), tg = mix(g, 1f, hot * 0.3f), tb = mix(b, 1f, hot * 0.3f);
			tangentQuad(vc, phi, half * 0.78f, R, base, base + h,
					r * 0.45f, g * 0.45f, b * 0.45f, 0.75f * vis, tr, tg, tb, 0.85f * vis);
			// Floating peak marker.
			tangentQuad(vc, phi, half * 0.78f, R, base + peakH + 0.3f, base + peakH + 0.75f,
					tr, tg, tb, 0.8f * vis, tr, tg, tb, 0.8f * vis);
			// Faint reflection under the horizon (visible over oceans).
			tangentQuad(vc, phi, half * 0.78f, R, base - h * 0.4f, base,
					r, g, b, 0f, r * 0.6f, g * 0.6f, b * 0.6f, 0.3f * vis);
		}
	}

	/** An oscilloscope ribbon wrapped around the sky. */
	private void waveRibbon(VertexConsumer vc, Spectrum s, float vis, Theme theme, float time) {
		int n = Spectrum.WAVE_POINTS;
		float y = 20f + s.mid * 3f;
		float rr = R * 0.98f;
		float amp = 7f;
		float thick = 0.22f + s.treble * 0.25f;
		float a = vis * (0.35f + s.loudness * 0.65f);
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float w0 = s.wave[mirror(i, n)] * amp, w1 = s.wave[mirror(j, n)] * amp;
			float p0 = -spin * 0.6f + TAU * i / n, p1 = -spin * 0.6f + TAU * (i + 1) / n;
			theme.color(0.5f + i / (float) n + time * 0.05f, rgb);
			float r = mix(rgb[0], 1f, 0.35f), g = mix(rgb[1], 1f, 0.35f), b = mix(rgb[2], 1f, 0.35f);
			float x0 = sin(p0) * rr, z0 = cos(p0) * rr, x1 = sin(p1) * rr, z1 = cos(p1) * rr;
			// Core line plus a wider faint halo.
			v(vc, x0, y + w0 - thick, z0, r, g, b, a);
			v(vc, x1, y + w1 - thick, z1, r, g, b, a);
			v(vc, x1, y + w1 + thick, z1, r, g, b, a);
			v(vc, x0, y + w0 + thick, z0, r, g, b, a);
			v(vc, x0, y + w0 - thick * 5f, z0, r, g, b, 0f);
			v(vc, x1, y + w1 - thick * 5f, z1, r, g, b, 0f);
			v(vc, x1, y + w1, z1, r, g, b, a * 0.25f);
			v(vc, x0, y + w0, z0, r, g, b, a * 0.25f);
			v(vc, x0, y + w0, z0, r, g, b, a * 0.25f);
			v(vc, x1, y + w1, z1, r, g, b, a * 0.25f);
			v(vc, x1, y + w1 + thick * 5f, z1, r, g, b, 0f);
			v(vc, x0, y + w0 + thick * 5f, z0, r, g, b, 0f);
		}
	}

	private static int mirror(int i, int n) {
		int half = n / 2;
		return i < half ? i * 2 : (n - 1 - i) * 2;
	}

	/** A radial spectrum "sun" straight overhead, with shockwave rings on every beat. */
	private void zenith(VertexConsumer vc, Spectrum s, float vis, Theme theme, float time) {
		float inner = 5f + s.bass * 4.5f + s.beat * 2.5f;
		float rot = -spin * 1.6f;

		// Glowing core.
		int seg = 48;
		theme.color(time * 0.05f, rgb);
		float ca = (0.25f + s.bass * 0.5f + s.beat * 0.35f) * vis;
		for (int i = 0; i < seg; i++) {
			float p0 = TAU * i / seg, p1 = TAU * (i + 1) / seg;
			float cr = inner * 0.92f;
			v(vc, 0f, ZENITH, 0f, 1f, 1f, 1f, ca);
			v(vc, 0f, ZENITH, 0f, 1f, 1f, 1f, ca);
			v(vc, sin(p1) * cr, ZENITH, cos(p1) * cr, rgb[0], rgb[1], rgb[2], ca * 0.6f);
			v(vc, sin(p0) * cr, ZENITH, cos(p0) * cr, rgb[0], rgb[1], rgb[2], ca * 0.6f);
			// Halo fading out past the core.
			float hr = inner * 2.6f;
			v(vc, sin(p0) * cr, ZENITH, cos(p0) * cr, rgb[0], rgb[1], rgb[2], ca * 0.5f);
			v(vc, sin(p1) * cr, ZENITH, cos(p1) * cr, rgb[0], rgb[1], rgb[2], ca * 0.5f);
			v(vc, sin(p1) * hr, ZENITH, cos(p1) * hr, rgb[0], rgb[1], rgb[2], 0f);
			v(vc, sin(p0) * hr, ZENITH, cos(p0) * hr, rgb[0], rgb[1], rgb[2], 0f);
		}

		// Rays.
		float half = TAU / BARS * 0.36f;
		for (int i = 0; i < BARS; i++) {
			int band = i < Spectrum.BANDS ? i : BARS - 1 - i;
			float level = s.bands[band];
			float len = 0.8f + (float) Math.pow(level, 1.2) * 24f;
			float p = rot + TAU * (i + 0.5f) / BARS;
			theme.color(0.35f + band / (float) Spectrum.BANDS * 0.85f + time * 0.03f, rgb);
			float r0 = inner + 0.6f, r1 = r0 + len;
			float a = vis * (0.55f + level * 0.45f);
			v(vc, sin(p - half) * r0, ZENITH, cos(p - half) * r0, rgb[0], rgb[1], rgb[2], a);
			v(vc, sin(p + half) * r0, ZENITH, cos(p + half) * r0, rgb[0], rgb[1], rgb[2], a);
			v(vc, sin(p + half * 0.4f) * r1, ZENITH, cos(p + half * 0.4f) * r1, rgb[0], rgb[1], rgb[2], a * 0.15f);
			v(vc, sin(p - half * 0.4f) * r1, ZENITH, cos(p - half * 0.4f) * r1, rgb[0], rgb[1], rgb[2], a * 0.15f);
		}

		// Shockwaves.
		int ring = 96;
		for (float[] w : shockwaves) {
			float t = w[0] / 1.6f;
			float radius = inner + (1f - (1f - t) * (1f - t)) * 70f;
			float fade = (1f - t) * (1f - t) * vis * 0.8f;
			float width = 0.6f + t * 2.5f;
			theme.color(w[1] * 0.13f + time * 0.05f, rgb);
			for (int i = 0; i < ring; i++) {
				float p0 = TAU * i / ring, p1 = TAU * (i + 1) / ring;
				float ri = radius - width, ro = radius + width;
				v(vc, sin(p0) * ri, ZENITH, cos(p0) * ri, rgb[0], rgb[1], rgb[2], 0f);
				v(vc, sin(p1) * ri, ZENITH, cos(p1) * ri, rgb[0], rgb[1], rgb[2], 0f);
				v(vc, sin(p1) * radius, ZENITH, cos(p1) * radius, rgb[0], rgb[1], rgb[2], fade);
				v(vc, sin(p0) * radius, ZENITH, cos(p0) * radius, rgb[0], rgb[1], rgb[2], fade);
				v(vc, sin(p0) * radius, ZENITH, cos(p0) * radius, rgb[0], rgb[1], rgb[2], fade);
				v(vc, sin(p1) * radius, ZENITH, cos(p1) * radius, rgb[0], rgb[1], rgb[2], fade);
				v(vc, sin(p1) * ro, ZENITH, cos(p1) * ro, rgb[0], rgb[1], rgb[2], 0f);
				v(vc, sin(p0) * ro, ZENITH, cos(p0) * ro, rgb[0], rgb[1], rgb[2], 0f);
			}
		}
	}

	/** Sparkles that twinkle with the treble and flare on beats. */
	private void stars(VertexConsumer vc, Spectrum s, float vis, Theme theme, float time) {
		for (int i = 0; i < STARS; i++) {
			float dx = starDir[i * 3], dy = starDir[i * 3 + 1], dz = starDir[i * 3 + 2];
			float twinkle = 0.5f + 0.5f * (float) Math.sin(time * 3f + starPhase[i]);
			float level = s.bands[starBand[i]];
			float a = vis * Math.min(1f, 0.08f + level * twinkle * 0.9f + s.beat * 0.45f);
			if (a < 0.01f) {
				continue;
			}
			float size = 0.25f + level * 0.55f + s.beat * 0.45f;
			// Tangent basis facing the camera at the origin.
			float ux = dz, uz = -dx;
			float ul = (float) Math.sqrt(ux * ux + uz * uz);
			ux /= ul;
			uz /= ul;
			float vx = -dy * uz, vy = uz * dx - ux * dz, vz = dy * ux;
			float cx = dx * R * 0.99f, cy = dy * R * 0.99f, cz = dz * R * 0.99f;
			theme.color(starPhase[i] / TAU + time * 0.02f, rgb);
			float r = mix(rgb[0], 1f, 0.6f), g = mix(rgb[1], 1f, 0.6f), b = mix(rgb[2], 1f, 0.6f);
			// Four-pointed sparkle: a thin vertical and a thin horizontal diamond.
			float l = size * 2.2f, w = size * 0.35f;
			v(vc, cx + vx * l, cy + vy * l, cz + vz * l, r, g, b, a);
			v(vc, cx + ux * w, cy, cz + uz * w, r, g, b, a);
			v(vc, cx - vx * l, cy - vy * l, cz - vz * l, r, g, b, a);
			v(vc, cx - ux * w, cy, cz - uz * w, r, g, b, a);
			v(vc, cx + ux * l, cy, cz + uz * l, r, g, b, a);
			v(vc, cx + vx * w, cy + vy * w, cz + vz * w, r, g, b, a);
			v(vc, cx - ux * l, cy, cz - uz * l, r, g, b, a);
			v(vc, cx - vx * w, cy - vy * w, cz - vz * w, r, g, b, a);
		}
	}

	/** A vertical quad on the cylinder of radius rr, centered on angle phi. */
	private static void tangentQuad(VertexConsumer vc, float phi, float half, float rr, float y0, float y1,
			float r0, float g0, float b0, float a0, float r1, float g1, float b1, float a1) {
		float xa = sin(phi - half) * rr, za = cos(phi - half) * rr;
		float xb = sin(phi + half) * rr, zb = cos(phi + half) * rr;
		v(vc, xa, y0, za, r0, g0, b0, a0);
		v(vc, xb, y0, zb, r0, g0, b0, a0);
		v(vc, xb, y1, zb, r1, g1, b1, a1);
		v(vc, xa, y1, za, r1, g1, b1, a1);
	}

	private static void v(VertexConsumer vc, float x, float y, float z, float r, float g, float b, float a) {
		vc.addVertex(x, y, z).setColor(clamp(r), clamp(g), clamp(b), clamp(a));
	}

	private static float clamp(float v) {
		return v < 0f ? 0f : Math.min(1f, v);
	}

	private static float mix(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float sin(float a) {
		return (float) Math.sin(a);
	}

	private static float cos(float a) {
		return (float) Math.cos(a);
	}
}
