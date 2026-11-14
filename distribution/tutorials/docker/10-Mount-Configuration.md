# Mount your Configuration into a Membrane Container

Run the official `predic8/membrane` image with your own configuration from the
[conf](conf) folder, mounted into the container. No image to build.

The folder contains the gateway configuration [apis.yaml](conf/apis.yaml) and a
Log4j2 configuration [log4j2.xml](conf/log4j2.xml) that writes JSON log lines.

## 1. Start the container

Run the command in this directory:

Linux/Mac:
```sh
docker run --rm -p 2000:2000 -v "$PWD/conf:/opt/membrane/conf" predic8/membrane:7.8.1
```

Windows Command Prompt:
```bat
docker run --rm -p 2000:2000 -v "%cd%\conf:/opt/membrane/conf" predic8/membrane:7.8.1
```

Windows PowerShell:
```powershell
docker run --rm -p 2000:2000 -v "${PWD}\conf:/opt/membrane/conf" predic8/membrane:7.8.1
```

The console shows JSON log lines, e.g.:

```json
{"@timestamp":"...","message":"Membrane API Gateway 7.8.1 up and running!",...}
```

The warning `Environment variable 'BACKEND_URL' not found` is expected. Without the
variable, the Gateway API uses the mock. See [40-Environment-Variables.md](40-Environment-Variables.md).

## 2. Call the API

```sh
curl localhost:2000
```

The Gateway API forwards the request to the Mock Backend API on port 2001 of the same
container.

## 3. Stop the container

Press `Ctrl+C`.

## Notes

- Changes to the mounted files take effect without a restart: Membrane reloads
  `conf/apis.yaml` when it changes, and Log4j2 picks up changes to `conf/log4j2.xml`
  within 10 seconds.
- The mount replaces the whole `conf/` folder of the image. That's why the `conf` folder
  here has its own `log4j2.xml`. Without one, Log4j2 falls back to its default and only
  logs errors.
- Mount the folder, not single files. Editors save a file by replacing it, and a container
  with a single-file mount doesn't see the change.

## Troubleshooting

- `Permission denied` on Linux with SELinux (Fedora, RHEL): add `:Z` to the mount, e.g.
  `-v "$PWD/conf:/opt/membrane/conf:Z"`.
- Membrane runs as user `membrane` (uid 1001) in the container, so the files in `conf/`
  must be readable for others.
