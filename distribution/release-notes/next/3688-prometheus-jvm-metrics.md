---
type: feature
area: Plugins
---
- `prometheus`: with `jvm: true` the metrics endpoint also exports basic JVM and process metrics, such as memory pool usage, GC pauses, live threads, open file descriptors, CPU usage and start time. Names, labels and units follow Micrometer, so Spring Boot dashboards and alerts work at least partially, without a JMX exporter or OpenTelemetry agent
