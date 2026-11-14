# Creating Your Own Membrane Docker Image

Bake the configuration from the [conf](conf) folder into your own image, based on the
official `predic8/membrane` image. The image then runs anywhere without further files,
e.g. in Kubernetes or a CI/CD pipeline.

## 1. Build the image

Run the command in this directory:

```sh
docker build -t my-gateway .
```

## 2. Start a container

```sh
docker run --rm -p 2000:2000 --name membrane my-gateway
```

## 3. Call the API

```sh
curl localhost:2000
```

## 4. Look at the log

The console of the container shows the request:

```json
{"@timestamp":"...","message":"Got: /",...}
```

The warning `Environment variable 'BACKEND_URL' not found` is expected. Without the
variable, the Gateway API uses the mock. See [40-Environment-Variables.md](40-Environment-Variables.md).

## Notes

- The configuration is now part of the image. Changes to `conf/` take effect after the next
  `docker build`.
- Don't set an `ENTRYPOINT` in your Dockerfile. The one of the base image starts Membrane
  under `tini`, so `docker stop` shuts Membrane down gracefully. Without it, `docker stop`
  waits 10 seconds and then kills the process.
- Membrane reads `conf/apis.yaml` before `conf/proxies.xml`. A `proxies.xml` copied into
  `conf/` is ignored, because the image already contains an `apis.yaml`.

## Troubleshooting

- `The container name "/membrane" is already in use` or `port is already allocated`: a
  container from an earlier run is still running. List the running containers with
  `docker ps` and stop it with `docker stop <name>`.
