# Membrane Deployment with Docker

This example illustrates how to deploy Membrane as a Docker container whilst making use of a custom `proxies.xml` file.

## Running the Example

1. **Build the Docker image:**  
	`docker build -t predic8/membrane:latestRelease .`


2. **Generate a Docker container from the built image and expose the API port:**  
    `docker run -d -p 2000:2000 --name membrane predic8/membrane:latestRelease`


3. **Send a simple get request to Membrane:**  
    `curl localhost:2000/shop/products/`  
   The request gets relayed to api.predic8.de.


4. **Access the OpenAPI UI by visiting `localhost:2000/shop/docs` in your browser.**

## How it is done

Take a look at the `proxies.xml` file.

```xml
<router>
  <api port="2000">
    <openapi location="fruitshop-api.yml"
             validateRequests="yes"
             validateResponses="yes"
             validationDetails="yes"/>
    <target url="api.predic8.de" />
  </api>
</router>
```
This file defines a simple API proxy with an OpenAPI validation plugin. All requests that the Docker container receives at port 2000 will be validated against the provided OpenAPI manifest.  
In this instance, we use the manifest YAML that is provided with Membrane and target its corresponding server.

For deploying Membrane with our custom proxies.xml file and OpenAPI manifest, we utilize a straightforward Dockerfile as follows:

```Dockerfile
FROM predic8/membrane

COPY proxies.xml /opt/membrane/conf/
COPY ./../../conf/fruitshop-api.yml /opt/membrane/conf

EXPOSE 2000

ENTRYPOINT ["/opt/membrane/service-proxy.sh"]
```

In this Dockerfile, we pull the Membrane base image from Docker Hub, then copy both proxies.xml and the OpenAPI manifest into the Membrane configuration directory. We declare that port 2000 should be exposed, and set the entrypoint to the Membrane startup script.