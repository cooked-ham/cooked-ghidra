"""Link a captured tool's runtime entries to source candidates without accepting them."""

import csv
from collections import Counter, defaultdict
from pathlib import Path
import sys

from inventory import ROOT, OUT, write_csv


def main():
    runtime_path = Path(sys.argv[1])
    sources = defaultdict(list)
    declarations = defaultdict(list)
    with (OUT / "surfaces.csv").open(newline="", encoding="utf-8") as stream:
        for row in csv.DictReader(stream):
            if row["source"].endswith(".java") and "/src/main/java/" in row["source"]:
                qualified = row["source"].split("/src/main/java/", 1)[1][:-5].replace("/", ".")
                if row["kind"] != "entry-hook":
                    sources[qualified].append(row)
                    package = qualified.rsplit(".", 1)[0]
                    declared = package + "." + row["surface"].split(" #", 1)[0].replace(".", "$")
                    declarations[declared].append(row)
    rows = []
    with runtime_path.open(newline="", encoding="utf-8-sig") as stream:
        for runtime in csv.DictReader(stream):
            name = runtime["class"].split("$", 1)[0]
            candidates = declarations.get(runtime["class"], sources.get(name, []))
            rows.append({
                **runtime,
                "source_candidates": ";".join(r["id"] for r in candidates),
                "match": "source class linked" if candidates else "unresolved/external",
                "blockers": "Observed registration only; trace action owner/entry hook, custom content, states and evidence"
            })
    rows.sort(key=lambda r: (r["kind"], r["class"], r["name"], r["entry_path"]))
    write_csv(OUT / "runtime-reconciliation.csv", rows,
              ["kind", "class", "name", "entry_path", "owner", "source_candidates", "match", "blockers"])
    print(dict(Counter(r["kind"] for r in rows)))
    print(dict(Counter(r["match"] for r in rows)))
    print("A single tool's registrations do not reconcile the full first-party inventory.")


if __name__ == "__main__":
    main()
