#!/usr/bin/env bash
# Checks the vCPU quota the 3 performance-test VMs need in LOCATION and requests an increase for
# every VM family (and the regional total) whose limit is too low. Run before provision.sh.
# Uses the same RG-independent defaults as provision.sh (LOCATION, *_SIZE) -- override via env vars.
# Prereqs: `az login`, the right subscription selected, and the `quota` CLI extension
# (installed automatically below if missing). Quota requests can take minutes to be approved, or
# be escalated to a support ticket for large increases; rerun this script to see the current state.
set -euo pipefail

LOCATION=${LOCATION:-swedencentral}
BACKEND_SIZE=${BACKEND_SIZE:-Standard_F16as_v7}
GATEWAY_SIZE=${GATEWAY_SIZE:-Standard_F16as_v7}
CLIENT_SIZE=${CLIENT_SIZE:-Standard_F16as_v7}
SIZES=("$BACKEND_SIZE" "$GATEWAY_SIZE" "$CLIENT_SIZE")

if ! az extension show --name quota >/dev/null 2>&1; then
  echo "Installing the az 'quota' extension..."
  az extension add --name quota
fi

SUBSCRIPTION=$(az account show --query id -o tsv)
SCOPE="/subscriptions/$SUBSCRIPTION/providers/Microsoft.Compute/locations/$LOCATION"
echo "Subscription: $(az account show --query name -o tsv) ($SUBSCRIPTION), location: $LOCATION"

# "family vCPUs" per size, one line each. A single list-skus call for all sizes, since it is slow.
SKU_FILTER=$(printf "name=='%s' || " "${SIZES[@]}")
SKU_FILTER=${SKU_FILTER% || }
echo "Resolving VM families and vCPU counts (az vm list-skus is slow)..."
SKUS=$(az vm list-skus -l "$LOCATION" --resource-type virtualMachines \
  --query "[?$SKU_FILTER].[name, family, capabilities[?name=='vCPUs'].value | [0], length(restrictions)]" -o tsv)

NEEDED=""   # lines of "quotaName vCPUs", summed per family below
TOTAL=0
for size in "${SIZES[@]}"; do
  line=$(awk -v s="$size" '$1 == s' <<< "$SKUS")
  if [[ -z "$line" ]]; then
    echo "VM size $size is not offered in $LOCATION -- pick another size or LOCATION." >&2
    exit 1
  fi
  read -r _ family vcpus restrictions <<< "$line"
  if [[ "$restrictions" != 0 ]]; then
    echo "VM size $size is restricted for this subscription in $LOCATION -- pick another size or LOCATION." >&2
    exit 1
  fi
  NEEDED+="$family $vcpus"$'\n'
  TOTAL=$((TOTAL + vcpus))
done
NEEDED+="cores $TOTAL"$'\n'

USAGE=$(az vm list-usage -l "$LOCATION" --query "[].[name.value, currentValue, limit]" -o tsv)

REQUESTED=0
while read -r name vcpus; do
  usage=$(awk -v n="$name" 'tolower($1) == tolower(n) {print $2, $3}' <<< "$USAGE")
  if [[ -z "$usage" ]]; then
    echo "No quota entry named '$name' in $LOCATION." >&2
    exit 1
  fi
  read -r current limit <<< "$usage"
  required=$((current + vcpus))
  if (( limit >= required )); then
    echo "OK       $name: in use $current + needed $vcpus <= limit $limit"
  else
    echo "REQUEST  $name: in use $current + needed $vcpus > limit $limit -- requesting $required"
    az quota update --resource-name "$name" --scope "$SCOPE" \
      --limit-object value="$required" --resource-type dedicated -o none
    REQUESTED=1
  fi
done < <(awk 'NF == 2 {sum[$1] += $2} END {for (n in sum) print n, sum[n]}' <<< "$NEEDED")

if (( REQUESTED )); then
  echo ""
  echo "Increase(s) requested. Check status with:"
  echo "  az quota request status list --scope \"$SCOPE\" -o table"
  echo "Rerun this script until every line says OK, then run ./provision.sh."
else
  echo ""
  echo "All quotas sufficient. Next: ./provision.sh"
fi
