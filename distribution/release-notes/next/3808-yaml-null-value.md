---
type: fix
area: Configuration
---
- YAML: a null value (`~` or `null`) for an attribute is now a configuration error that names the key and shows its location. Before, a value under a map-style element such as `properties` of `jmsProducer` failed with a bare `NullPointerException`, a text attribute was set to the text `null`, and a boolean attribute was silently set to `false`. An empty value (`key:`) is still an empty string.
