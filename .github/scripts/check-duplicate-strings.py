#!/usr/bin/env python3
"""Fails when a Compose resource string id is declared in more than one values file.

Compose Resources builds fine with a duplicate id, then throws "Resource ... has more than one
file" at runtime the first time the string is drawn.
"""
import glob
import os
import re
import sys

failed = False
for folder in sorted(glob.glob("composeApp/src/commonMain/composeResources/values*")):
    seen = {}
    for path in sorted(glob.glob(os.path.join(folder, "*.xml"))):
        text = open(path, encoding="utf-8").read()
        for name in re.findall(r'<(?:string|plurals|string-array) name="([^"]+)"', text):
            seen.setdefault(name, []).append(os.path.basename(path))
    for name, files in seen.items():
        if len(set(files)) > 1:
            print(f"::error title=duplicate string::{folder}: '{name}' is in {', '.join(sorted(set(files)))}")
            failed = True
sys.exit(1 if failed else 0)
