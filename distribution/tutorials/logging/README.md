# Logging Tutorial

Learn how to configure Membrane's log output: JSON log lines for log aggregators and access logs.

Membrane writes two kinds of logs:

- **Log**: Membrane's own diary — startup, warnings, errors and the messages of the `log` plugin.
  Use it to find out what the gateway is doing.
- **Access log**: a record of the traffic — exactly one line per request with client, method, path,
  status and size. Use it to find out which APIs are called, by whom, and with which result.

Both are written through Log4j2, so each can have its own format and destination.

| Step | Topic |
|---|---|
| [10-Json-Logging.yaml](10-Json-Logging.yaml) | Log as JSON for log aggregators |
| [30-Access-Log.yaml](30-Access-Log.yaml) | Access log file in Apache Common Log Format |
| [31-Access-Log-Custom-Fields.yaml](31-Access-Log-Custom-Fields.yaml) | Own fields in the access log, e.g. a header or a value from the JSON body |
| [32-Access-Log-Per-Api.yaml](32-Access-Log-Per-Api.yaml) | One access log file per API |

Each step is explained directly in the configuration file, which is also the
Membrane config you run. Open [10-Json-Logging.yaml](10-Json-Logging.yaml) and follow the instructions there.
