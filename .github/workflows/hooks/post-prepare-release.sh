#!/bin/bash

set -e

sed -i "s/:999-SNAPSHOT@/:${CURRENT_VERSION}@/g" jbang-catalog.json