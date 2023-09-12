FROM docker.io/eclipse-temurin:17.0.8_7-jre
LABEL authors="info@predic8.de"
ARG USERNAME=membrane
ARG USER_UID=1000
ARG USER_GID=$USER_UID

COPY release-bin .
RUN apt-get update \
    && apt-get install unzip
RUN unzip membrane-api-gateway-*.zip -x */examples* */tutorials* \
    && rm membrane-api-gateway-*.zip \
    && mv membrane-api-gateway-* /opt/membrane
COPY log4j2.xml /opt/membrane/conf/.

# Create the user
RUN groupadd --gid $USER_GID $USERNAME && useradd --uid $USER_UID --gid $USER_GID -m $USERNAME

USER $USERNAME
WORKDIR /opt/membrane
ENTRYPOINT ["./service-proxy.sh"]
