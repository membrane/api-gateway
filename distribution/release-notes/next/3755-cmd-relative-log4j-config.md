---
type: fix
area: Configuration and Distribution
---
- `membrane.cmd` now resolves a relative `-Dlog4j.configurationFile` in `JAVA_OPTS` against the Membrane installation directory, like `membrane.sh` does. Absolute paths and URIs are left unchanged. Before, Windows resolved the path against the current directory, so Log4j often found no configuration and Membrane started without the intended logging.
