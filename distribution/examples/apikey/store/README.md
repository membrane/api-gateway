# JDBC Database API Key Store

A quick guide to setting up a JDBC-based API key store using PostgreSQL.

### Prerequisite

- **Docker installed:**

    - If Docker us already installed, skip to the next step.
    - Otherwise, install Docker from [https://docs.docker.com/get-started/get-docker/](Get Docker).

---

1. **Run Database Container:**

    - Start a database container (e.g., PostgreSQL) with:

  ```shell
  docker run --name postgres -e POSTGRES_USER=user -e POSTGRES_PASSWORD=password -p 5432:5432 -d postgres
  ```

2. **Download JDBC Driver:**

    - Download the PostgreSQL JDBC driver from the official
      site: [https://jdbc.postgresql.org/download/](https://jdbc.postgresql.org/download/).
    - Place it in the `lib` directory of your Membrane installation.

3. **Configure `proxies.xml`:**

    - Example configuration for PostgreSQL:

   ```xml
    <spring:bean id="datasource" class="org.apache.commons.dbcp2.BasicDataSource">
                    <spring:property name="driverClassName" value="org.postgresql.Driver" />
                    <spring:property name="url" value="jdbc:postgresql://localhost:5432/postgres" />
                    <spring:property name="username" value="user" />
                    <spring:property name="password" value="password" />
                </spring:bean>
   ```
   ```xml
    <api port="2000">
            <apiKey>
                <databaseApiKeyStore datasource="dataSource">
                    <keyTable>key</keyTable>
                    <scopeTable>scope</scopeTable>
                </databaseApiKeyStore>
                <headerExtractor />
            </apiKey>
            <target url="https://api.predic8.de"/>
    </api>
   ```

4. **run service.proxy.sh script:**

```shell
./membrane.sh
```

5. **Run SQL File in your Docker Container**

- Run this command:

```shell
docker exec -i postgres psql -U user -d postgres < ./insert_apikeys.sql
```

- test

```shell
docker exec -i postgres psql -U user -d postgres -c "SELECT * FROM key;"
```

6. **You can test it using curl:**

```shell
curl localhost:2000 -H "x-api-key:unsecure2000"
```

- if the API key is valid, you will be redirected to ```https://api.predic8.de```.