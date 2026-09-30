# Roblox Physics (NeoForge 1.21.1)

Makes Minecraft feel like Roblox: Roblox character physics, the Roblox third-person camera, live
Lua/Luau scripting with a Roblox-style API, and the F9 Developer Console.
**Install on both client and server.** (The client still gets the camera, physics and client console
on servers without the mod.)

## Controls

| Key | Action |
|---|---|
| **F5** | Toggle the Roblox camera (third person, free mouse) / first person |
| Right mouse (hold + drag) | Orbit the camera |
| Right mouse (quick click) | Use item / place block at the cursor |
| Left mouse | Attack / break what's under the cursor |
| Mouse wheel, **I** / **O** | Zoom (zoom all the way in = first person) |
| Arrow keys ← → | Turn the camera |
| **Left Alt** | Shift Lock (cursor locked, over-the-shoulder camera, character faces the camera) |
| **F9** | Developer Console |

All keys can be rebound in *Options → Controls → Roblox Physics*.

### Roblox camera (F5)
Works like Roblox's default "Classic" camera: the cursor is free, WASD moves relative to the camera, and
the character smoothly turns to face the direction it is walking (`Humanoid.AutoRotate`, using
`CFrame:Lerp`). Clicks go to whatever is under the cursor, and the character turns to face the target
when it attacks or uses an item. The camera clamps pitch to ±80° and zooms between
`StarterPlayer.CameraMinZoomDistance` and `CameraMaxZoomDistance` (default 0.5-128 studs, starting at
12.5).

## Developer Console (F9)
- **Client / Server** tabs, each with its own log (Output / Information / Warning / Error filters, search,
  timestamps, Clear).
- The command line at the bottom runs Lua in the selected context. Up/Down browse history.
  `= expr` or a bare expression prints the result.
- **Server** execution and the server log require operator permission (or being the singleplayer host).
- Built-in commands: `reload` re-runs the script files, `reset` restarts a stuck script context.

## Scripting
Scripts are Luau, run by an embedded Lua 5.2 VM (LuaJ) with a translation layer for Luau syntax:
compound assignment (`+=` ...), `continue`, type annotations, `type` declarations, `::` casts,
`` `interpolated {strings}` `` and `//`.

Script files run automatically:
- **Server scripts**: `<world>/scripts/server/*.lua` (or `.luau`) run as `Script`s in `ServerScriptService`.
- **Client scripts**: `.minecraft/config/robloxphysics/scripts/client/*.lua` run as `LocalScript`s in
  `Players.LocalPlayer.PlayerScripts`.

Scripts can also be created live, e.g. from the console:
```lua
local s = Instance.new("Script")
s.Source = "print('hi from', script:GetFullName())"
s.Parent = game.ServerScriptService
```

### API
Positions and sizes are in **studs** (1 stud = 0.28 blocks by default, see `workspace.StudSize`).

- Globals: `game`, `workspace`, `script`, `print`, `warn`, `error`, `wait`, `delay`, `spawn`, `tick`,
  `time`, `typeof`, `require`, `loadstring`, `task.wait/spawn/defer/delay/cancel`, `shared`
- Types: `Instance.new`, `Vector3`, `CFrame` (`new`, `Angles`, `fromOrientation`, `lookAt`,
  `fromAxisAngle`, `fromMatrix`, operators, `Lerp`, `ToObjectSpace`, ...), `Color3`, `BrickColor`, `Enum`
- Luau library extras: `math.clamp/sign/round`, `string.split`, `table.find/clear/create`
- `Instance`: `Name`, `Parent`, `ClassName`, `FindFirstChild`, `WaitForChild`, `GetChildren`,
  `GetDescendants`, `IsA`, `Destroy`, `Clone`, `GetFullName`, attributes,
  `GetPropertyChangedSignal`, `Changed`, `ChildAdded`, `ChildRemoved`, `AncestryChanged`, `Destroying`, ...
- `Players`: `GetPlayers`, `LocalPlayer` (client), `GetPlayerFromCharacter`, `PlayerAdded`, `PlayerRemoving`
- `Player`: `Character`, `UserId`, `Kick`, `LoadCharacter`, `Chatted`, `CharacterAdded`
- Characters: every player and mob is a `Model` in `Workspace` with a `Humanoid`,
  `HumanoidRootPart` and `Head` (`Position`/`CFrame` teleport, `AssemblyLinearVelocity` flings).
- `Humanoid`: `Health`/`MaxHealth` (Roblox 100-point scale), `WalkSpeed`, `JumpHeight`, `JumpPower`,
  `UseJumpPower`, `AutoRotate`, `Jump`, `MoveTo` (mobs), `TakeDamage`, `Died`, `HealthChanged`.
  A player's movement values are replicated to that player's client physics.
- `Workspace.Gravity` (replicated), `StarterPlayer` character/camera defaults
- `RunService`: `Heartbeat`, `Stepped`, `RenderStepped` (client), `IsServer`, `IsClient`
- Client: `workspace.CurrentCamera` (`CFrame`, `FieldOfView`, `CameraType = Enum.CameraType.Scriptable`,
  `ScreenPointToRay`), `UserInputService` (`InputBegan`, `InputEnded`, `IsKeyDown`, ...)
- `ModuleScript` + `require`

Each `runSync` step has a 10 second budget; a runaway loop gets
`Script timeout: exhausted allowed execution time`, like Roblox.

Studio backend: `StudioService` exposes Explorer/Properties/editing operations (tree, props, set,
create, delete, duplicate, reparent, script source, run, command bar with `Selection`) to operators
over the network (`"studio"` message). It has no in-game UI yet.

## Easter egg
Say **wifies** in chat.

## Build
1. Java 21.
2. `./gradlew build` -> `build/libs/robloxphysics-2.0.0.jar` (LuaJ is bundled inside).
3. `./gradlew runClient` to test.

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
- No Roblox part physics; this is the **Humanoid character** model only.
- Lua is Lua 5.2 semantics underneath (e.g. no Luau native vectors, integer division only for simple operands).
- Overspeed momentum (e.g. knockback) decays toward WalkSpeed instead of being preserved in the air.
