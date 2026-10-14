---
title: Rune privacy policy
---

# Rune privacy policy

_Last updated: 14 October 2026_

Rune is a password generator for Android, made by Bassam Alim. It has no servers and no
accounts, and it collects no data.

## What stays on your phone

- **Your master password** is typed only at setup and when Rune asks you to confirm it. It is
  never stored. The key derived from it is kept on the phone, encrypted by an Android Keystore
  key that only your fingerprint (or your master password) unlocks.
- **Your site list** (site names, logins, password settings, and any old passwords you chose to
  keep, which are encrypted under your master password) is stored only in Rune's private storage
  on the phone. Android backup is turned off for Rune, so it is not copied to Google's backup.
- **Generated passwords** are worked out when you ask for them and are never stored or sent
  anywhere, except as described below.

## What leaves your phone

- **Site logos and names.** When you add a site, Rune requests that site's own `/favicon.ico` and
  homepage directly from the site. No third-party service is involved. The site (and your DNS
  provider) can see that the request came from your IP address.
- **Sync with Rune for desktop.** If you pair Rune with the desktop app, the two devices talk
  directly over your local Wi-Fi network. The site list is exchanged encrypted with a key that
  only your master password gives. A password is sent to the desktop only after you approve the
  request on the phone with your fingerprint.
- **Autofill and clipboard.** When you fill a password with autofill, Android passes it to the
  app or browser you are logging in to. If you copy a password instead, other apps may be able
  to read it while it is on the clipboard.
- **Export.** If you export your site list, the file goes wherever you choose to save or share
  it.

Rune contains no analytics, advertising, crash reporting or tracking of any kind, and shares no
data with third parties.

## Permissions

- **Internet**: only for the logo and name requests above.
- **Local network**: only to find and sync with Rune for desktop.
- **Biometrics**: to ask for your fingerprint before showing a password.
- **Installed apps (launcher apps only)**: so you can link a site to an app and show its icon.
  This list never leaves the phone.

## Deleting your data

Uninstalling Rune deletes everything it stored. You can also clear its data from Android's
app settings.

## Children

Rune is not directed at children under 13.

## Changes and contact

Changes to this policy will be posted on this page. Questions: bassamalim@gmail.com or
[open an issue](https://github.com/BassamAlim/rune-android/issues).
