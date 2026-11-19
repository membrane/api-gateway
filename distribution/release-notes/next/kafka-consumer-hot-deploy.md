---
type: improvement
---
- `kafkaConsumer` now works with hot deployment, which is on by default. When `apis.yaml` changes, the consumers of the old configuration are stopped before the new configuration starts. Before, `kafkaConsumer` refused to start unless `hotDeploy` was turned off
