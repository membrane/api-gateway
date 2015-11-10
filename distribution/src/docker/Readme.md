# Membrane Dockerfile

Baut ein Image mit Membrane und kopiert die proxies.xml Konfiguration aus diesem Verzeichnis in den Container.

## Image Bauen

docker build -t predic8/membrane:4.0.18 .


## Container ausführen

 docker run -d -p 8000-9000:8000-9000 --name membrane  predic8/membrane:4.0.18

## Beispiel Proxies aufrufen

### REST Names
http://<<docker-machine>>:8080/restnames/name.groovy?name=Franz

### Membrane Web Console
http://<<docker-machine>>:9000/

### Gesamte Web Seite
http://<<docker-machine>>:9000/

## Troubleshooting

### Logs Anzeigen
docker logs membrane

### Shell im Container öffenen und Blick ins Membrane log werfen
docker exec -it membrane /bin/bash
less /opt/membrane/memrouter.log
