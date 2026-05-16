---
name: quarkus-roq-data
description: "Use JSON/YAML data files in your Qute templates and articles with type-safe mapping via CDI."
guide: https://docs.quarkiverse.io/quarkus-roq/dev/quarkus-roq-data.html
---

# Quarkus Roq Data

Roq Data processes JSON and YAML data files into CDI beans, making them accessible in Qute templates and injectable in Java code with type safety.

### Data Files

Place JSON (`.json`) or YAML (`.yml`, `.yaml`) files in the `data/` directory. Each file becomes a CDI bean named after the file (without extension).

**Example** (`data/authors.yaml`):
```yaml
ia3andy:
  name: Andy
  url: https://github.com/ia3andy
john:
  name: John Doe
  url: https://example.com
```

**Template access**:
```html
{cdi:authors.ia3andy.name}
```

### Data Directories

A directory inside `data/` is automatically grouped into a single `JsonObject` CDI bean, with each file as a key (filename without extension). Individual files are also available as separate beans.

**Example**:
```
data/heroes/
  batman.yaml    # { "name": "Batman", "city": "Gotham" }
  superman.yaml  # { "name": "Superman", "city": "Metropolis" }
```

This produces:
- `@Named("heroes")` `JsonObject` with keys `batman` and `superman`
- `@Named("heroes/batman")` `JsonObject` for the individual file

**Template access**:
```html
{cdi:heroes.batman.name}
{cdi:heroes.superman.city}
```

### Type-Safe Mapping

Use `@DataMapping` on a record or class to create a typed CDI bean from a data file:

```java
@DataMapping("authors")
public record Authors(Map<String, Author> authors) {

    public Author get(String id) {
        return authors.get(id);
    }

    public record Author(String name, String url) {}
}
```

Inject in Java code:
```java
@Inject
Authors authors;
```

- `value` — data file or directory name (without extension for files, must match an entry in `data/`)
- `type` — determines how data is loaded (see `Type` enum below, default: `OBJECT_FILE`)
- `required` — `true` if the data file/directory must exist (default: `false`)

### Type Enum

The `type` attribute on `@DataMapping` controls how data is loaded:

| Type | Source | Constructor | Description |
|------|--------|-------------|-------------|
| `OBJECT_FILE` | Single file | Direct fields | Default. Maps a file to a typed object |
| `ARRAY_FILE` | Single file (array) | `List<T>` | Maps a root-level array file to a list |
| `ARRAY_DIR` | Directory | `List<T>` | Maps each file in a directory to a list item |
| `OBJECT_DIR` | Directory | `Map<String, T>` | Maps each file to a map entry (filename as key) |

### Array Data (from file)

For data files with a root-level JSON/YAML array, use `type = Type.ARRAY_FILE`:

**Example** (`data/contributors.json`):
```json
[
  { "name": "Alice", "role": "maintainer" },
  { "name": "Bob", "role": "contributor" }
]
```

```java
@DataMapping(value = "contributors", type = DataMapping.Type.ARRAY_FILE)
public record Contributors(List<Contributor> contributors) {

    public record Contributor(String name, String role) {}
}
```

The record must have a single constructor parameter of type `List<T>`.

Note: `parentArray = true` is deprecated but still works as an alias for `Type.ARRAY_FILE`.

### Directory Mapping

For data directories where each file represents an item, use `ARRAY_DIR` or `OBJECT_DIR`:

**Example** (`data/heroes/batman.yaml`, `data/heroes/superman.yaml`):

**As a list** (`ARRAY_DIR`):
```java
@DataMapping(value = "heroes", type = DataMapping.Type.ARRAY_DIR)
public record HeroList(List<Hero> list) {
    public record Hero(String name, String city) {}
}
```

**As a map** (`OBJECT_DIR`, filename without extension as key):
```java
@DataMapping(value = "heroes", type = DataMapping.Type.OBJECT_DIR)
public record HeroMap(Map<String, Hero> map) {
    public record Hero(String name, String city) {}
}
```

The record must have a single constructor parameter: `List<T>` for `ARRAY_DIR`, `Map<String, T>` for `OBJECT_DIR`.

### Dynamic Mapping

For untyped access, inject the raw JSON using `@Named`:

```java
@Inject
@Named("authors")
JsonObject authors;

@Inject
@Named("contributors")
JsonArray contributors;
```

### Qute Access

```html
{! Direct property access !}
{cdi:authors.ia3andy.name}

{! Iteration !}
{#for contributor in cdi:contributors.contributors}
  <span>{contributor.name} ({contributor.role})</span>
{/for}

{! Let bindings for convenience !}
{#let author=cdi:authors.get(page.data.author)}
  <a href="{author.url}">{author.name}</a>
{/let}
```

### Common Pitfalls

- **Name must match annotation value** — `@DataMapping("authors")` requires a file named `authors.yaml`/`.yml`/`.json` in `data/`, or a directory named `authors/` for `ARRAY_DIR`/`OBJECT_DIR`.
- **Constructor parameter must match type** — `OBJECT_FILE`: direct fields. `ARRAY_FILE`/`ARRAY_DIR`: single `List<T>` parameter. `OBJECT_DIR`: single `Map<String, T>` parameter.
- **Data files must be in `data/`** — Files placed elsewhere are not discovered. When used standalone (without the full `quarkus-roq` extension), the `data/` directory is under `src/main/resources/`. When used with the full Roq SSG, it's at the project root.
- **Optional by default** — `@DataMapping` has `required = false` by default. If your app requires the data file/directory to exist, set `required = true`.
