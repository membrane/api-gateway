---
type: fix
area: Exchange Stores
---
- `fileExchangeStore`: `maxDays` now deletes old folders. Before, the daily cleanup was set up before the configuration was applied, so it never ran
- Exchanges read back from `elasticSearchExchangeStore` and `mongoDBExchangeStore` now show the server they were sent to. Before, the server was empty
