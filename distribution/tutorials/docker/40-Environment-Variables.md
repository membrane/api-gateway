# Passing Environment Variables for the Configuration

Use one image in every environment and pass in what differs, such as backend URLs, log
levels or secrets, as environment variables.

The configuration reads two of them:

- `BACKEND_URL` in [conf/apis.yaml](conf/apis.yaml): `#{env.BACKEND_URL ?: 'http://localhost:2001'}`
- `LOG_LEVEL` in [conf/log4j2.xml](conf/log4j2.xml): `${env:LOG_LEVEL:-info}`

This step uses the image `my-gateway` from [30-Own-Image.md](30-Own-Image.md). If you
skipped that step, build the image first, in this directory:

```sh
docker build -t my-gateway .
```

## 1. Start a container with environment variables

```sh
docker run --rm -d -p 2000:2000 --name membrane -e BACKEND_URL=https://api.predic8.de -e LOG_LEVEL=debug my-gateway
```

## 2. Call the API

```sh
curl localhost:2000
```

The request now goes to `api.predic8.de`:

```json
{
  "aid" : "api.predic8.de:api-predic8-de",
  "name" : "predic8 APIs",
  ...
}
```

## 3. Look at the log

```sh
docker logs membrane
```

With `LOG_LEVEL=debug`, the log has many more lines than in step 30.

## Notes

- Membrane reads the environment variables when it loads the configuration. To change one,
  start a new container.
- Several variables, e.g. for production, fit into a file with one `NAME=value` per line:
  `docker run --env-file prod.env ...`. Pass secrets this way instead of writing them into
  the image.
- `JAVA_OPTS` passes options to the JVM, e.g. `-e JAVA_OPTS=-Xmx512m`.
- With Docker Compose, set the variables under `environment:` or `env_file:`:

  ```yaml
  services:
    membrane:
      image: my-gateway
      environment:
        BACKEND_URL: https://api.predic8.de
        LOG_LEVEL: debug
  ```

## See also

- [../advanced/100-Environment-Variables.yaml](../advanced/100-Environment-Variables.yaml) for
  environment variables in the configuration
- [../logging](../logging) for more Log4j2 configurations
