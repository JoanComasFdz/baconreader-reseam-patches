<!-- SPDX-FileCopyrightText: 2026 JoanComasFdz -->
<!-- SPDX-License-Identifier: GPL-3.0-only -->

# Patcher-specific: release (Reseam)

## Branches and pull requests

- Work branches from `main`; PRs go to `main`.
- A commit's type doesn't release anything: releases are started by hand.

## Releases

`.github/workflows/release.yml`, from the Reseam template, builds and signs the bundle and publishes it with its `patches.json` on a GitHub release, for a `v*` tag.

- **Signing key:** the repository secret `BUNDLE_SIGNING_KEY_B64` (`base64 -w0 ~/.reseam/bundle-signing.key`). A bundle signed with another key asks every user to trust it again.
- **Release:** Actions → release → Run workflow, on `main`, with the new version, such as `v0.1.2`. It tags `main`, then releases. Or push the tag: `git tag v0.1.2 && git push origin v0.1.2`. Cloud sessions can't push tags; with GitHub access, start the workflow with the workflow dispatch API.
- `scripts/publish.sh` does both from a throwaway Docker container, and creates the key on the first run (the `docker run` command is at its top). It keeps the key in `./keys`; back that folder up.

Users add the bundle to Reseam Manager with this link, then check the signing key it shows against the one in the README:

```text
https://reseam.app/add/?url=https%3A%2F%2Fgithub.com%2FJoanComasFdz%2Fbaconreader-reseam-patches
```

## Reseam updates

While Reseam is on 0.x, Reseam Manager loads only bundles built with its own minor version, so a new Reseam breaks the published bundle until it's rebuilt. `.github/workflows/reseam-update.yml` checks for one every day at 06:17 UTC, or on Actions → Reseam update → Run workflow. When there is a newer `app.reseam.workspace` plugin with its CLI, it:

1. Puts the new version in `settings.gradle.kts` and the README badge.
2. Builds the bundle with it and patches the clean BaconReader Premium 6.1.4 APK with every patch (`--preset all`). It stops unless the run ends with `skipped=0` and `failed=0`.
3. Commits the new version to main and runs the release workflow with the next patch version, such as `v0.1.3`.

When it stops, GitHub emails the failed run, and the bundle needs a fix by hand. It needs, once:

- The repository secret `CLEAN_APK_URL`: a private download link to the clean APK, never a public one.
- Pushes to main from GitHub Actions. If main is protected, let `github-actions[bot]` bypass the rule.
