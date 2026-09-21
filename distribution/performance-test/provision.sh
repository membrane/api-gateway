#!/usr/bin/env bash
# Provisions 3 Azure VMs (backend, gateway, client) in one VNet for the Membrane performance
# test. Prereqs: an Azure account/subscription and the `az` CLI, with `az login` and
# `az account set --subscription <id>` done once, interactively, beforehand.
set -euo pipefail

RG=${RG:-membrane-perftest-rg}
# Pick a region with enough quota for 3x16 vCPUs of this VM family; if `az quota update` or VM
# creation stalls/fails in your default region, try another one -- quota approval speed and
# availability both vary noticeably by region.
LOCATION=${LOCATION:-swedencentral}
# Per-role sizes, found empirically to give the best throughput for each role (see
# TESTED-CONFIGURATIONS.md for the comparisons that led here) -- override via env vars if your
# subscription's quota doesn't support them, or to go back to a uniform size for a cleaner
# single-variable comparison (all three were `Standard_F16as_v6` in earlier rounds).
# NOTE: these are non-default quota families; you will likely need `az quota update` for
# `StandardFasv7Family` and `StandardFXmdsv2Family` in your target region before `az vm create`
# below succeeds -- see the quota check this script runs next.
BACKEND_SIZE=${BACKEND_SIZE:-Standard_F16as_v7}
GATEWAY_SIZE=${GATEWAY_SIZE:-Standard_FX16mds_v2}
CLIENT_SIZE=${CLIENT_SIZE:-Standard_F16as_v6}
IMAGE=Canonical:ubuntu-24_04-lts:server:latest
ADMIN_USER=azureuser
VNET=lt-vnet
SUBNET=lt-subnet
NSG=lt-nsg

echo "Resolving your current public IPv4 for the SSH NSG rule..."
MY_IP=$(curl -4 -s ifconfig.me)/32
echo "Using $MY_IP"

echo "Verifying the Ubuntu image alias is still valid (Azure rotates these)..."
az vm image list --publisher Canonical --sku 24_04-lts-gen2 --all -o table || true
echo "If IMAGE above looks wrong, edit provision.sh before continuing."

echo ""
echo "Checking vCPU quota in $LOCATION..."
az vm list-usage --location "$LOCATION" -o table | grep -Ei "Total Regional vCPUs|Fasv6 Family|Fasv7 Family|FXmdsv2 Family" || true
echo "(If CurrentValue + this test's needed cores > Limit above, request a quota increase, e.g.:"
echo "   az quota update --resource-name StandardFasv7Family --scope \"/subscriptions/\$(az account show --query id -o tsv)/providers/Microsoft.Compute/locations/$LOCATION\" --limit-object value=32 --resource-type dedicated"
echo "   az quota update --resource-name StandardFXmdsv2Family --scope \"/subscriptions/\$(az account show --query id -o tsv)/providers/Microsoft.Compute/locations/$LOCATION\" --limit-object value=32 --resource-type dedicated"
echo " -- or pick different sizes/LOCATION.)"

az group create -n "$RG" -l "$LOCATION"

az network vnet create -g "$RG" -n "$VNET" --address-prefix 10.10.0.0/24 \
  --subnet-name "$SUBNET" --subnet-prefix 10.10.0.0/24

az network nsg create -g "$RG" -n "$NSG"

az network nsg rule create -g "$RG" --nsg-name "$NSG" -n AllowSSH \
  --priority 100 --access Allow --protocol Tcp --direction Inbound \
  --source-address-prefixes "$MY_IP" --destination-port-ranges 22

# Belt-and-braces: Azure's default AllowVnetInBound already permits intra-VNet traffic.
az network nsg rule create -g "$RG" --nsg-name "$NSG" -n AllowGatewayPort \
  --priority 110 --access Allow --protocol Tcp --direction Inbound \
  --source-address-prefixes 10.10.0.0/24 --destination-port-ranges 2000

az network nsg rule create -g "$RG" --nsg-name "$NSG" -n AllowBackendPort \
  --priority 120 --access Allow --protocol Tcp --direction Inbound \
  --source-address-prefixes 10.10.0.0/24 --destination-port-ranges 2010

declare -A SIZES=( [lt-backend]="$BACKEND_SIZE" [lt-gateway]="$GATEWAY_SIZE" [lt-client]="$CLIENT_SIZE" )

CLOUDINIT_FILE=$(mktemp)
trap 'rm -f "$CLOUDINIT_FILE"' EXIT
# Ubuntu 24.04's own apt repos only carry OpenJDK up to 21, which is also the fastest JVM version
# we measured for this workload (see TESTED-CONFIGURATIONS.md: Java 25/26 and GraalVM CE all came
# out behind Temurin 21 here) -- so this installs Temurin 21 explicitly from the Eclipse Temurin
# apt repo (packages.adoptium.net) rather than relying on whatever `openjdk-21-jdk` resolves to,
# keeping the exact vendor/build reproducible. The repo also carries 22-26 side by side
# (temurin-<N>-jdk) if you want to re-run the JVM-version comparison yourself.
#
# net.core.somaxconn is raised well above the kernel default (1024 or 4096 depending on the
# image) so a config's `<transport backlog=".../>` is never silently capped by the kernel,
# regardless of what value a scenario config asks for.
cat > "$CLOUDINIT_FILE" <<'CLOUDINIT'
#cloud-config
package_update: true
packages:
  - curl
  - unzip
  - sysstat
  - wget
  - gnupg
runcmd:
  - mkdir -p /etc/apt/keyrings
  - wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
  - echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb noble main" > /etc/apt/sources.list.d/adoptium.list
  - apt-get update
  - apt-get install -y temurin-21-jdk
  - echo "net.core.somaxconn=65535" > /etc/sysctl.d/99-loadtest.conf
  - sysctl -p /etc/sysctl.d/99-loadtest.conf
CLOUDINIT

for NAME in lt-backend lt-gateway lt-client; do
  echo "Creating VM $NAME (${SIZES[$NAME]})..."
  az vm create -g "$RG" -n "$NAME" \
    --image "$IMAGE" \
    --size "${SIZES[$NAME]}" \
    --vnet-name "$VNET" --subnet "$SUBNET" \
    --nsg "$NSG" \
    --admin-username "$ADMIN_USER" \
    --generate-ssh-keys \
    --public-ip-sku Standard \
    --accelerated-networking true \
    --custom-data "$CLOUDINIT_FILE"
done

echo ""
echo "Done. VM public IPs:"
az vm list-ip-addresses -g "$RG" -o table

echo ""
echo "Waiting ~120s for cloud-init to add the Adoptium repo and install Java 21 before you deploy..."
sleep 120
echo "Verify with: ssh $ADMIN_USER@<public-ip> java -version (expect Temurin 21)"
echo ""
echo "Next: ./deploy-and-run.sh"
