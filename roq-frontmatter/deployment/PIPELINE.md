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
