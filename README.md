# Skybeat — music skybox visualizer (Fabric 1.21.11)

Turns the sky into a music visualizer. When music plays, the sky dims and fills with:

- **A 360° spectrum ring** of glowing bars around the horizon, with floating peak caps and reflections
- **A radial spectrum "sun"** straight overhead, with **shockwave rings** on every beat
- **An oscilloscope ribbon** of the waveform wrapped around the sky
- **Sparkles** that twinkle with the treble and flare on kicks
- **A horizon glow** that breathes with the bass

All of it sits behind terrain, mobs, water and clouds, like a real skybox. It fades in when music starts and fades out when it stops.

## What it reacts to

- **Minecraft's own soundtrack** (background music, menu music)
- **Jukebox music discs.** The effect gets stronger as you get closer to the jukebox.
- **Your own songs:** put `.ogg` or `.wav` files in `.minecraft/config/skybeat/music/` and press **N**.

Skybeat decodes the same audio file the game is playing and analyzes it in sync with playback (2048-point FFT, 64 log-spaced bands, beat detection). It doesn't record your speakers or microphone.

## Controls

All keys can be rebound under *Options → Controls → Skybeat Visualizer*.

| Key | Action |
| --- | --- |
| K | Toggle the visualizer |
| J | Next color theme (Rainbow, Synthwave, Aurora, Ember, Ocean, Candy) |
| H | Toggle sky dimming |
| N | Play the next song from `config/skybeat/music` |
| M | Stop that song |

Settings are saved to `config/skybeat.properties`.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.11**.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and `skybeat-1.0.0.jar` in `.minecraft/mods`.

It's client-side only, so it works on any server.

## Building

```
./gradlew build                        # jar ends up in build/libs/
./gradlew runProductionClientGametest  # boots the game and screenshots the visualizer
```

Every push also builds on GitHub Actions. You can download the jar from the run's **skybeat** artifact.
