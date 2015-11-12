FROM	ubuntu:14.04

RUN 	apt-get install -y software-properties-common && \
		apt-get install unzip && \
		add-apt-repository -y ppa:webupd8team/java && \
		apt-get update
RUN		echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
		apt-get install -y oracle-java8-installer
RUN		rm -rf /var/lib/apt/lists/* && \
		rm -rf /var/cache/oracle-jdk8-installer

ENV		JAVA_HOME /usr/lib/jvm/java-8-oracle

ENV		MEMBRANE_DOWNLOAD_URL "http://repository.membrane-soa.org/service/local/artifact/maven/content?r=snapshots&g=org.membrane-soa&a=membrane-service-proxy&v=LATEST&e=zip"

RUN		wget -L -P /opt ${MEMBRANE_DOWNLOAD_URL} --content-disposition && \
		cd /opt && \
		unzip *.zip && \
		rm *.zip && \
		ln -s membrane-service-proxy-* membrane

COPY	proxies.xml /opt/membrane/conf/

EXPOSE	8000-9900

ENTRYPOINT	["/opt/membrane/service-proxy.sh"]
