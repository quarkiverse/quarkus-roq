# Blog Antoroq - FOR DEMONSTRATION ONLY

## ⚠️ IMPORTANT - DO NOT MERGE

This directory contains a **test/demo site** for the `roq-antoroq` theme during code review.

**This directory should NOT be merged into the main repository.**

It exists solely to:
1. Demonstrate the Antoroq theme functionality
2. Allow reviewers to test the theme in a real environment
3. Show how the theme renders actual documentation content

## For Reviewers

To test the theme:

```bash
cd blog-antora
mvn quarkus:dev
```

Then visit http://localhost:8080

## After PR Approval

This directory should be deleted before merging. The theme itself is in:

```
roq-theme/antoroq/
├── deployment/
├── runtime/
└── integration-tests/
```

## Contents

### Root Pages

The root-level pages (`index.adoc`, `quarkus-roq-data.adoc`, etc.) are copies of the official documentation from `docs/modules/ROOT/pages/`. They are included here to demonstrate the navbar with real content.

**Note:** The official documentation source is at `docs/modules/ROOT/pages/`. These copies exist solely for demonstration purposes and include added frontmatter for navigation ordering.

### Demo Navigation (`content/docs/`)

The `content/docs/` directory demonstrates the dynamic hierarchical navigation features:

- **Hierarchy**: `index.adoc` files become parent nodes for their directory
- **Ordering**: Uses `nav_order` frontmatter and numeric filename prefixes (`01-`, `02-`)
- **Custom titles**: Uses `nav_title` for shorter navigation labels
- **External links**: Uses `nav_external` for links outside the site

These demo pages are placeholders to showcase the navigation tree structure and are not meant to be comprehensive documentation.

### Configuration

- `config/application.properties` - Theme configuration (`site.theme=roq-antoroq`)
- Site uses the `roq-antoroq` theme which is based on Antora UI Default (MPL 2.0)
