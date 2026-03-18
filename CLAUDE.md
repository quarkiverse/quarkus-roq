# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Quarkus Roq is a static site generator built as a set of Quarkus extensions. It processes Markdown/AsciiDoc/HTML content with FrontMatter headers, Qute templates, and data files (JSON/YAML) to produce static websites. Java 17+ target, built with Java 21 and Maven.

## Build Commands

```shell
mvn verify                          # Build, test, and auto-format code
mvn clean install                   # Install all modules locally
mvn clean install -DskipTests       # Install without tests
mvn verify -pl roq-frontmatter/deployment  # Build/test a single module
cd blog && mvn quarkus:dev          # Run the example blog with live reload (check if port 8080 is not already started)
```

Integration tests (including the blog) require the `it` profile:
```shell
mvn verify -Pit
```

Native image tests:
```shell
mvn verify -Pit -Dnative
```

Code formatting is enforced automatically by `mvn verify`. Run it before submitting PRs.

## Architecture

### Quarkus Extension Pattern

Every module follows the standard Quarkus extension split:
- **deployment/** - Build-time processors (`@BuildStep` methods) that produce build items
- **runtime/** - Runtime code, recorders, and configuration

### Module Dependency Flow

```
roq-common (project discovery, Jackson config, path utils)
    ↓
roq-data (JSON/YAML data file processing → typed beans via @DataMapping)
    ↓
roq-frontmatter (FrontMatter parsing, template processing, page publishing)
    ↓                    ↑
roq-plugin/* (markup, tagging, series, sitemap, etc.)
    ↓
roq-generator (static site export - crawls pages, writes HTML to disk)
    ↓
roq (aggregator extension combining all of the above)

roq-editor (Dev UI editor for content)
roq-theme/* (default, resume - bundled CSS/JS/templates)
roq-testing (test utilities, @RoqAndRoll annotation)
```

### Key Processors (Build-Time Pipeline)

1. **RoqProjectProcessor** (roq-common) - Discovers project root, produces `RoqProjectBuildItem`
2. **RoqJacksonProcessor** (roq-common) - Configures JSON/YAML mappers, produces `RoqJacksonBuildItem`
3. **RoqDataReaderProcessor** (roq-data) - Scans `data/` directory for YAML/JSON files
4. **RoqFrontMatterScanProcessor** (roq-frontmatter) - Scans `content/`, `templates/`, `public/`, `static/` directories
5. **RoqFrontMatterDataProcessor** (roq-frontmatter) - Resolves layouts, URLs, collections, pagination; filters drafts/future
6. **RoqFrontMatterPublishProcessor** (roq-frontmatter) - Publishes final pages as `GeneratedStaticResourceBuildItem`
7. **RoqGeneratorProcessor** (roq-generator) - Exports static files to disk

### Plugin System

Plugins live under `roq-plugin/` and extend Roq via build items:
- `RoqFrontMatterQuteMarkupBuildItem` - Register markup sections (e.g., `{#markdown}...{/markdown}`)
- `RoqFrontMatterHeaderParserBuildItem` - Register FrontMatter header parsers
- `RoqFrontMatterDataModificationBuildItem` - Modify template data during processing

Available plugins: markdown, asciidoc, asciidoc-jruby, asciidoc-common, diagram, tagging, series, lunr, sitemap, aliases, qrcode, faker.

### Roq Site Directory Structure (used by the blog/)

```
content/          # Pages and collections (posts/, pages/, etc.)
data/             # JSON/YAML data files
templates/        # Qute layout templates
public/           # Static assets served as-is
web/              # Web Bundler assets (CSS/JS)
```

## Testing

- Integration tests use `@QuarkusTest` with REST Assured for HTTP assertions and AssertJ
- The `roq-testing` module provides `@RoqAndRoll` annotation for Roq-specific test setup
- Integration test modules are at: `roq/integration-tests/`, `roq-generator/integration-tests/`, `roq-plugin/asciidoc/integration-tests/`, `roq-theme/resume/integration-tests/`

## Key Packages

- `io.quarkiverse.roq` - Root package
- `io.quarkiverse.roq.deployment` - Common deployment (roq-common)
- `io.quarkiverse.roq.data` - Data file processing
- `io.quarkiverse.roq.frontmatter` - FrontMatter core
- `io.quarkiverse.roq.generator` - Static generator
- `io.quarkiverse.roq.plugin.*` - Individual plugins
