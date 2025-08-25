resolve() {
  target="$1"
  while [ -h "$target" ]; do
    link=$(ls -ld "$target" 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($(i)=="->"){print $(i+1); exit}}')
    case "$link" in
      /*) target="$link" ;;
      *)  target=$(dirname "$target")/"$link" ;;
    esac
  done
  dir=$(cd "$(dirname "$target")" 2>/dev/null && pwd) || exit 1
  echo "$dir/$(basename "$target")"
}

SCRIPT_PATH=$(resolve "$0")
SCRIPTS_DIR=$(cd "$(dirname "$SCRIPT_PATH")" && pwd)

if [ -z "${MEMBRANE_HOME:-}" ] || [ ! -f "$MEMBRANE_HOME/starter.jar" ]; then
  MEMBRANE_HOME=$(cd "$SCRIPTS_DIR/.." && pwd)
fi
export MEMBRANE_HOME

: "${MEMBRANE_REQUIRED_JAVA_VERSION:=21}"

"$SCRIPTS_DIR/java_check.sh" || exit 1

exec "$SCRIPTS_DIR/start_router.sh" "$@"
