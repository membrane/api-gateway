# Membrane Roadmap

# Version 6.1.0

- Grafana Dashboard to import in examples/prometheus
  - Also provide the datasource config
  - Maybe the config can be included into the docker-compose setup
- Example Tests without unzipping for every test

# Version 6.0.0

- Delete interceptor/
  - api-management
  - CBR

- Check
  - Is example embeddug-java still running?
    - Update to Java 21 needed? 

- Move Examples
  - basic-xml-interceptor -> xml/basic-xml-interceptor


- Refactor setHeader, setProperty make it like if
- Is SessionResumptionTest still needed?
- Document XPath Language of <if>
- Delete CBR
- Restructure samples
- UnitTests: Use PackageScan for more or all
- Make <log headerOnly="false"/> clearer! 

# ADR Discussions

- Make flow a property of exchange 
  - That would support a method like getMessage
  - Flow is more a property of E