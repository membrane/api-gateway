---
type: fix
area: Plugins
---
- An empty `value: ""` now sets an empty value. Before, `setBody` set the body to the text `null`, `setHeader` failed with `500`, and `setProperty` set the property to `null` instead of an empty string.
