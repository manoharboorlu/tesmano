# TesMano Project Instructions

## Working approach

- Work quietly and efficiently. Do not narrate routine reads, searches, builds, or obvious intermediate steps.
- Prefer targeted searches and file reads; do not repeatedly reread the entire repository.
- Interrupt the user only for a required manual action, meaningful architecture decision, security-sensitive decision, or blocking failure.
- Final task reports must be concise: changes, validation, limitations, and next step.
- Correctness and reliability outrank implementation speed. Keep the app buildable at every phase.
- Do not combine several large phases into one implementation pass or begin the next phase without explicit approval.
- Preserve useful MateDroid functionality; avoid broad refactors unless a documented problem justifies them.
- Prefer sound existing architecture and dependencies. Do not blindly upgrade AGP, Gradle, Kotlin, Compose, or libraries.
- Make logical, focused, reviewable commits; avoid giant mixed-purpose commits.

## Established knowledge

- Use repository documentation as the source of truth unless relevant code or dependencies changed.
- Do not re-audit TeslaMateApi when `docs/TESLAMATE_API_CONTRACT.md` is current.
- Do not rediscover documented timezone or vehicle findings unnecessarily; re-verify only affected areas after upstream or code changes.
- Read current official documentation before relying on a changed platform or API behavior.

## Product and vehicle rules

- TesMano uses a dark-only UI.
- The vehicle source of truth is a legacy/pre-Juniper Model Y Performance: PN01 Stealth Grey, 19-inch dark Gemini wheels, red Performance brake calipers, and a Performance spoiler.
- Generation, trim, paint, wheels, brakes, and spoiler are independent dimensions.
- Performance trim must not imply Performance wheels. PN01 or Uberturbine21 alone must not imply Juniper; P74D indicates Performance trim, not generation.
- Manual overrides are authoritative for the dimensions they control. Never hard-code user-specific logic such as `if user == Manohar`.

## Primary real Android device

- Samsung Galaxy Z Fold 8 Ultra is the primary real-device authority.
- Folded and unfolded experiences are first-class; do not merely stretch compact layouts on the unfolded display.
- Real-device behavior overrides emulator assumptions for final layout, hinge/fold transitions, chart and map usability, performance, widget appearance, touch targets, typography, and animation decisions.
- The Fold8 Ultra cover/main emulator targets are the primary layout approximations; Pixel 9 targets are secondary compatibility checks, and `TesMano_Foldable_API_36` is for posture/transition behavior only.

## Backend safety and security

- Do not modify Oracle Cloud, TeslaMate, TeslaMateApi, or PostgreSQL. Do not expose PostgreSQL to Android.
- TesMano v1 is read-only toward the backend: no Tesla Fleet API, vehicle controls, or vehicle wake-up functionality.
- Never request, store, or commit real credentials. Never commit credentials or place secrets in BuildConfig or test fixtures; sanitize fixtures.
- Never log `Authorization` values, passwords, tokens, or equivalent secrets.
- No trust-all TLS or cleartext HTTP in release. Production signing must never silently fall back to debug signing.

## Time and quality gates

- Never use fixed `EST`; use named timezone rules such as `America/New_York`.
- Preserve absolute instants through network, storage, and domain layers; convert to local time only for display or calendar logic. DST correctness is release-critical.
- Add focused tests for behavioral changes, run relevant tests, lint, and `assembleDebug`, and smoke-test when practical.
- Run `./gradlew test`, `./gradlew lint`, and `./gradlew assembleDebug` before declaring a task complete.
- Never disable failing tests, blindly suppress lint, conceal failures to obtain green, or call a phase complete with core validation failing.

## Upstream and Git

- `origin` is the TesMano fork; `upstream` is `vide/matedroid`.
- Keep TesMano changes upstream-mergeable where practical.
- Do not push unless explicitly instructed.
