#!/bin/bash

set -e

sed -i "s|quarkus-roq-cli:[^:]*:runner@fatjar|quarkus-roq-cli:${CURRENT_VERSION}:runner@fatjar|g" jbang-catalog.json
git add jbang-catalog.json
git commit -m "Update jbang-catalog.json for ${CURRENT_VERSION}"