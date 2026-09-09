# API Key Tutorial

Learn how to secure an API with API keys: require a key on every request, grant
different scopes to different keys, derive key requirements from an OpenAPI
document, and store keys in a database instead of the config file.

Each step is explained directly in the configuration file, which is also the
Membrane config you run. If possible, use an editor with YAML support such as
Visual Studio Code or IntelliJ IDEA.

The tutorials build on each other, from simple to advanced:

1. [10-API-Key-Simple.yaml](10-API-Key-Simple.yaml) — secure an API by requiring a
   valid API key with every request.
2. [20-API-Key-RBAC.yaml](20-API-Key-RBAC.yaml) — use scopes on API keys for
   role-based access control.
3. [30-API-Key-OpenAPI.yaml](30-API-Key-OpenAPI.yaml) — derive API key enforcement
   from an OpenAPI document's security schemes.
4. [40-API-Key-JDBC-Store.yaml](40-API-Key-JDBC-Store.yaml) — store API keys and
   scopes in a PostgreSQL database.
5. [50-MongoDB-API-Key-Store.yaml](50-MongoDB-API-Key-Store.yaml) — store API keys
   and scopes in a MongoDB collection.

## Next Steps

Start with [10-API-Key-Simple.yaml](10-API-Key-Simple.yaml) and follow the
instructions in each file.
