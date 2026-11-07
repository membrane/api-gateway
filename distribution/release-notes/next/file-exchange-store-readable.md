---
type: feature
area: Exchange Stores
---
- `fileExchangeStore`: exchanges written to disk can be listed and viewed in the admin console. Next to the `.msg` files, a `<time>-<number>-Exchange.json` file now holds the metadata of each exchange, and request and response of an exchange share the same number in their file names. The newest exchanges are kept in an index whose size is set by the new attribute `maxIndexed` (default `10000`); bodies are read from disk only when an exchange is viewed. Files written by earlier versions are not listed
