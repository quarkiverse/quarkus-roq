# Roq FrontMatter Build Pipeline

```
  ┌──────────────────┐
  │ 1. Scan          │
  │ files + metadata │
  └────────┬─────────┘
           │
 ScannedContent/Layout
           │
           ▼
  ┌──────────────────┐
  │ 2. Assemble      │
  │ layouts +        │
  │ transforms       │
  └────────┬─────────┘
           │
   RawPage/RawLayout
           │
           ▼
  ┌──────────────────┐
  │ 3. Data          │
  │ merge data +     ├───────────────────────┐
  │ dates + URLs     │                       │
  └────────┬─────────┘                       │
           │                          LayoutTemplate
   Document/Paginate                    PageTemplate
           │                                 │
           ▼                                 │
  ┌──────────────────┐                       │
  │ 4. Publish       │                       │
  │ collections +    │                       │
  │ pagination       │                       │
  └────────┬─────────┘                       │
           │                                 │
  PublishDoc/NormalPage                      │
           │                                 │
           ▼                                 │
  ┌──────────────────┐                       │
  │ 5. Record        │                       │
  │ CDI beans        │                       │
  └────────┬─────────┘                       │
           │                                 │
         Output                              │
           │                                 │
           ▼                                 │
  ┌──────────────────┐◄──────────────────────┘
  │ 6. Bind          │
  │ Qute + routes    │
  └──────────────────┘
```

## Url paths

Every source serving a url path claims it with a `RoqPathBuildItem` (pages and static files in step 6, aliases in
the aliases plugin, any plugin serving a path). Step 6 `bindSelectedPaths` checks that each path is claimed once,
fails the build on a duplicate (a warning in dev mode) and selects the paths in Roq Generator.
