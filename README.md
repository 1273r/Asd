# Roblox Physics (NeoForge 1.21.1)

Ports Roblox's character physics to Minecraft. **Install on both client and server.**

## Build
1. Java 21.
2. Copy `gradlew`, `gradlew.bat` and the `gradle/` folder from the official NeoForge MDK
   (https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle) into this folder.
3. `./gradlew build` -> `build/libs/robloxphysics-1.0.0.jar`  (or `./gradlew runClient` to test).
4. If `neo_version` in `gradle.properties` doesn't resolve, use any 21.1.x release.

## Unit conversion
1 stud = 0.28 m = 0.28 blocks. Config values are in studs so you can tune against Roblox docs.

| Property | Roblox | In Minecraft | Vanilla MC |
|---|---|---|---|
| Gravity | 196.2 studs/s² | 54.94 blocks/s² | 32 |
| WalkSpeed | 16 studs/s | 4.48 blocks/s | 4.32 |
| JumpHeight | 7.2 studs | 2.02 blocks | 1.25 |
| Terminal velocity | ~200 studs/s (approx.) | 56 blocks/s | 78 |

## What is modelled
- Roblox gravity with **no air drag** (vanilla applies a 0.98 drag every tick), integrated so jump apex is exact.
- JumpHeight / JumpPower jumps, no jump cooldown (hold space to keep hopping).
- WalkSpeed with near-instant ground acceleration, and strong mid-air steering.
- No sprint, no fall damage. Non-player mobs get a gravity attribute scaled to Roblox gravity.
- Skipped (vanilla behaviour) while swimming, climbing, flying, gliding, riding, or with levitation/slow falling.

## Approximations / not modelled
- Ground/air acceleration and terminal velocity are tuned guesses, not values from Roblox source. Tweak in `robloxphysics-common.toml`.
- Ice/slime/soul-sand friction is ignored on the ground; ropes/ladders/swimming use vanilla rules.
- No Roblox part physics (Rapier-style constraint solver, rotational inertia, etc.); this is the **Humanoid character** model only.
- Overspeed momentum (e.g. knockback) decays toward WalkSpeed instead of being preserved in the air.
- Dropped items, falling blocks and projectiles keep vanilla gravity.
