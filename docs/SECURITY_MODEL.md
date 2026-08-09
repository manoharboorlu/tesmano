# Security model

TesMano is read-only toward TeslaMate/TeslaMateApi. It does not store backend credentials in source, fixtures, logs, `BuildConfig`, or backups.

Server URLs and optional Basic-auth usernames remain ordinary DataStore preferences. API tokens and Basic-auth passwords are encrypted with AES-256-GCM using a key generated and retained by Android Keystore. This follows Android's current `KeyGenParameterSpec` guidance instead of the deprecated AndroidX Security Crypto master-key helpers. Existing plaintext settings are migrated only after encryption succeeds; the old secret value is then removed. If encryption/decryption fails, no legacy secret is erased.

The existing backup rules exclude the settings DataStore, including encrypted ciphertext. Debug HTTP logs redact `Authorization`; the client selects exactly one authorization mode, with Bearer taking precedence over Basic.

Release accepts only HTTPS endpoints and always uses platform TLS certificate and hostname validation. Debug builds may opt into invalid certificates or cleartext for local development only; those paths are compiled out of release behavior.

References: [Android Keystore KeyGenParameterSpec](https://developer.android.com/reference/kotlin/android/security/keystore/KeyGenParameterSpec), [Android security guidance](https://developer.android.com/privacy-and-security/security-tips).
