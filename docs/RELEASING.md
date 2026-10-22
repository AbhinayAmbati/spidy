# Releasing Spidey

Releases are built only by GitHub Actions (`.github/workflows/release.yml`). Nothing is built or
signed on a developer machine.

## What a release contains

| File | Purpose |
|---|---|
| `spidy.apk` | Installable APK (arm64-v8a). Also the `spidey-apk` workflow artefact. |
| `spidy.aab` | Android App Bundle, for Play Console. |
| `spidey-sbom.cdx.json` | CycloneDX software bill of materials of the dependencies in the release. |
| `SHA256SUMS` | SHA-256 of the three files above. |

The R8 `mapping.txt` of the build is kept as the private workflow artefact `spidey-mapping`
(90 days) and is deliberately not published. Keep it for as long as you support that version:
it is what turns a crash trace back into readable names.

## Cutting a release

1. Merge everything for the release into `main` and wait for **Android CI** and **Supply chain
   checks** to be green on it.
2. Update `CHANGELOG.md`.
3. Tag `main` and push the tag:

   ```bash
   git switch main && git pull
   git tag -a v0.2.0 -m "Spidey 0.2.0"
   git push origin v0.2.0
   ```

   The tag must be `vMAJOR.MINOR.PATCH` or `vMAJOR.MINOR.PATCH-prerelease` (for example
   `v0.2.0-rc.1`, published as a GitHub pre-release). Anything else is rejected. The tagged
   commit must be on `main`; the workflow refuses otherwise.
4. Watch **Actions > Release APK**. On success the GitHub Release is created with the four files.
5. Verify a download:

   ```bash
   sha256sum --check SHA256SUMS
   apksigner verify --print-certs spidy.apk   # certificate must be the release key's
   ```

### Versions

* `versionName` is the tag without the `v`.
* `versionCode = (major*1000000 + minor*1000 + patch)*10 + 9` for a stable tag and `+ 0` for a
  pre-release (`v0.2.0` is 20009, `v0.2.0-rc.1` is 20000). It grows with the version, so a
  rebuild of the same tag has the same code, and a pre-release never outranks the final release.
  Limits: major up to 200, minor and patch up to 999. Only one pre-release per `x.y.z` can be
  uploaded to Play; use a new patch number for a second one.
* A manual run (Actions > Release APK > Run workflow) does not read a tag; it uses the defaults
  in `app/build.gradle.kts` and produces only workflow artefacts (`spidey-apk`, `spidey-aab`,
  `spidey-sbom`), no GitHub Release. Bump those defaults when you change them by hand; the tag
  path overrides them with `-Pspidey.versionName` / `-Pspidey.versionCode`.

### What the workflow checks before it publishes

Import checks and unit tests; the pinned model hashes against Hugging Face; `apksigner` on the
APK and `jarsigner` on the AAB; that the version inside the APK matches the tag; the 16 KB
alignment of the native library; and a vulnerability scan of the SBOM. Any failure stops the
release. Nothing is published until all of them pass.

## Required repository secrets and variables

Settings > Secrets and variables > Actions.

Secrets (all four, or none; with none, the release is signed with the debug key and must not be
distributed):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.keystore` |
| `KEYSTORE_PASSWORD` | the keystore password |
| `KEY_ALIAS` | the key alias in the keystore |
| `KEY_PASSWORD` | the key password |

Variables (optional, strongly recommended on the main repository):

| Variable | Effect |
|---|---|
| `REQUIRE_RELEASE_SIGNING` = `true` | A tag build fails instead of falling back to the debug key. Forks that do not set it keep the debug-key fallback. |
| `RELEASE_CERT_SHA256` | SHA-256 fingerprint of the release certificate. The build fails if the APK is signed by anything else, which catches a swapped or wrong key. |

The workflow also relies on branch protection for `main` (require pull request reviews, require
code-owner review per `.github/CODEOWNERS`, require the status checks above) and on
tag protection for `v*` so only maintainers can create release tags. Both are repository
settings and cannot be set from the repository.

## Keystore

Create a release key (once):

```bash
keytool -genkeypair -v -keystore release.keystore -storetype PKCS12 \
  -alias spidey -keyalg RSA -keysize 4096 -validity 9125
base64 -w0 release.keystore    # -> KEYSTORE_BASE64
```

Keep the keystore and its passwords in a password manager, outside the repository
(`*.keystore`, `*.jks` and `keystore.properties` are git-ignored). For a local signed build copy
`keystore.properties.example` to `keystore.properties`.

Print the fingerprint to set `RELEASE_CERT_SHA256`:

```bash
keytool -list -v -keystore release.keystore -alias spidey | grep 'SHA256:'
```

### Rotating the keystore

Android only accepts an update signed by the same key as the installed app, so a rotation is a
migration, not a swap.

* **Distributing through Google Play with Play App Signing:** Play holds the app signing key and
  your key is only the upload key. Create a new upload key and register it in Play Console
  (App integrity > App signing > Request upload key reset). Then replace the four secrets and
  `RELEASE_CERT_SHA256`.
* **Distributing the APK directly:** a new key cannot update existing installs unless the APK
  carries a proof-of-rotation. Generate it with
  `apksigner rotate --out lineage --old-signer --ks old.keystore --new-signer --ks new.keystore`,
  then sign with `apksigner sign --lineage lineage ...` (this needs a custom signing step; the
  current workflow signs through Gradle with one key). Plan this before the old key expires or leaks.
  If the old key is compromised, treat every installed copy as untrusted and announce the new
  signing certificate fingerprint through a channel you control.

In both cases: update the secrets, cut a pre-release tag, run the verification above, and only
then delete the old key material.

## Updating a model pin

The app downloads `spidey.cact` and `whistle.cact` on first launch and refuses any file whose size
or SHA-256 differs from `OnDeviceModels` in
`app/src/main/java/com/example/agent/core/ai/local/ModelSpec.kt`. The CI job
`Verify pinned model hashes` (and the release workflow) checks those pins against Hugging Face on every
push and weekly.

To change a model:

1. Upload or locate the new file on Hugging Face and open its page; copy the LFS **size** in bytes
   and **SHA-256** (the `hf` CLI shows both, or `curl -sI <resolve-url>` and read
   `X-Linked-Size` and `X-Linked-ETag`).
2. Edit `sizeBytes` and `sha256` together in `ModelSpec.kt` (and `url` if the repository or file
   moved).
3. Run `python3 tools/verify_model_pins.py` locally. It must print `ok` for both models.
4. Open a pull request. A pin change is a change to what every installation downloads, so it needs
   code-owner review.

If the check fails without any commit of yours, the model on Hugging Face was replaced or removed.
Do not just update the pin: find out why, and confirm the new file is the intended one.

Because the URL ends in `resolve/main/`, a new upload goes live for existing installs the moment it
is pushed, and they then fail the hash check until the app is updated. For a safe model rollout,
publish the model under a new file name or a pinned revision (`resolve/<commit>/...`) first, and ship
the pin with the app release that needs it.

## Lint baseline

Lint is blocking in CI only once `app/lint-baseline.xml` is committed (until then every existing
finding would fail every build). To create it:

1. Actions > **Android CI** > Run workflow, tick `update_lint_baseline`.
2. Download the `lint-baseline` artefact from the run and commit it as `app/lint-baseline.xml`.
3. Review it: the baseline accepts today's findings, it does not fix them. Triage the list over time
   and regenerate the file as findings are fixed so it only shrinks.

From then on `lintDebug` (CI) fails on any new issue; `lintVitalRelease` (part of every release build) already fails on fatal issues.

## Dependencies and the supply chain

* All versions are pinned in `gradle/libs.versions.toml`; Dependabot opens weekly pull requests for
  Gradle dependencies and GitHub Actions. Actions are pinned by commit SHA (the version is in the
  trailing comment), and Dependabot updates both.
* **Supply chain checks** (`.github/workflows/security-checks.yml`) generates the CycloneDX SBOM and
  scans it with OSV-Scanner on every push, pull request and weekly. A known vulnerability fails the
  job. To accept a finding with no fix yet, add it to `osv-scanner.toml` at the repository root:

  ```toml
  [[IgnoredVulns]]
  id = "GHSA-xxxx-xxxx-xxxx"
  ignoreUntil = 2026-12-31
  reason = "Not reachable: we never call the affected API."
  ```
* The same SBOM is attached to every release.

## Not done yet (follow-ups)

* **Gradle dependency locking / verification** (`gradle.lockfile`, `gradle/verification-metadata.xml`)
  needs a Gradle run to generate and could not be produced without a toolchain. Generate with
  `./gradlew dependencies --write-locks` and `./gradlew --write-verification-metadata sha256 help`
  and commit them; OSV-Scanner reads both.
* **Build provenance attestations** (`actions/attest-build-provenance`) need a public repository or
  GitHub Enterprise plan with attestations enabled.
* The `libc++_shared.so` shipped with the app is 16 KB page-aligned only when built with NDK r28 or
  newer; `tools/check_native_alignment.py` reports it as a warning. Pin `ndkVersion` once the runner
  image's NDK is known.
