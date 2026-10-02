package dev.skybeat.audio;

import org.jspecify.annotations.Nullable;

/** Something the visualizer can listen to. */
public interface AudioSource {
	/** The decoded audio, or null while it is still loading. */
	@Nullable
	PcmTrack track();

	/** Playback position in seconds. */
	double position();

	/** How loud this source is for the player right now, 0..1. */
	float gain();

	boolean finished();

	String name();
}
