# Layout Resolution

**`layout:`** resolves with user override fallback. This is the default for theme layouts, allowing users to customize the theme by placing a layout with the same name in their site.

**`theme-layout:`** resolves directly to theme layouts, bypassing user overrides. Use this when a content page or user layout needs to wrap/extend a specific theme layout without risk of circular resolution.

## Resolution algorithm

**Legacy detection** (all contexts):
1. Starts with `theme-layouts/` or `layouts/` -> warn deprecated, direct match or fail
2. Contains `:theme/` -> warn deprecated, strip prefix, continue as simple name

**From content pages and user layouts:**

| Key | Value | Resolution | Use case |
|-----|-------|------------|----------|
| `layout:` | `foo` | `layouts/foo` -> `layouts/{active}/foo` -> `theme-layouts/{active}/foo` -> fail | Default. User layout wins, theme is fallback. |
| `layout:` | `other/foo` | `layouts/other/foo` -> `theme-layouts/other/foo` -> fail | Reference a layout from a specific theme. |
| `theme-layout:` | `foo` | `theme-layouts/{active}/foo` -> fail | Force theme layout, skip user overrides. |
| `theme-layout:` | `other/foo` | `theme-layouts/other/foo` -> fail | Force a specific theme's layout directly. |

**From theme layouts (own == active theme):**

| Key | Value | Resolution | Use case |
|-----|-------|------------|----------|
| `layout:` | `foo` | `layouts/foo` -> `theme-layouts/{own}/foo` -> fail | Default for themes. Users can override the chain. |
| `layout:` | `other/foo` | `layouts/other/foo` -> `theme-layouts/other/foo` -> fail | Extend a layout from another theme. |
| `theme-layout:` | `foo` | `theme-layouts/{own}/foo` -> fail | Internal ref that users can't intercept. |
| `theme-layout:` | `other/foo` | `theme-layouts/other/foo` -> fail | Lock to another theme's layout directly. |

**From theme layouts (own != active theme):**

| Key | Value | Resolution | Use case |
|-----|-------|------------|----------|
| `layout:` | `foo` | `layouts/{own}/foo` -> `theme-layouts/{own}/foo` -> fail | Internal ref within own theme, user can override per-theme. |
| `layout:` | `other/foo` | `layouts/other/foo` -> `theme-layouts/other/foo` -> fail | Cross-theme reference. |
| `theme-layout:` | `foo` | `theme-layouts/{own}/foo` -> fail | Internal ref that users can't intercept. |
| `theme-layout:` | `other/foo` | `theme-layouts/other/foo` -> fail | Lock to another theme's layout directly. |
