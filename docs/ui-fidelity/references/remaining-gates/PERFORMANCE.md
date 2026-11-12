# Stock/modern performance measurements

Same fixture and process, ABBA theme order, eight warmup paints and 32 measured paints per block.
Graph: 257 vertices / 384 edges, detail and 5% overview. Table: 10,000 rows, moving viewport and filter/reset round
trips.
Allocation is for the EDT only. Filtering allocation is not measured. Paint buffers are allocated before timing.
These are logical Swing paint measurements on this Windows host, not physical presentation latency or a historical
release baseline.
No release performance threshold was supplied; the figures below are measurements, not a fabricated pass criterion.

| Scale | Workload               | Stock median / p95 ms | Modern median / p95 ms | Modern / stock median |
|-------|------------------------|-----------------------|------------------------|-----------------------|
| 1.0   | large-graph-detail     | 9.091 / 17.175        | 12.344 / 16.722        | 1.36×                 |
| 1.0   | large-graph-overview   | 8.821 / 11.755        | 12.138 / 15.851        | 1.38×                 |
| 1.0   | table-10000-scroll     | 0.91 / 1.644          | 0.678 / 1.137          | 0.75×                 |
| 1.0   | table-filter-roundtrip | 0.834 / 1.28          | 0.607 / 0.927          | 0.73×                 |
| 1.25  | large-graph-detail     | 8.733 / 14.038        | 11.16 / 14.599         | 1.28×                 |
| 1.25  | large-graph-overview   | 8.312 / 10.909        | 11.683 / 18.833        | 1.41×                 |
| 1.25  | table-10000-scroll     | 0.767 / 1.727         | 0.82 / 1.305           | 1.07×                 |
| 1.25  | table-filter-roundtrip | 0.578 / 1.315         | 0.661 / 0.866          | 1.14×                 |
| 1.5   | large-graph-detail     | 10.15 / 18.343        | 10.777 / 12.054        | 1.06×                 |
| 1.5   | large-graph-overview   | 10.959 / 18.06        | 11.366 / 13.193        | 1.04×                 |
| 1.5   | table-10000-scroll     | 0.965 / 1.735         | 0.615 / 1.198          | 0.64×                 |
| 1.5   | table-filter-roundtrip | 0.835 / 1.445         | 0.541 / 0.895          | 0.65×                 |
| 2.0   | large-graph-detail     | 8.17 / 15.01          | 10.317 / 13.371        | 1.26×                 |
| 2.0   | large-graph-overview   | 7.828 / 8.989         | 10.647 / 11.952        | 1.36×                 |
| 2.0   | table-10000-scroll     | 0.716 / 1.637         | 0.679 / 1.176          | 0.95×                 |
| 2.0   | table-filter-roundtrip | 0.807 / 1.272         | 0.745 / 0.86           | 0.92×                 |
