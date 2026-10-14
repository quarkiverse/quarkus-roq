#!/bin/bash

set -e

sed -i "s|quarkus-roq-cli:[^:]*:runner@fatjar|quarkus-roq-cli:${CURRENT_VERSION}:runner@fatjar|g" jbang-catalog.json
git add jbang-catalog.json
git commit -m "Update jbang-catalog.json for ${CURRENT_VERSION}"

# Auto-create a release post if one doesn't already exist for this version
RELEASE_DIR="blog/content/posts/releases"
EXISTING=$(find "${RELEASE_DIR}" -maxdepth 2 -name "index.md" -exec grep -l "released-version: ${CURRENT_VERSION}\$" {} + 2>/dev/null || true)

if [ -z "${EXISTING}" ]; then
  DATE=$(date +%Y-%m-%d)
  POST_DIR="${RELEASE_DIR}/roq-${CURRENT_VERSION}"
  mkdir -p "${POST_DIR}"
  cat > "${POST_DIR}/index.md" <<EOF
---
title: "Roq ${CURRENT_VERSION}"
layout: release
description: ""
author: ia3andy
tags: release, patch
date: ${DATE} 12:00:00 +0000
released-version: ${CURRENT_VERSION}
---

See the [GitHub Release](https://github.com/quarkiverse/quarkus-roq/releases/tag/${CURRENT_VERSION}) for details.
EOF
  git add "${POST_DIR}/index.md"
  git commit -m "Add release post for ${CURRENT_VERSION}"
fi
