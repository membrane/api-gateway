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

  - Choose from:
    [https://repo1.maven.org/maven2/org/mongodb/mongodb-driver-sync/](https://repo1.maven.org/maven2/org/mongodb/mongodb-driver-sync/)
    a recent version. 5.X is recommended.
  - Place it in the `lib` directory of your Membrane installation.

2. **Configure:** edit `apis.yaml`

3. **run service.proxy.sh script:**

```shell
./membrane.sh
```

4. **You can test it using curl:**

```shell
curl -X POST http://localhost:2000 -H "Content-Type: application/json" -d '{"message": "Hallo"}'
```

5. **Verify Exchange Storage via Admin Console:**

- Open the Admin Console by navigating to ```http://localhost:9000``` in your browser.
- Under the ```Call``` section, you will see a list of exchanges. These are the same entries stored in the MongoDB
  collection.
- Even if you stop Membrane and restart it, the exchanges will still appear in the Admin Console. This is because
  Membrane pulls the exchange data from MongoDB on startup.