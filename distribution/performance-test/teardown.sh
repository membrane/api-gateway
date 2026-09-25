#!/usr/bin/env bash
# Deletes the entire performance-test resource group (all 3 VMs, VNet, NSG, public IPs, disks).
set -euo pipefail

RG=${RG:-membrane-perftest-rg}

echo "This will permanently delete resource group '$RG' and everything in it."
read -r -p "Type the resource group name to confirm: " CONFIRM
if [ "$CONFIRM" != "$RG" ]; then
  echo "Aborted."
  exit 1
fi

az group delete -n "$RG" --yes --no-wait
echo "Deletion started (--no-wait). Check status with: az group show -n $RG"
