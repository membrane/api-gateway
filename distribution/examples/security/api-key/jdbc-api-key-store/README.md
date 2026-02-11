# JDBC Database API Key Store

A quick guide to setting up a JDBC-based API key store using PostgreSQL.

### Prerequisite

- **Docker installed:**
    - If Docker is already installed, skip to the next step.
    - Otherwise, install Docker from [https://docs.docker.com/get-started/get-docker/](https://docs.docker.com/get-started/get-docker/).

---

1. **Run Database Container:**
   - Start a database container (e.g., PostgreSQL) with:

   ```shell
   docker run --rm --name postgres -e POSTGRES_PASSWORD=secret -p 5432:5432 postgres
   ```

2. **Download JDBC Driver:**
   - Download the PostgreSQL JDBC driver from the official
     site: [https://jdbc.postgresql.org/download/](https://jdbc.postgresql.org/download/).
   - Place it in the `lib` directory of your Membrane installation.

3. **Take a look at the configuration in the [`apis.yaml`](apis.yaml)**

4. **Run the membrane.sh script:**

   ```shell
   ./membrane.sh
   ```

5. **Run SQL File in your Docker Container:**
   - Make sure that Membrane was running before to create the database tables.
   - Run this command:

   ```shell
   docker exec -i postgres psql -U postgres -d postgres < ./insert_apikeys.sql
   ```

   - test

   ```shell
   docker exec -i postgres psql -U postgres -d postgres -c "SELECT * FROM key;"
   ```

6. **Test it using curl:**

   ```shell
   curl localhost:2000 -H "x-api-key:unsecure2000"
   ```

- if the API key is valid, you will be forwarded to ```https://api.predic8.de```.