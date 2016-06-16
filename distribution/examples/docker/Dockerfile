FROM	anapsix/alpine-java

RUN	apk update && apk add curl wget openssl

RUN	curl -s https://api.github.com/repos/membrane/service-proxy/releases | grep browser_download_url | head -n 1 | cut -d '"' -f 4 | xargs wget -P /opt
RUN cd /opt && \
	ls -la && \
	unzip *.zip && \
	rm *.zip && \
	ln -s membrane-service-proxy-* membrane

COPY proxies.xml /opt/membrane/conf/

EXPOSE 8000-9900

ENTRYPOINT ["/opt/membrane/service-proxy.sh"]
