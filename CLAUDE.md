# TesMano

Read these before editing:

@AGENTS.md
@docs/CLAUDE_CODE_HANDOFF.md
@docs/TESMANO_FEATURE_ROADMAP.md
@docs/TESLAMATE_API_CONTRACT.md

## Product

TesMano is a personal, dark-only Tesla analytics Android app derived from MateDroid. It is read-only toward TeslaMate/TeslaMateApi and is deliberately data-first and privacy-first. The Samsung Galaxy Z Fold 8 Ultra is the sole product-device target; both its cover and unfolded layouts matter, and the physical device is the final visual authority.

## Mandatory boundaries

- Do not modify TeslaMate, TeslaMateApi, PostgreSQL, Oracle infrastructure, backend auth/security, or use Tesla Fleet API unless a future prompt explicitly authorizes it.
- Do not add vehicle controls, wake, lock, climate, or other commands.
- Never expose, log, commit, or copy credentials, tokens, signing material, or private keys.
- During physical testing, never uninstall TesMano, run `pm clear`, or clear app data. Use `./gradlew installDebug` for in-place upgrades.

## Development philosophy

- Correctness and transparent data semantics outrank feature breadth.
- Keep the summary-first/on-demand-detail design: never bulk-fetch thousands of Drive/Charge details for an analytics surface.
- Unknown is not zero. Preserve **MEASURED**, **DERIVED**, and **ESTIMATED** provenance.
- Manual intent beats automatic derivation. Derived classifications must naturally re-evaluate when their inputs change.
- Preserve absolute instants through storage; use named time zones, never fixed `EST`.
- Retain the approved Home hierarchy; do not casually redesign it or add oversized cards.

## Git workflow

- Work on `develop`; inspect status and recent log before edits.
- Make one focused feature commit per approved phase.
- Do not push until the user/ChatGPT explicitly approves it.
- Amend the current phase only when specifically requested; never rewrite pushed history.

## Build environment

- Repository: `/Users/boorlu/Developer/tesmano`
- JDK 17: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`
- Android SDK: `/Users/boorlu/Library/Android/sdk`

Typical command:

```sh
env JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ANDROID_HOME="/Users/boorlu/Library/Android/sdk" \
  ANDROID_SDK_ROOT="/Users/boorlu/Library/Android/sdk" \
  PATH="/Users/boorlu/Library/Android/sdk/platform-tools:$PATH" \
  ./gradlew test lint assembleDebug installDebug
```

`installDebug` upgrades the installed `com.manohar.tesmano` app in place and preserves its local data.

## Durable implementation rules

- Localize new user-visible strings in all existing resource locales; do not hardcode them in Kotlin.
- TeslaMateApi already returns user-configured distance, speed, temperature, and efficiency units. `UnitFormatter` labels them; it must not convert them again.
- Vehicle generation, trim, paint, wheels, brakes, and spoiler are independent. Never infer Juniper from PN01, P74D, or Uberturbine21; Performance trim does not imply Performance wheels.
- Night Fury’s preferred legacy Model Y custom asset is resolved through the shared resolver, never user-specific logic. See `docs/VEHICLE_MODEL.md`.
