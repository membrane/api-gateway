# MongoDB Api Key Store

A quick guide to setting up a MongoDB API key store.

### Prerequisite

- **MongoDB installed:**

    - If MongoDB is already installed, skip to the next step.
    - Otherwise, install MongoDB from [https://www.mongodb.com/try/download/community](https://www.mongodb.com/try/download/community).

---

1. **Insert API Keys into Mongodb:**

  - Run this Command

  ```shell
mongosh --eval "use('apiKeyDB'); db.apikey.insertMany([
{ _id: '7842b294-5d58-4d29-914e-f84ec9266d3e', scopes: ['read', 'write', 'delete'] }, 
{ _id: '3c7f6c34-89e6-4b4a-9dfb-708f8c28ef3d', scopes: ['read'] }, 
{ _id: 'c42e9ae1-4d4a-42ad-8b49-216912c8eb82', scopes: ['read', 'write', 'admin'] }, 
{ _id: 'unsecure2000', scopes: ['read'] }, 
{ _id: 'flower2025', scopes: ['read', 'write'] }]);"
  ```

2. **Download MongoDB Driver:**

  - Download the MongoDB driver [https://repo1.maven.org/maven2/org/mongodb/mongodb-driver-sync/5.0.1/mongodb-driver-sync-5.0.1.jar](https://repo1.maven.org/maven2/org/mongodb/mongodb-driver-sync/5.0.1/mongodb-driver-sync-5.0.1.jar).
  - Place it in the `lib` directory of your Membrane installation.

3. **Configure `proxies.xml`:**

  - Example configuration for MongoDB:

   ```xml
    <api port="2000">
        <apiKey>
            <mongoDBApiKeyStore connectionString="yourConnectionString" databaseName="apiKeyDB">
                <keyCollection>apikey</keyCollection>
            </mongoDBApiKeyStore>
            <headerExtractor/>
        </apiKey>
        <target url="https://api.predic8.de"/>
    </api>
   ```

4. **run service.proxy.sh script:**

```shell
./membrane.sh
```

5. **Verify API Keys in MongoDB:**

- Run this command to ensure your API keys are stored correctly:

```shell
mongosh --eval "db.apiKeys.find().pretty()" apiKeyDB
```

6. **You can test it using curl:**

```shell
curl localhost:2000 -H "x-api-key:unsecure2000"
```

- if the API key is valid, you will be redirected to ```https://api.predic8.de```.