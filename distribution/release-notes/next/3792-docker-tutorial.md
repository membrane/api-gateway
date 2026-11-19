---
type: improvement
area: Tutorials and Documentation
---
- Tutorials: new Docker tutorial `tutorials/docker`. It covers mounting your configuration into the official image, Docker Compose, building your own image, and environment variables. It replaces the example `examples/deployment/docker`, whose configuration the image never loaded because the image's own `conf/apis.yaml` took precedence [#3792](https://github.com/membrane/api-gateway/pull/3792)
