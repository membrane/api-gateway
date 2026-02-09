# MongoDB Exchange Store

Make exchanges persistent with MongoDB. You can search and inspect the HTTP traffic later even after restarts.
View live data in the Admin Console from the MongoDB store. Great for debugging, audits, or traffic insights.

### Prerequisite

- **Run MongoDB:**
  - Run the following command to start MongoDB in a Docker container:

    ```shell
    docker run --name mongo -p 27017:27017 mongo:latest
    ```
  - This starts MongoDB, accessible at ```mongodb://localhost:27017```.

---

1. **Download MongoDB Driver:**

    * Download these three JARs (all **the same version**):
        * [`org.mongodb:mongodb-driver-sync`](https://mvnrepository.com/artifact/org.mongodb/mongodb-driver-sync)
        * [`org.mongodb:mongodb-driver-core`](https://mvnrepository.com/artifact/org.mongodb/mongodb-driver-core)
        * [`org.mongodb:bson`](https://mvnrepository.com/artifact/org.mongodb/bson)
    * Place **all three** JARs in the `lib` directory of your Membrane installation.

2. Run membrane:

```shell
./membrane.sh
```

3. **You can test it using curl:**

```shell
curl -X POST http://localhost:2000 -H "Content-Type: application/json" -d '{"message": "Hallo"}'
```

4. **Verify Exchange Storage via Admin Console:**

- Open the Admin Console by navigating to ```http://localhost:9000``` in your browser.
- Under the ```Call``` section, you will see a list of exchanges. These are the same entries stored in the MongoDB
  collection.
- Even if you stop Membrane and restart it, the exchanges will still appear in the Admin Console. This is because
  Membrane pulls the exchange data from MongoDB on startup.

5. Take a look at the [`apis.yaml`](apis.yaml) to see how it is configured.