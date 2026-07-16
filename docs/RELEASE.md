# Release and update runbook

This runbook separates public source validation from possession of the historical Android signing key.

## Repository topology

| Location | Responsibility | Secrets allowed |
|---|---|---|
| `admin0330/liquid-music-android` | Source, PRs, unit tests, lint, unsigned R8 verification | None |
| `admin0330/real-liquid-glass-android-demo` | Signed APK and GitHub Release | Signing secrets |
| `ym3861.cn/liquid-music-updates` | Mainland China APK mirror and `latest.json` | Aliyun SSH secrets in the distribution repository only |

Never publish an APK built by the source CI. Its Debug artifact uses the `.dev` applicationId, and its Release output is unsigned when no local key is configured.

## Required protected secrets

Keep these in a protected `production` environment in the distribution repository:

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`
- `ALIYUN_SSH_PRIVATE_KEY`
- `ALIYUN_SSH_HOST_KEY`
- `ALIYUN_SSH_HOST`
- `ALIYUN_SSH_USER`

`ALIYUN_SSH_HOST_KEY` must contain a complete pinned `known_hosts` line, not only a `SHA256:...` fingerprint. Confirm the line out of band against the recorded ED25519 fingerprint before saving it. The deploy user must not be `root`; restrict it to the update directory and the commands needed for atomic deployment.

Require manual approval for the production environment. Protect `v*` tags and `main`, disable arbitrary third-party Actions, and pin every Action to a full commit SHA.

## Version rules

- `versionCode` must strictly increase. Version 2.4.9 used code 18; 3.0.0 uses code 19.
- The source tag without its leading `v` must exactly equal `versionName` embedded in the APK.
- The formal applicationId must stay `io.github.admin0330.real_liquid_glass_demo`.
- The signing certificate SHA-256 must stay:

```text
621185c90ce4a8d95d531bc4ac936b0f54c029dddf910c60e0074342047fb523
```

Do not create a `v*` tag until the source commit has passed CI and the release workflow has been reviewed. A tag is an immutable release input; never reuse a version or replace its APK after publication.

## Build in the distribution workflow

1. Accept a tag using a strict expression such as `^v[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?$`.
2. Check out the public source repository at that exact tag.
3. Decode the keystore into `$RUNNER_TEMP` after `umask 077`.
4. Write root-level `key.properties`, with `storeFile` pointing to that absolute temporary JKS.
5. Build without caches that could serialize signing properties:

```bash
./gradlew --no-daemon --no-configuration-cache --no-build-cache \
  clean testDebugUnitTest lintRelease assembleRelease
```

6. Require `app/build/outputs/apk/release/app-release.apk`. Fail if only an `*-unsigned.apk` exists.
7. Always delete `key.properties` and the temporary keystore in an `if: always()` cleanup step.

All four signing secrets must be non-empty. Missing secrets are a hard failure; never fall back to a debug key.

## APK verification

Use the Android SDK tools from the same runner:

```bash
apksigner verify --verbose --print-certs app-release.apk
aapt dump badging app-release.apk
sha256sum app-release.apk
stat --printf='%s' app-release.apk
```

Verify all of the following before upload:

- v1/v2/v3 signature verification succeeds as applicable.
- Package name is the formal applicationId.
- APK `versionCode` and `versionName` equal the Gradle configuration and tag.
- Normalized certificate SHA-256 equals the historical fingerprint above.
- SHA-256 is 64 lowercase hexadecimal characters.
- Size is a positive integer and matches the uploaded object.

The app repeats size, hash, package, version, and signer checks on-device, but CI verification is still mandatory.

## Combined manifest

Create `latest.json` with `jq`, never by interpolating untrusted strings into a shell script. It must contain exactly nine keys:

```json
{
  "versionCode": 19,
  "versionName": "3.0.0",
  "apkUrl": "https://ym3861.cn/liquid-music-updates/liquid-music-v3.0.0.apk",
  "sha256": "<sha256>",
  "size": 12345678,
  "changelog": "更新说明",
  "version": "3.0.0",
  "apk_url": "liquid-music-v3.0.0.apk",
  "notes": "更新说明"
}
```

Validate with:

```bash
jq -e '
  (keys | sort) == ([
    "apkUrl", "apk_url", "changelog", "notes", "sha256",
    "size", "version", "versionCode", "versionName"
  ] | sort)
  and (.versionName == .version)
  and (.changelog == .notes)
  and (.sha256 | test("^[0-9a-f]{64}$"))
  and (.size > 0)
' latest.json
```

The absolute `apkUrl` and the legacy `apk_url` resolved relative to the manifest must identify the same HTTPS resource. The app rejects extra keys, URL credentials, HTTP, cross-origin redirects, and inconsistencies between new and legacy fields.

## Publication order

1. Create the immutable GitHub Release in the distribution repository and attach the verified APK, SHA-256 file, and combined manifest.
2. Upload the versioned APK and checksum to Aliyun under temporary names.
3. Recompute SHA-256 and size on the server.
4. Atomically rename the versioned files into place.
5. Upload `latest.json.tmp`, validate it on the server, then atomically rename it to `latest.json` last.
6. Read the manifest back through public HTTPS.
7. Download the public APK URL, verify Content-Length, size, SHA-256, package, version, and certificate again.

Versioned APKs should use immutable long-lived caching. `latest.json` should use `no-cache` or a short TTL. The APK URL must point directly to the Aliyun origin: GitHub asset URLs redirect across origins, while the in-app updater intentionally rejects cross-origin redirects.

Pass only the four Aliyun secrets to a mirror deployment job. Do not use `secrets: inherit`, which would unnecessarily expose signing secrets.

## Rollback

Never overwrite a broken versioned APK. To roll back availability:

1. Select the previous known-good immutable APK.
2. Recreate a combined manifest for that version using its original hash, size, changelog, and URL.
3. Validate and atomically replace only `latest.json`.
4. Verify the public manifest and APK again.

Android will not install a lower `versionCode` over a newer installed app. A true corrective release therefore needs a new, higher `versionCode`; manifest rollback only prevents additional users from receiving the bad release.
