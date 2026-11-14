# Docker Tutorial

Run Membrane in Docker with your own configuration: mount it into the official
`predic8/membrane` image, or bake it into your own image.

All steps use the configuration in the [conf](conf) folder: [apis.yaml](conf/apis.yaml)
with a Gateway API that forwards to a mock backend, and a Log4j2 configuration
[log4j2.xml](conf/log4j2.xml) that writes JSON log lines. The steps need no Internet
access, except for pulling the image and step 40.

1. [10-Mount-Configuration.md](10-Mount-Configuration.md) — run the official image with
   the `conf` folder mounted.
2. [20-Docker-Compose.md](20-Docker-Compose.md) — the same with Docker Compose.
3. [30-Own-Image.md](30-Own-Image.md) — build your own image with the configuration
   inside.
4. [40-Environment-Variables.md](40-Environment-Variables.md) — configure that image per
   environment with environment variables.

## Mount or own image?

| Mount the configuration                           | Build your own image                                   |
|---------------------------------------------------|--------------------------------------------------------|
| A change means editing a file                     | A change means a new build                             |
| Needs the `conf` folder on the host               | The image is immutable, versioned and self-contained   |
| Development, one image for many configurations    | Kubernetes, CI/CD, shipping the gateway as one image   |
