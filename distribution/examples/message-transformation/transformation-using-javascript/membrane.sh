find_root() {
  dir="${PWD}"
  while [ "$dir" != "/" ]; do
    if [ -x "$dir/scripts/run-membrane.sh" ] && [ -f "$dir/starter.jar" ]; then
      echo "$dir"
      return 0
    fi
    dir=$(dirname "$dir")
  done
  return 1
}

ROOT=$(find_root) || { echo "Could not locate Membrane root. Ensure directory structure is correct." >&2; exit 1; }
exec "$ROOT/scripts/run-membrane.sh" "$@"