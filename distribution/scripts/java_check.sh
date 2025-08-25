required="${MEMBRANE_REQUIRED_JAVA_VERSION:-21}"

if ! command -v java >/dev/null 2>&1; then
  echo "Java is not installed. Membrane needs at least Java $required." >&2
  exit 1
fi

vline="$(java -version 2>&1 | head -n1)"
[ -z "$vline" ] && vline="$(java --version 2>/dev/null | head -n1)"

case "$vline" in
  *version*\"*\"*) full="$(printf '%s' "$vline" | sed -n 's/.*version \"\([^\"]*\)\".*/\1/p')" ;;
  *)               full="$(printf '%s' "$vline" | awk '{print $2}')" ;;
esac

if [ -z "$full" ]; then
  echo "WARNING: Could not determine Java version. Proceeding anyway..." >&2
  exit 0
fi

major=$(printf '%s' "$full" | awk -F[._-] '{if($1=="1") print $2; else print $1}')
case "$major" in
  ''|*[!0-9]*)
    echo "WARNING: Could not parse Java version \"$full\". Proceeding anyway..." >&2
    exit 0
    ;;
esac

if [ "$major" -lt "$required" ]; then
  echo "Java version mismatch: Required=$required, Installed=$full" >&2
  exit 1
fi
exit 0
