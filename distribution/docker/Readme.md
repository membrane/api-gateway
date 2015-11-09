# Membrane Dockerfile

Creates an image with Membrane and copies over the proxies.xml configuration file from this folder.

## Create image
	docker build -t predic8/membrane:latestStable .

## Run container
	docker run -d -p 8000-9000:8000-9000 --name membrane predic8/membrane:latestStable

## Open Example Proxies

### REST Names
	http://<<docker-machine>>:8080/restnames/name.groovy?name=Franz

### Membrane Web Console
	http://<<docker-machine>>:9000/

### Whole Web Seite
	http://<<docker-machine>>:9000/

## Troubleshooting

### Display Logs
	docker logs membrane

### Open shell in container and take a look at the Membrane log
	docker exec -it membrane /bin/bash
	less /opt/membrane/memrouter.log
