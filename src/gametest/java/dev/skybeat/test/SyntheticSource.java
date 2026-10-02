package dev.skybeat.test;

import java.util.Random;

import dev.skybeat.audio.AudioSource;
import dev.skybeat.audio.PcmTrack;

/** A procedurally generated 120 BPM groove, so the visualizer can be tested without audio hardware. */
final class SyntheticSource implements AudioSource {
	private static final int RATE = 44100;
	private final PcmTrack track;
	private final long start = System.nanoTime();

	SyntheticSource() {
		PcmTrack.Writer writer = new PcmTrack.Writer(RATE, 1);
		Random random = new Random(7);
		double beat = 0.5;
		double[] chord = {220.0, 261.63, 329.63, 392.0};
		double[] arp = {440.0, 523.25, 659.25, 783.99, 659.25, 523.25};
		float hat = 0f, prevNoise = 0f;
		for (int i = 0; i < RATE * 60; i++) {
			double t = i / (double) RATE;
			double inBeat = t % beat;
			int beatIndex = (int) (t / beat);
			double kickFreq = 45 + 90 * Math.exp(-inBeat * 30);
			double kick = Math.sin(2 * Math.PI * kickFreq * inBeat) * Math.exp(-inBeat * 7);
			float noise = random.nextFloat() * 2f - 1f;
			double snare = beatIndex % 2 == 1 ? noise * Math.exp(-inBeat * 18) * 0.5 : 0;
			double inEighth = t % (beat / 2);
			hat = noise - prevNoise;
			prevNoise = noise;
			double hats = hat * Math.exp(-inEighth * 60) * 0.25;
			double pad = 0;
			for (double f : chord) {
				pad += ((t * f) % 1.0 * 2 - 1) * 0.04;
			}
			double sixteenth = beat / 4;
			double lead = Math.sin(2 * Math.PI * arp[(int) (t / sixteenth) % arp.length] * t) * Math.exp(-(t % sixteenth) * 10) * 0.18;
			writer.accept((float) ((kick * 0.9 + snare + hats + pad + lead) * 0.6));
		}
		track = writer.track();
		track.markComplete();
	}

	@Override
	public PcmTrack track() {
		return track;
	}

	@Override
	public double position() {
		return ((System.nanoTime() - start) / 1.0e9) % 60.0;
	}

	@Override
	public float gain() {
		return 1f;
	}

	@Override
	public boolean finished() {
		return false;
	}

	@Override
	public String name() {
		return "Synthetic Groove";
	}
}
