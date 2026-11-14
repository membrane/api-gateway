# Running Membrane with Docker Compose

[docker-compose.yml](docker-compose.yml) contains the options: image, port and mount and it
works the same way in every shell.

## 1. Start Membrane

Run the command in this directory:

```sh
docker compose up
```

The warning `Environment variable 'BACKEND_URL' not found` is expected. Without the
variable, the Gateway API uses the mock. See [40-Environment-Variables.md](40-Environment-Variables.md).

## 2. Call the API

```sh
curl localhost:2000
```

## 3. Stop Membrane

Press `Ctrl+C`.

## Notes

- `docker compose up -d` starts Membrane in the background. Follow the log with
  `docker compose logs -f`.
- With `restart: unless-stopped`, Docker starts the container again after it exits or
  Docker restarts, unless you stopped it yourself.
