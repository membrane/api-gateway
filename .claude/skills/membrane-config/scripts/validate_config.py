#!/usr/bin/env python3
"""Validate a Membrane YAML configuration against membrane.schema.json.

Usage:
    python3 validate_config.py path/to/apis.yaml [more.yaml ...]

What it does
------------
- Finds the JSON schema. Prefers the locally built copy
  (core/target/classes/com/predic8/membrane/core/config/json/membrane.schema.json),
  searching upward from the config file and from this script. Falls back to the
  published schema, downloaded once into a cache dir.
- Each YAML file may contain several documents separated by `---`; Membrane
  treats each as one proxy definition, and the schema requires exactly one
  top-level key per document (api / global / configuration / ...). Every
  document is validated independently.
- Reports the JSON path of each violation so you can find it in the file.

Dependencies (jsonschema, pyyaml) are bootstrapped into a private venv on first
run, so the script works on a stock Python without touching system packages.
"""
import json
import os
import subprocess
import sys
import urllib.request

PUBLISHED_SCHEMA_URL = "https://www.membrane-api.io/v7.2.3.json"
LOCAL_SCHEMA_REL = "core/target/classes/com/predic8/membrane/core/config/json/membrane.schema.json"
CACHE_DIR = os.path.join(os.path.expanduser("~"), ".cache", "membrane-config-skill")


# --- dependency bootstrap -------------------------------------------------
def ensure_deps():
    try:
        import jsonschema  # noqa: F401
        import yaml  # noqa: F401
        return
    except ImportError:
        pass
    venv = os.path.join(CACHE_DIR, "venv")
    py = os.path.join(venv, "bin", "python")
    if not os.path.exists(py):
        os.makedirs(CACHE_DIR, exist_ok=True)
        print("Bootstrapping validation deps (one-time)...", file=sys.stderr)
        subprocess.check_call([sys.executable, "-m", "venv", venv])
        subprocess.check_call(
            [py, "-m", "pip", "install", "--quiet", "jsonschema", "pyyaml"]
        )
    # re-exec inside the venv
    os.execv(py, [py, os.path.abspath(__file__)] + sys.argv[1:])


# --- schema location ------------------------------------------------------
def find_local_schema(start_paths):
    seen = set()
    for start in start_paths:
        d = os.path.abspath(start)
        while d and d not in seen:
            seen.add(d)
            candidate = os.path.join(d, LOCAL_SCHEMA_REL)
            if os.path.isfile(candidate):
                return candidate
            parent = os.path.dirname(d)
            if parent == d:
                break
            d = parent
    return None


def download_schema():
    os.makedirs(CACHE_DIR, exist_ok=True)
    cached = os.path.join(CACHE_DIR, "v7.2.3.json")
    if not os.path.isfile(cached):
        print(f"Downloading schema from {PUBLISHED_SCHEMA_URL} ...", file=sys.stderr)
        urllib.request.urlretrieve(PUBLISHED_SCHEMA_URL, cached)
    return cached


def load_schema(config_paths):
    starts = [os.path.dirname(p) for p in config_paths] + [
        os.path.dirname(os.path.abspath(__file__)),
        os.getcwd(),
    ]
    schema_path = find_local_schema(starts)
    source = "local build"
    if not schema_path:
        schema_path = download_schema()
        source = "published (v7.2.3)"
    with open(schema_path) as f:
        return json.load(f), schema_path, source


# --- validation -----------------------------------------------------------
def validate_file(path, schema):
    import yaml
    from jsonschema import Draft202012Validator

    with open(path) as f:
        try:
            docs = list(yaml.safe_load_all(f))
        except yaml.YAMLError as e:
            return [f"YAML parse error: {e}"]

    validator = Draft202012Validator(schema)
    problems = []
    real_docs = [d for d in docs if d is not None]
    for i, doc in enumerate(real_docs):
        prefix = f"document #{i + 1}: " if len(real_docs) > 1 else ""
        for err in sorted(validator.iter_errors(doc), key=lambda e: list(e.path)):
            loc = "/".join(str(p) for p in err.path) or "(root)"
            problems.append(f"{prefix}{loc}: {err.message}")
    return problems


def main():
    ensure_deps()
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        sys.exit(2)

    schema, schema_path, source = load_schema(args)
    print(f"Schema: {schema_path} [{source}]\n")

    total = 0
    for path in args:
        if not os.path.isfile(path):
            print(f"✗ {path}: file not found")
            total += 1
            continue
        problems = validate_file(path, schema)
        if problems:
            print(f"✗ {path}: {len(problems)} problem(s)")
            for p in problems:
                print(f"    - {p}")
            total += len(problems)
        else:
            print(f"✓ {path}: valid")
    print()
    sys.exit(1 if total else 0)


if __name__ == "__main__":
    main()
