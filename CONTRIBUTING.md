# Contributing

Thank you for improving Liquid Music Android.

## Product boundary

Contributions must keep music functionality strictly local. Do not add Apple authentication/catalog APIs, DRM, Subsonic/Navidrome, online music search/download, cloud playlists, telemetry, advertising, or bundled commercial media. Network code belongs only to the explicit application-update feature.

Do not copy Apple Music, Bunpod, Telegram, or another project's code/assets without a compatible license. Independent implementations may take visual inspiration while preserving this project's own components and identity.

## Development workflow

1. Fork the repository and branch from `main`.
2. Keep changes focused and preserve the Clean/MVVM boundaries described in `docs/ARCHITECTURE.md`.
3. Use the design-system spacing, glass, color, and motion tokens instead of local magic values where practical.
4. Keep all touch targets at least 44dp and account for status/navigation insets plus the floating Dock/mini player.
5. Add tests for parsing, persistence, update security, queue ordering, or other deterministic logic.
6. Run before opening a pull request:

```bash
./gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease
```

7. Explain behavior changes, manual test coverage, Android versions used, and any remaining device-specific limitation in the PR.

## Code expectations

- Kotlin and Jetpack Compose only; no Flutter/Dart layer.
- Coroutines and Flow for asynchronous/reactive work; avoid process-global unmanaged scopes.
- Real MediaStore/Room data only—no fake songs, placeholder actions, TODO implementations, or demo screens.
- Playback media items must remain local `content://`, `file://`, or app-private file URIs.
- User-visible failures need a useful recovery path and must not crash the app.
- Avoid decoding full-size artwork for palette extraction or creating per-row infinite animations.
- Keep Room schemas under `app/schemas` and provide migrations when incrementing the database version.

## Tests and copyrighted data

Tests must generate synthetic bytes/text. Never commit a full commercial song, copyrighted lyric, album cover, user library database, APK signing material, or server handoff document. A tiny original/self-authored LRC fixture is acceptable.

## Commit and PR hygiene

- Use descriptive imperative commit messages.
- Do not mix formatting or unrelated refactors into a functional fix.
- Update README/architecture/release documentation when behavior or operational assumptions change.
- Never force-push another contributor's branch or rewrite a published release tag.

For security-sensitive reports, follow `SECURITY.md` instead of opening a public issue.
