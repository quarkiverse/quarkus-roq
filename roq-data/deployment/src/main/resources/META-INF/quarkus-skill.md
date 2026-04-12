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

- `value` — data file name without extension (must match a file in `data/`)
- `required` — `true` if the data file must exist (default: `false`)

### Array Data

For data files with a root-level JSON/YAML array, use `parentArray = true`:

**Example** (`data/contributors.json`):
```json
[
  { "name": "Alice", "role": "maintainer" },
  { "name": "Bob", "role": "contributor" }
]
```

```java
@DataMapping(value = "contributors", parentArray = true)
public record Contributors(List<Contributor> contributors) {

    public record Contributor(String name, String role) {}
}
```

The record must have a single constructor parameter of type `List<T>`.

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

- **File name must match annotation value** — `@DataMapping("authors")` requires a file named `authors.yaml`, `authors.yml`, or `authors.json` in `data/`.
- **`parentArray=true` requires `List<T>` constructor** — When the root element is an array, the record must have a single `List<T>` constructor parameter.
- **Data files must be in `data/`** — Files placed elsewhere are not discovered. When used standalone (without the full `quarkus-roq` extension), the `data/` directory is under `src/main/resources/`. When used with the full Roq SSG, it's at the project root.
- **Optional by default** — `@DataMapping` has `required = false` by default. If your app requires the data file to exist, set `required = true`.
