#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 JoanComasFdz
# SPDX-License-Identifier: GPL-3.0-only
#
# Sets up and publishes a release of this bundle, from a throwaway container:
#
#   docker run --rm -it --platform linux/amd64 -v "$PWD/keys:/keys" ubuntu:24.04 \
#     bash -c 'apt-get update -qq && apt-get install -y -qq curl >/dev/null &&
#              bash <(curl -fsSL https://raw.githubusercontent.com/JoanComasFdz/baconreader-reseam-patches/main/scripts/publish.sh) v0.1.0'
#
# It creates the bundle signing key in ./keys (or reuses the one there), prints its public key,
# stores the key as the repository secret BUNDLE_SIGNING_KEY_B64, and, when given a tag such as
# v0.1.0, tags main with it. The tag starts the release workflow, which builds, signs and
# publishes the bundle. Without a tag it stops after the secret.
#
# Ubuntu 24.04 because the reseam CLI needs glibc 2.39 or newer.
#
# GitHub login: set GH_TOKEN to a token that can write the repository's contents and secrets, or
# log in with the one-time code it shows.

set -euo pipefail

REPO=JoanComasFdz/baconreader-reseam-patches
KEYS=/keys
KEY=$KEYS/bundle-signing.key
TAG=${1:-}

step() { printf '\n\033[1;33m==> %s\033[0m\n' "$*"; }

step "Installing tools"
apt-get update -qq
apt-get install -y -qq ca-certificates curl git openssl >/dev/null
curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
  -o /usr/share/keyrings/githubcli-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
  >/etc/apt/sources.list.d/github-cli.list
apt-get update -qq
apt-get install -y -qq gh >/dev/null

# The CLI that matches the Reseam plugin version the bundle builds with.
version=$(curl -fsSL "https://raw.githubusercontent.com/$REPO/main/settings.gradle.kts" |
  sed -n 's/.*id("app.reseam.workspace") version "\(.*\)".*/\1/p')
curl -fsSL -o /usr/local/bin/reseam "https://cdn.reseam.app/engine/v$version/reseam-linux-x64"
chmod +x /usr/local/bin/reseam
echo "Reseam CLI $version"

step "Signing key"
mkdir -p "$KEYS"
if [ -f "$KEY" ]; then
  echo "Reusing $KEY"
else
  reseam bundle keygen --out "$KEY" >/dev/null
  echo "Created $KEY"
fi
# The key file is an Ed25519 seed; wrapping it in PKCS#8 lets OpenSSL derive the public key.
public=$({ printf '\x30\x2e\x02\x01\x00\x30\x05\x06\x03\x2b\x65\x70\x04\x22\x04\x20'; cat "$KEY"; } |
  openssl pkey -inform DER -pubout -outform DER | tail -c 32 | od -A n -t x1 | tr -d ' \n')
echo "$public" >"$KEYS/bundle-signing.pub"
echo "Public key: $public"
echo "Back up keys/bundle-signing.key. A bundle signed with a new key asks every user to trust it again."

step "GitHub login"
if [ -z "${GH_TOKEN:-}" ]; then
  gh auth login --hostname github.com --git-protocol https --scopes repo
fi
gh auth setup-git

step "Repository secret BUNDLE_SIGNING_KEY_B64"
base64 -w0 "$KEY" | gh secret set BUNDLE_SIGNING_KEY_B64 --repo "$REPO"

if [ -z "$TAG" ]; then
  step "Done. Run again with a tag, such as v0.1.0, to publish a release."
  exit 0
fi

step "Tagging main as $TAG"
git clone -q --depth 1 "https://github.com/$REPO" /tmp/repo
git -C /tmp/repo tag "$TAG"
git -C /tmp/repo push -q origin "$TAG"

step "Done"
echo "The release workflow is running: https://github.com/$REPO/actions"
echo "When it finishes, users can add the bundle with:"
echo "https://reseam.app/add/?url=https%3A%2F%2Fgithub.com%2FJoanComasFdz%2Fbaconreader-reseam-patches"
