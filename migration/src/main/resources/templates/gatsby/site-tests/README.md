# Site tests

These are the tests of the Gatsby site that ran against the served site. The migration to Roq kept them, and they
still work: they now run against the Roq site, which `serve-roq.js` serves from `../target/roq/` on the port they used
with `gatsby serve` (${port}).

To run them, build the site, then run them from this directory:

```
QUARKUS_ROQ_GENERATOR_BATCH=true mvn package quarkus:run -DskipTests
cd site-tests
npm ci
${commands}
```

`package.json` and its lock file are the Gatsby site's, so `npm ci` installs exactly what the tests used before; only
`gatsby serve` was replaced with `serve-roq.js`. A `gatsby-config.js` here is a copy that the tests read; the site does
not use it any more.

These tests are meant to be temporary. Once they are converted to Java tests, for example with `@RoqAndRoll` and
Playwright, delete this directory.
