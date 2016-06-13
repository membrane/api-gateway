FROM	anapsix/alpine-java

RUN	apk update && apk add curl

RUN	wget https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 && \
	chmod +x jq-linux64 && \
	mv jq-linux64 /usr/bin/jq

ENV	MEMBRANE_DOWNLOAD_URL="curl https://api.github.com/repos/membrane/service-proxy/releases | jq '.[0].assets[].browser_download_url' -r"

RUN	echo $MEMBRANE_DOWNLOAD_URL

#RUN	wget -P /opt ${MEMBRANE_DOWNLOAD_URL} && \
#	cd /opt && \
#	ls -la && \
#	unzip *.zip && \
#	rm *.zip && \
#	ln -s membrane-service-proxy-* membrane

#COPY	proxies.xml /opt/membrane/conf/

#EXPOSE	8000-9900

#ENTRYPOINT	["/opt/membrane/service-proxy.sh"]
