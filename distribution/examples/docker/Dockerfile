FROM predic8/membrane

COPY proxies.xml /opt/membrane/conf/
COPY ./../../conf/fruitshop-api.yml /opt/membrane/conf

EXPOSE 2000

ENTRYPOINT ["/opt/membrane/service-proxy.sh"]
