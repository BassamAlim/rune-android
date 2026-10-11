<p align="center">
  <img src="docs/images/logo.svg" width="96" alt="Rune logo" />
</p>

<h1 align="center">Rune</h1>

<p align="center">
  <b>A deterministic password generator for Android.</b><br />
  One master password and a secret key become a different password for every site. No password is ever stored.
</p>

![The site list, a site's password, the site editor and settings](docs/images/screens.png)

<sub>Rendered from the app's own screens by its screenshot test, not captured on a phone.</sub>

The desktop app is in [`rune-desktop`](https://github.com/BassamAlim/rune-desktop) and produces
the same passwords. The algorithm and file formats are specified in that repo's `SPEC.md`;
`app/src/test/resources/vectors.json` is a copy of its shared test data.

## Features

- **Nothing to steal.** Passwords are worked out from your master password, a secret key held
  on the phone, and the site's name. They are never stored.
- **No internet permission.** Nothing Rune knows can leave the phone.
- **Fingerprint for every password.** Your master password is typed once at setup. After that
  each password asks for your fingerprint, with the master password as the fallback.
- **Autofill.** Rune fills login forms in apps and browsers directly, skipping the clipboard.
  In a browser it only offers a password on the site it belongs to.
- **Per-site settings.** Length, which kinds of character, and a counter you raise to get a
  new password for that site.
- **Same passwords on your desktop.** Set both apps up with the same master password and secret
  key; the four-character fingerprint shows they match.
- **Export and import.** The site list is one JSON file, shared with the desktop app.

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
3. Turn on autofill in Settings to have Rune fill login forms directly.

To use the same passwords on the desktop app, set it up with the same master password and type
in the secret key. Move the site list across with Export and Import in Settings.
