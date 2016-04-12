FROM ubuntu:15.04

RUN \
  apt-get install -y software-properties-common zip curl && \
  echo oracle-java7-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections && \
  add-apt-repository -y ppa:webupd8team/java && \
  apt-get update && \
  apt-get install -y oracle-java7-installer 
  
RUN  rm -rf /var/lib/apt/lists/* && rm -rf /var/cache/oracle-jdk7-installer

RUN curl -o /maven.tar.gz http://ftp.halifax.rwth-aachen.de/apache/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz && \
  mkdir /maven && \
  cd /maven && \
  tar -xvf /maven.tar.gz && \
  rm /maven.tar.gz

ADD pom.xml /app/
ADD annot/pom.xml /app/annot/
ADD core/pom.xml /app/core/
ADD distribution/pom.xml /app/distribution/
WORKDIR /app

RUN if [ -d .m2 ] ; then mv .m2 /root ; fi

# fake maven run to pre-cache a few maven dependencies
RUN /maven/apache-maven-*/bin/mvn install ; exit 0

ADD . /app

ENV MAVEN_OPTS="-XX:MaxPermSize=128m"

RUN /maven/apache-maven-*/bin/mvn install