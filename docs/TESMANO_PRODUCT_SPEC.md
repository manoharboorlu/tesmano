# TesMano product specification

TesMano is a private, read-only companion for an existing TeslaMate deployment. It is built for the Samsung Galaxy Z Fold 8 Ultra: the cover display supports focused, one-hand use; the main display supports deliberately composed workspaces rather than stretched compact screens.

## Product areas

- **Home:** current vehicle state, freshness, charging and key next actions.
- **Activity:** drives, charges and trips with clear list/detail flows.
- **Battery:** health, charging context and understandable confidence states.
- **Analytics:** durable, source-backed history and comparisons.
- **Lab:** explicitly experimental, read-only diagnostics isolated from core flows.

## Experience principles

- Existing TeslaMate servers only: connection setup is not account creation and never wakes or controls a vehicle.
- Offline and partial-sync states stay understandable; history sync is resumable and summary-first.
- The app remains dark-only, quiet and high-contrast. Privacy-sensitive data and credentials are never logged or exposed in product metadata.
- Dashboard and widgets share vehicle appearance resolution. The legacy Model Y Performance custom-asset path falls back to the correct legacy dark-Gemini image until artwork is bundled.

## Distribution direction

The intended private release path is: GitHub release/tag → automated tests → signed AAB → Google Play Internal Testing → the user's Google account and Fold8 Ultra. Play Console configuration and publishing remain final release-hardening work.
