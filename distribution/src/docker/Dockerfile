FROM ubuntu:14.04

RUN 	apt-get install -y software-properties-common && \
	    apt-get install unzip && \
	    add-apt-repository -y ppa:webupd8team/java && \
	    apt-get update && \
	    echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
	    apt-get install -y oracle-java8-installer && \
	    rm -rf /var/lib/apt/lists/* && \
	    rm -rf /var/cache/oracle-jdk8-installer

ENV JAVA_HOME /usr/lib/jvm/java-8-oracle

ADD http://mirror.predic8.com/membrane/router/membrane-service-proxy-4.0.18.zip /opt
RUN cd /opt && \
    unzip *.zip && \
    rm *.zip && \
    ln -s membrane-service-proxy-4.0.18 membrane

COPY *.xml /opt/membrane/conf/

EXPOSE 8000-9900

ENTRYPOINT ["/opt/membrane/service-proxy.sh"]
