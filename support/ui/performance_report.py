"""Summarize retained paired stock/modern measurements without inventing release thresholds."""
import argparse
import csv
import json
import statistics
from collections import defaultdict
from pathlib import Path


def percentile(values, fraction):
    values = sorted(values)
    index = (len(values) - 1) * fraction
    low = int(index)
    return values[low] + (values[min(low + 1, len(values) - 1)] - values[low]) * (index - low)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("runs", nargs="+", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    summary = []
    for run in args.runs:
        groups = defaultdict(list)
        path = run / "review/acceptance/performance.csv"
        manifest = json.loads((run / "specialist-ui-manifest.json").read_text(encoding="utf-8-sig"))
        for row in csv.DictReader(path.open(encoding="utf-8-sig")):
            groups[(row["theme"], row["workload"])].append(row)
        for (theme, workload), rows in sorted(groups.items()):
            times = [float(row["milliseconds"]) for row in rows]
            allocation = [int(row["allocated_bytes"]) for row in rows if int(row["allocated_bytes"]) >= 0]
            summary.append({"logical_scale": manifest["logicalScale"], "theme": theme, "workload": workload,
                "samples": len(rows), "median_ms": round(statistics.median(times), 3),
                "p95_ms": round(percentile(times, .95), 3),
                "median_allocated_bytes": round(statistics.median(allocation)) if allocation else "not measured",
                "source_run": run.as_posix()})
    with (args.output / "performance-summary.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(summary[0])); writer.writeheader(); writer.writerows(summary)
    lines = ["# Stock/modern performance measurements", "",
        "Same fixture and process, ABBA theme order, eight warmup paints and 32 measured paints per block.",
        "Graph: 257 vertices / 384 edges, detail and 5% overview. Table: 10,000 rows, moving viewport and filter/reset round trips.",
        "Allocation is for the EDT only. Filtering allocation is not measured. Paint buffers are allocated before timing.",
        "These are logical Swing paint measurements on this Windows host, not physical presentation latency or a historical release baseline.",
        "No release performance threshold was supplied; the figures below are measurements, not a fabricated pass criterion.", "",
        "| Scale | Workload | Stock median / p95 ms | Modern median / p95 ms | Modern / stock median |", "| --- | --- | --- | --- | --- |"]
    for row in summary:
        if row["theme"] != "Graphite Preview":
            continue
        stock = next(r for r in summary if r["theme"] == "Flat Dark Theme" and r["logical_scale"] == row["logical_scale"] and r["workload"] == row["workload"])
        lines.append(f'| {row["logical_scale"]} | {row["workload"]} | {stock["median_ms"]} / {stock["p95_ms"]} | {row["median_ms"]} / {row["p95_ms"]} | {row["median_ms"] / stock["median_ms"]:.2f}× |')
    (args.output / "PERFORMANCE.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Summarized {len(summary)} workload/theme/scale groups.")


if __name__ == "__main__":
    main()
