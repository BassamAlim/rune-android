# Echo (Android)

A deterministic password generator. One master password and a secret key become a different
password for every site; no password is ever stored, and the app has no internet permission.
The desktop app is in `echo-desktop` and produces the same passwords.

The algorithm and data formats are specified in `echo-desktop/SPEC.md`.
`app/src/test/resources/vectors.json` is a copy of that repo's shared test data.

## Commands

```bash
./gradlew :app:assembleDebug        # build the APK (app/build/outputs/apk/debug/)
./gradlew :app:testDebugUnitTest    # unit tests, and screenshots of every screen
./gradlew :app:installDebug         # install on a connected phone
```

The screenshots land in `app/build/screenshots/`.

## Using it

1. Set up with a master password. Write down the secret key it shows you.
2. Add a site. Opening it asks for your fingerprint and shows the password.
3. Turn on autofill in Settings to have Echo fill login forms directly.

To use the same passwords on the desktop app, set it up with the same master password and type
in the secret key. The four-character fingerprint must match on both. Move the site list across
with Export and Import in Settings.
