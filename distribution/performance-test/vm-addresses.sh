# Shared Azure address discovery. Source this file after setting RG.
loadtest_vm_ip() {
  local vm="$1" kind="$2" query address
  case "$kind" in
    public) query='[0].virtualMachine.network.publicIpAddresses[0].ipAddress' ;;
    private) query='[0].virtualMachine.network.privateIpAddresses[0]' ;;
    *) return 1 ;;
  esac
  if ! address=$(az vm list-ip-addresses -g "$RG" -n "$vm" --query "$query" -o tsv); then
    echo "Azure address lookup failed for '$vm' in resource group '$RG'. Check az login and the selected subscription." >&2
    return 1
  fi
  if [[ -z "$address" || "$address" == null || "$address" == None || "$address" == *[[:space:]]* ]]; then
    echo "No $kind IP found for '$vm' in resource group '$RG'." >&2
    echo "Check the subscription with 'az account show' and select the subscription used for provisioning." >&2
    echo "If you used a custom resource group, set RG to that name. If the VMs were deleted, provision them again." >&2
    return 1
  fi
  printf '%s\n' "$address"
}

discover_vm_ips() {
  BACKEND_PUB=$(loadtest_vm_ip lt-backend public) || return 1
  BACKEND_PRIV=$(loadtest_vm_ip lt-backend private) || return 1
  GATEWAY_PUB=$(loadtest_vm_ip lt-gateway public) || return 1
  GATEWAY_PRIV=$(loadtest_vm_ip lt-gateway private) || return 1
  CLIENT_PUB=$(loadtest_vm_ip lt-client public) || return 1
}
