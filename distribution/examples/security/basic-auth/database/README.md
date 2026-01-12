# Basic Authentication - JDBC

This example walks you through setting up **HTTP Basic Authentication** for an API or Web application using a JDBC data source.

### Prerequisite

- **Docker installed:**

   - If Docker is already installed, skip to the next step.
   - Otherwise, install Docker from [https://docs.docker.com/get-started/get-docker/](https://docs.docker.com/get-started/get-docker/).

---

## Running the Example

1. **Start a database container (e.g. postgres):**
   ```shell
   docker run --name postgres -e POSTGRES_USER=user -e POSTGRES_PASSWORD=password -p 5432:5432 -d postgres
   ```

2. **Download JDBC Driver:**
   - Download the PostgreSQL JDBC driver from the official
     site: [https://jdbc.postgresql.org/download/](https://jdbc.postgresql.org/download/).
   - Place it in the `lib` directory of your Membrane installation.

3. Create demo users:  
   ```shell
   docker exec -i postgres psql -U user -d postgres < ./insert_users.sql
   ```
   test:
   ```shell
   docker exec -i postgres psql -U user -d postgres -c 'SELECT * FROM users;'
   ```

4. Start Membrane:
   ```shell
   ./membrane.sh
   ```

5. Open the URL http://localhost:2000 in your browser.

6. Login with the username `membrane` and the password `gateway`.

7. Take a look at the [`apis.yaml`](apis.yaml) to see how it is configured.

---
See:
- [basicAuthentication](https://www.membrane-api.io/docs/current/basicAuthentication.html) reference