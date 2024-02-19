FROM maven:3.9.6-amazoncorretto-21-al2023

ADD pom.xml /app/
ADD annot/pom.xml /app/annot/
ADD core/pom.xml /app/core/
ADD distribution/pom.xml /app/distribution/
WORKDIR /app

RUN if [ -d .m2 ] ; then mv .m2 /root ; fi

# fake maven run to pre-cache a few maven dependencies
RUN mvn install ; exit 0

ADD . /app

RUN mvn install