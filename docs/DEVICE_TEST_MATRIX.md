# Device test matrix

The physical Samsung Galaxy Z Fold8 Ultra is the final authority. Emulators approximate its display/window behavior.

## Official visual targets

| Surface | Official panel | Physical target | Emulator logical result |
|---|---:|---:|---:|
| Cover | 6.5 in, 1080 × 2520 | `TesMano_ZFold8Ultra_Cover_API36` | 1080 × 2520 at 420 dpi; 411 × 960 dp portrait |
| Main / unfolded | 8.0 in, 2504 × 2256 | `TesMano_ZFold8Ultra_Main_API36` | 2256 × 2504 at 420 dpi; 859 × 954 dp portrait |

Both custom AVDs use the installed Android 16 / API 36 Google APIs ARM64 image. They have software navigation and no hardware navigation buttons. They deliberately do not invent Samsung hinge or cutout behavior.

## Local targets

| Target | Purpose |
|---|---|
| `TesMano_ZFold8Ultra_Cover_API36` | Primary compact/cover layout approximation. |
| `TesMano_ZFold8Ultra_Main_API36` | Primary unfolded-width layout approximation. |
| `TesMano_Foldable_API_36` (Pixel 9 Pro Fold) | Fold/unfold posture, resize, activity-continuity, and folding-feature testing; not the visual baseline. Closed: 1080 × 2424 at 390 dpi. Opened: 2076 × 2152 at 390 dpi. |

Pixel 9 is not a TesMano product or compatibility target. The generic Pixel foldable remains only for posture behavior; its dimensions must not drive UI decisions.

## Emulator process hygiene

Launch a smoke-test AVD detached from the command runner (for example, `nohup emulator -avd <name> >/tmp/tesmano-emulator.log 2>&1 </dev/null &` followed by `disown` where available). After each smoke test, use `adb -s <serial> emu kill` before final Git checks. Do not leave emulator stdio attached to a command wrapper.

## Physical-device preparation

When the Galaxy Z Fold8 Ultra is connected, record `adb shell wm size` and `adb shell wm density` in both relevant folded and unfolded states if Android reports different values. Those measured logical values supersede these emulator assumptions.
