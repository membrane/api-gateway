# Membrane API Gateway
[![GitHub release](https://img.shields.io/github/release/membrane/service-proxy.svg)](https://github.com/membrane/service-proxy/releases/latest)
[![Hex.pm](https://img.shields.io/hexpm/l/plug.svg)](https://raw.githubusercontent.com/membrane/service-proxy/master/distribution/router/LICENSE.txt)

Simple & extensible API gateway for REST and legacy services, written in Java

## Full Documentation

[http://membrane-api.io](https://membrane-api.io/getting-started.html)

## Getting started

```
docker run -p 2000:2000 predic8/membrane
```
Navigating to [http://localhost:2000](http://localhost:2000) in your browser or using `curl http://localhost:2000` should yield the same responses as calling http://api.predic8.de does:


## Configuration

Membrane is typically configured by a configuration file named *proxies.xml*

```xml
<api>
    <!-- TODO -->
</api>
```

To start Membrane with this configuration, run:

```
docker run -v proxies.xml:/opt/membrane/conf/proxies.xml -p 2000:2000 predic8/membrane
```

## More examples

https://github.com/membrane/service-proxy/blob/master/README.md

## Source Code

https://github.com/membrane/service-proxy/
