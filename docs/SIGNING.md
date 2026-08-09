# Release signing

Debug builds use normal Android debug signing.

Release builds never fall back to the debug keystore. Before packaging a release APK or AAB, set these environment variables in the local release/CI secret store:

- `RELEASE_KEYSTORE_PATH` — path to the release keystore file
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

The release packaging task fails with a clear message if any value or the keystore file is missing. Do not commit a keystore, credentials, or encoded key material. Release key ownership, rotation, recovery, and final artifact verification remain Phase 10 work.
