#!/usr/bin/env python3
"""Look up a Membrane configuration element in membrane.schema.json.

Every config element (api, target, setHeader, choose, rateLimiter, ...) is
backed by a schema definition that lists its exact attributes, allowed enum
values, and nested children. Guessing attribute names is the main way a
generated config goes wrong, so use this to check the real names instead.

Usage:
    python3 describe_element.py <name>     # show one element's attributes
    python3 describe_element.py --list     # list every known element id
    python3 describe_element.py --grep foo # list element ids containing "foo"

The schema is located the same way as validate_config.py: the locally built
copy is preferred, otherwise the published schema is downloaded and cached.
"""
import json
import os
import sys
import urllib.request

PUBLISHED_SCHEMA_URL = "https://www.membrane-api.io/v7.2.3.json"
LOCAL_SCHEMA_REL = "core/target/classes/com/predic8/membrane/core/config/json/membrane.schema.json"
CACHE_DIR = os.path.join(os.path.expanduser("~"), ".cache", "membrane-config-skill")


def find_local_schema():
    starts = [os.getcwd(), os.path.dirname(os.path.abspath(__file__))]
    seen = set()
    for start in starts:
        d = os.path.abspath(start)
        while d and d not in seen:
            seen.add(d)
            cand = os.path.join(d, LOCAL_SCHEMA_REL)
            if os.path.isfile(cand):
                return cand
            parent = os.path.dirname(d)
            if parent == d:
                break
            d = parent
    return None


def load_schema():
    path = find_local_schema()
    if not path:
        os.makedirs(CACHE_DIR, exist_ok=True)
        path = os.path.join(CACHE_DIR, "v7.2.3.json")
        if not os.path.isfile(path):
            print(f"Downloading schema from {PUBLISHED_SCHEMA_URL} ...", file=sys.stderr)
            urllib.request.urlretrieve(PUBLISHED_SCHEMA_URL, path)
    with open(path) as f:
        return json.load(f), path


def id_map(schema):
    """Map element id (the YAML key, e.g. 'setHeader') -> definition."""
    out = {}
    for key, d in schema.get("$defs", {}).items():
        if isinstance(d, dict) and "id" in d:
            out.setdefault(d["id"], (key, d))
    return out


def short_type(prop, defs):
    if "$ref" in prop:
        ref = prop["$ref"].split("/")[-1]
        child = defs.get(ref, {})
        return f"<{child.get('id', ref)}>"
    if "enum" in prop:
        return "enum(" + "|".join(map(str, prop["enum"])) + ")"
    if "anyOf" in prop:
        return " | ".join(short_type(p, defs) for p in prop["anyOf"])
    if "items" in prop:
        return f"[{short_type(prop['items'], defs)}]"
    t = prop.get("type", "?")
    return "/".join(t) if isinstance(t, list) else t


def describe(name, schema):
    defs = schema.get("$defs", {})
    ids = id_map(schema)
    if name not in ids:
        matches = sorted(k for k in ids if name.lower() in k.lower())
        print(f"No element named '{name}'.")
        if matches:
            print("Did you mean: " + ", ".join(matches))
        else:
            print("Use --list to see all element ids.")
        return 1
    key, d = ids[name]
    print(f"# {name}")
    props = d.get("properties", {})
    if not props:
        print("(no attributes)")
    else:
        print("attributes:")
        for pname, p in props.items():
            if pname == "$ref":
                continue
            print(f"  {pname}: {short_type(p, defs)}")
    if d.get("additionalProperties", True):
        print("(accepts additional/free-form properties)")
    return 0


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return 2
    schema, path = load_schema()
    if args[0] == "--list":
        for k in sorted(id_map(schema)):
            print(k)
        return 0
    if args[0] == "--grep":
        needle = args[1].lower() if len(args) > 1 else ""
        for k in sorted(id_map(schema)):
            if needle in k.lower():
                print(k)
        return 0
    return describe(args[0], schema)


if __name__ == "__main__":
    sys.exit(main())
