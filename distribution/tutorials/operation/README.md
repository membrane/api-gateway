# Operation Tutorial

Learn how to run and observe Membrane in production: trace requests across services, export
traces and metrics, record exchanges, harden the configuration, and look at the raw bytes on
the wire.

Each step is explained directly in the configuration file, which is also the Membrane config
you run. If possible, use an editor with YAML support such as Visual Studio Code or
IntelliJ IDEA.

Before you start, make sure you have completed the
[Getting Started](../getting-started) tutorial.

1. [10-Correlation-Id.yaml](10-Correlation-Id.yaml) — attach a correlation id to every
   request and find it in the logs.
2. [20-Propagation.yaml](20-Propagation.yaml) — pass the correlation id on from one API
   to the next.
3. [40-OpenTelemetry.yaml](40-OpenTelemetry.yaml) — send traces to Jaeger with
   OpenTelemetry (Docker required).
4. [50-Prometheus.yaml](50-Prometheus.yaml) — expose metrics for Prometheus.
5. [60-Grafana.yaml](60-Grafana.yaml) — show the metrics on a Grafana dashboard.
6. [70-Record-Messages-In-Loki.yaml](70-Record-Messages-In-Loki.yaml) — send complete
   exchanges to Grafana Loki (Docker required).
7. [70-Production-Settings.yaml](70-Production-Settings.yaml) — a checklist-driven
   production setup with rate limiting, health probe and metrics.
8. [80-Byte-Stream-Logging.yaml](80-Byte-Stream-Logging.yaml) — log every byte sent and
   received, including HTTPS traffic in clear text.

## Next Steps

Start with [10-Correlation-Id.yaml](10-Correlation-Id.yaml), or jump to any step that fits
your needs.
