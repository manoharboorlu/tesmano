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
| `TesMano_Phone_API_36` (Pixel 9) | Secondary phone compatibility check only. |

Pixel 9 and Pixel 9 Pro Fold dimensions must not be used as TesMano's primary visual authority.

## Physical-device preparation

When the Galaxy Z Fold8 Ultra is connected, record `adb shell wm size` and `adb shell wm density` in both relevant folded and unfolded states if Android reports different values. Those measured logical values supersede these emulator assumptions.
