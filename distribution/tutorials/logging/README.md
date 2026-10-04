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
| [30-Access-Log.yaml](30-Access-Log.yaml) | Access log file in Apache Combined Log Format |
| [31-Access-Log-Custom-Fields.yaml](31-Access-Log-Custom-Fields.yaml) | Own fields in the access log, e.g. a header or a value from the JSON body |
| [32-Access-Log-Per-Api.yaml](32-Access-Log-Per-Api.yaml) | One access log file per API |

Each step is explained directly in the configuration file, which is also the
Membrane config you run. Open [10-Json-Logging.yaml](10-Json-Logging.yaml) and follow the instructions there.

Run from this folder and copy the desired logging configuration to `log4j2.yaml`:

```sh
cp log4j2-10-json.yaml log4j2.yaml
export MEMBRANE_DISABLE_TERM_COLORS=true
./membrane.sh -c 10-Json-Logging.yaml
```

Membrane's startup scripts load `log4j2.yaml` from the current working directory.
An explicit `-Dlog4j.configurationFile` in `JAVA_OPTS` takes precedence; clear an old
setting before following these tutorials. Without either setting, Membrane uses
its default logging configuration. Restart Membrane after changing `log4j2.yaml`.
Remove the copied file when finished to restore the default logging setup.

To run with Docker after copying the configuration:

```sh
export MEMBRANE_DOCKER_OPTS="--env-file docker.env"
./run-docker.sh -c 10-Json-Logging.yaml
```

The shared `docker.env` disables terminal colors for all steps. The launchers mount
this folder at `/opt/membrane/work` and use it as the working directory. They stay
identical to the other tutorial launchers.

The Docker image must contain the updated startup scripts that discover local
`log4j2.yaml` files. A locally rebuilt `predic8/membrane:7.7.0` image supports this;
the original published image does not.

Clear any previous logging configuration in `JAVA_OPTS` before starting, so it
does not override the copied file.

Each tutorial includes Linux/macOS and PowerShell commands. Access log files are
written into this tutorial folder.
