#!/bin/sh
[ -n "${MEMBRANE_HOME:-}" ] || { echo "start_router.sh: MEMBRANE_HOME not set" >&2; exit 1; }

CLASSPATH="$MEMBRANE_HOME/conf:$MEMBRANE_HOME/lib/*"

normalize_java_opts() {
  [ -n "${JAVA_OPTS:-}" ] || return 0

  set -- $JAVA_OPTS
  NEW_OPTS=
  for tok in "$@"; do
    case "$tok" in
      -D*=*)
        key=${tok%%=*}
        val=${tok#*=}
        # Only normalize when the key is log4j.configurationFile
        if [ "$key" = "-Dlog4j.configurationFile" ]; then
          case "$val" in
            /*|~/*|[A-Za-z]:/*|\\\\*|file:*|*://*) : ;;
            *) val="$MEMBRANE_HOME/$val" ;;
          esac
          tok="$key=$val"
        fi
        ;;
    esac
    NEW_OPTS="$NEW_OPTS $tok"
  done
  JAVA_OPTS=$NEW_OPTS
}
normalize_java_opts

detect_colors() {
  # User override via environment variable (only accepts "true" or "false", case-insensitive)
  if [ -n "$MEMBRANE_DISABLE_TERM_COLORS" ]; then
    case "$(echo "$MEMBRANE_DISABLE_TERM_COLORS" | tr '[:upper:]' '[:lower:]')" in
      true)
        return 1  # Colors disabled
        ;;
      false)
        return 0  # Colors enabled
        ;;
      *)
        echo "Warning: MEMBRANE_DISABLE_TERM_COLORS must be 'true' or 'false' (case-insensitive). Ignoring value: $MEMBRANE_DISABLE_TERM_COLORS" >&2
        # Continue with auto-detection
        ;;
    esac
  fi

  # Disable colors in CI environments
  [ -n "$CI" ] && return 1

  # Check if stdout is a terminal
  [ -t 1 ] || return 1

  # Check for known IDE/terminal environments
  [ -n "$TERM_PROGRAM" ] && {
    case "$TERM_PROGRAM" in
      vscode|iTerm.app|Apple_Terminal|Hyper|WezTerm|Alacritty|kitty) return 0 ;;
    esac
  }

  # IntelliJ IDEA detection
  [ -n "$IDEA_INITIAL_DIRECTORY" ] && return 0

  # Windows Terminal when running in WSL!
  [ -n "$WT_SESSION" ] && return 0

  # Check TERM variable
  [ -n "$TERM" ] && {
    case "$TERM" in
      dumb) return 1 ;;  # Explicitly disable for dumb terminals
      *) return 0 ;;      # Enable for any other TERM value
    esac
  }

  # Default: disable colors (conservative approach)
  return 1
}

if detect_colors; then
  DISABLE_COLORS="false"
else
  DISABLE_COLORS="true"
fi

JAVA_OPTS="${JAVA_OPTS:-} -Dmembrane.disable.term.colors=$DISABLE_COLORS"

java ${JAVA_OPTS:-} -cp "$CLASSPATH" com.predic8.membrane.core.cli.RouterCLI "$@"
s=$?
[ $s -ne 0 ] && {
  echo "Membrane terminated with exit code $s" >&2
  echo "MEMBRANE_HOME: $MEMBRANE_HOME" >&2
  echo "CLASSPATH: $CLASSPATH" >&2
}
exit $s
