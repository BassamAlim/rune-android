<p align="center">
  <img src="docs/images/logo.svg" width="96" alt="Rune logo" />
</p>

<h1 align="center">Rune</h1>

<p align="center">
  <b>A deterministic password generator for Android.</b><br />
  One master password becomes a different password for every site. No password is ever stored.
</p>

![The site list, a site's password, the site editor and settings](docs/images/screens.png)

<sub>Rendered from the app's own screens by its screenshot test, not captured on a phone.</sub>

The desktop app is in [`rune-desktop`](https://github.com/BassamAlim/rune-desktop) and produces
the same passwords. The algorithm and file formats are specified in that repo's `SPEC.md`;
`app/src/test/resources/vectors.json` is a copy of its shared test data.

## Features

- **Nothing to steal.** Passwords are worked out from your master password and the site's
  name. They are never stored. Moving in from another manager, a site can keep its old password,
  encrypted under your master password, until you change it to Rune's.
- **Syncs with your desktop over Wi-Fi.** No server and no account: the two devices find each other on your network and exchange the site list, encrypted with a key only your master password gives.
- **Internet only for logos and names.** Rune asks each site for its own `/favicon.ico` and homepage, no third-party service. Passwords and keys never leave the phone, though the sites you fetch logos from (and your DNS) can see that you have an account there.
- **Fingerprint for every password.** Your master password is typed once at setup. After that
  each password asks for your fingerprint, with the master password as the fallback.
- **Autofill.** Rune fills login forms in apps and browsers directly, skipping the clipboard.
  In a browser it only offers a password on the site it belongs to.
- **Per-site settings.** Length, which kinds of character, and a counter you raise to get a
  new password for that site.
- **Same passwords on your desktop.** Set both apps up with the same master password; the
  four-character fingerprint shows they match.
- **Export and import.** The site list is one JSON file, shared with the desktop app.

## Commands

```bash
./gradlew :app:assembleDebug        # build the APK (app/build/outputs/apk/debug/)
./gradlew :app:testDebugUnitTest    # unit tests, and screenshots of every screen
./gradlew :app:installDebug         # install on a connected phone
```

The screenshots land in `app/build/screenshots/`.

## Using it

1. Set up with a generated master password (or your own). Learn it; Rune asks for it every two
   weeks so you don't forget.
2. Add a site. Opening it asks for your fingerprint and shows the password.
3. Turn on autofill in Settings to have Rune fill login forms directly.

To use the same passwords on the desktop app, set it up with the same master password. Move the site list across with Export and Import in Settings.
