# TesMano Project Instructions

## Project priorities

- The project is TesMano, based on MateDroid.
- Prefer reliability over speed.
- Preserve useful upstream functionality and maintain upstream mergeability.
- Keep changes focused and avoid unrelated dependency, Android Gradle Plugin, Gradle, or Kotlin upgrades.
- Read current official documentation instead of guessing APIs or platform behavior.
- Make logical, reviewable commits.

## Engineering standards

- Add or update tests for behavioral changes.
- Run `./gradlew test`, `./gradlew lint`, and `./gradlew assembleDebug` before declaring a task complete.
- Do not disable tests, suppress lint, or conceal failures to obtain a green build.
- Never add release signing credentials or other secrets to the repository.

## Backend and security boundaries

- Do not alter Oracle Cloud, TeslaMate, TeslaMateApi, or PostgreSQL.
- Treat all production backend interactions as read-only.
- Never request, store, or commit real credentials.
- Never log `Authorization` header values, tokens, passwords, or equivalent secrets.
- Do not add vehicle-control functionality.
- Do not integrate the Tesla Fleet API in v1.

## Product rules

- TesMano uses a dark-only UI.
- Use the `America/New_York` IANA time zone correctly; never substitute a fixed `EST` offset.
- The user's vehicle is a legacy Model Y Performance, option code PN01, with dark 19-inch Gemini wheels and red Performance calipers.
- Performance trim must not imply that the vehicle has Performance wheels.

## Primary real Android device

- The primary real device is the Samsung Galaxy Z Fold 8 Ultra. TesMano must be exceptionally optimized for it.
- Treat folded and unfolded layouts as distinct, first-class experiences. Do not merely stretch compact layouts on the unfolded screen.
- The real Galaxy Z Fold 8 Ultra is the final authority for layout, hinge and fold transitions, chart usability, map usability, performance, widget appearance, touch targets, typography, and animation smoothness.
- Emulators are development checks and do not override results from the real device.
