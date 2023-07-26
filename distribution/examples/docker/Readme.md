# Membrane Deployment with Docker

This example illustrates how to deploy Membrane as a Docker container whilst making use of a custom `proxies.xml` file.

## Running the Example

1. **Build the Docker image:**
    `docker build -t membrane:1 .`


2. **Generate a Docker container from the built image and expose the API port:**  
    `docker run -d -p 2000:2000 --name membrane membrane:1`


3. **Send a simple get request to Membrane:**  
    `curl localhost:2000/shop/v2/products`  
     The request gets relayed to `api.predic8.de`.


4. **Access the API documentation:**  
    Visit `localhost:2000/api-doc` in your browser.
    Membrane automatically generates this address out of the provided manifest.

## How it is done

Take a look at the `proxies.xml` file.

```xml
<router>
    <api port="2000">
        <openapi location="https://api.predic8.de/shop/v2/api-docs" />
    </api>
</router>
```
This file defines a simple API proxy with an OpenAPI plugin.  
In this instance, we define an online path to the manifest YAML, this will make membrane automatically set the manifest's host as the proxy target.

For deploying Membrane with our custom proxies.xml file, we utilize a straightforward Dockerfile as follows:

```Dockerfile
FROM predic8/membrane

COPY proxies.xml /opt/membrane/conf/

EXPOSE 2000

ENTRYPOINT ["/opt/membrane/service-proxy.sh"]
```

In this Dockerfile, we pull the Membrane base image from Docker Hub, then copy proxies.xml into the Membrane configuration directory. We declare that port 2000 should be exposed, and set the entrypoint to the Membrane startup script.