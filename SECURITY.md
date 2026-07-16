# Security policy

## Supported versions

Security fixes are provided for the latest 3.x source and release. The retired Flutter 2.x implementation is not maintained.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability. Use GitHub Private Vulnerability Reporting:

<https://github.com/admin0330/liquid-music-android/security/advisories/new>

Include the affected commit/version, Android version, reproduction steps, impact, and a minimal proof of concept. Remove personal music metadata, complete lyrics, authentication material, server configuration, signing files, and device identifiers before attaching logs.

The maintainer will acknowledge a usable report, investigate it, and coordinate disclosure after a fix is available. Please do not test against infrastructure or devices you do not own or have permission to use.

## Security boundaries

- Music playback is local-only. Network access is reserved for explicit application updates.
- The updater accepts HTTPS and same-origin redirects only, validates a strict manifest, and verifies APK size, SHA-256, package, version, and signer.
- Android's system package installer performs the final install confirmation; the app cannot silently install an update.
- Formal releases must retain the historical signing certificate and be built only in a protected environment.
- Keystores, `key.properties`, `.env*`, private keys, `known_hosts`, server handoff files, music, artwork, and lyrics are excluded from source control.

If a signing key is suspected to be exposed, stop all releases immediately and treat it as an incident; simply changing the key will break the existing Android upgrade chain.
