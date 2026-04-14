#!/bin/bash

set -e

sed -i "s|quarkus-roq-cli/[^/]*/quarkus-roq-cli-[^-]*-runner|quarkus-roq-cli/${CURRENT_VERSION}/quarkus-roq-cli-${CURRENT_VERSION}-runner|g" jbang-catalog.json
git add jbang-catalog.json
git commit -m "Update jbang-catalog.json for ${CURRENT_VERSION}"