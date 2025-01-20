### JDBC API Key Store

A quick guide to setting up a JDBC-based API key store using PostgreSQL.

#### Key Features:
- **Database Integration**: PostgreSQL for storing API keys and scopes.
- **JDBC Configuration**: Leverage the PostgreSQL JDBC driver.
- **Spring Integration**: Configure data sources via `proxies.xml`.

### Prerequisite:

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

    - Download the PostgreSQL JDBC driver from the official site: [https://jdbc.postgresql.org/download/](https://jdbc.postgresql.org/download/).
    - Place it in the `lib` directory of your project.

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
./service-proxy.sh
```

5. **Run Database Script to Initialize Tables and Insert Values**

    ```shell
   ./psql.sh
    ```
   
- This script will:
  - Connect to the PostgreSQL database.
  - Create the required key and scope tables if they do not exist.
  - Insert initial API keys into the key table.

- After running the script, the first API key inserted will be displayed in the console. Copy this key for use in the next step.

6. **You can test it using curl:**

```shell
curl localhost:2000 -H "x-api-key:<FIRST API KEY>"
```

#### Implementation Highlights:

- **`JDBCApiKeyStore`**:
    - Manages API keys and scopes using JDBC.
    - Automatically creates `key` and `scope` tables if missing.
   