"""Write original raw binaries and adapt the shipped demo trace for headless saving."""

import hashlib
import json
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]


def prepare(output):
    binaries = output / "binaries"
    binaries.mkdir(parents=True)
    hashes = {}
    for name in ("source", "destination", "merge-base", "merge-latest", "merge-private"):
        data = bytearray(1024)
        classify = bytes.fromhex("554889e583f92a7507b801000000eb05b8000000005dc3")
        accumulate = bytes.fromhex("31c085c97e0b31d201d083c20139ca7cf7c3")
        data[:len(classify)] = classify
        data[0x40:0x40 + len(accumulate)] = accumulate
        for offset in (0x80, 0xa0):
            data[offset:offset + 6] = bytes.fromhex("b82a000000c3")
        offset = 0x140 if name == "destination" else 0x100
        data[offset:offset + 6] = bytes.fromhex("b807000000c3")
        if name == "destination":
            data[6] = 43
        if name in {"merge-latest", "merge-private"}:
            data[6] = 44 if name == "merge-latest" else 45
        text = b"Specialist UI fixture: symbols, matches and changed values\0"
        data[0x280:0x280 + len(text)] = text
        path = binaries / (name + ".bin")
        path.write_bytes(data)
        hashes[path.name] = hashlib.sha256(data).hexdigest()
    demo = ROOT / "Ghidra/Debug/Debugger/ghidra_scripts/PopulateDemoTrace.java"
    original = demo.read_text(encoding="utf-8")
    anchor = """DebuggerTraceManagerService manager =
			state.getTool().getService(DebuggerTraceManagerService.class);
		manager.openTrace(trace);
		manager.activateTrace(trace);"""
    if original.count(anchor) != 1:
        raise ValueError("Demo trace save anchor changed; review adaptation")
    adapted = original.replace("public class PopulateDemoTrace", "public class CreateUiDemoTrace")
    adapted = adapted.replace(anchor, """try {
            var folder = state.getProject().getProjectData().getRootFolder();
            folder.createFile("UI Debugger Trace", trace, monitor);
            trace.save("Saved UI specialist demo trace", monitor);
            if (trace.getTimeManager().getAllSnapshots().size() < 20 ||
                    trace.getThreadManager().getAllThreads().size() != 2) {
                throw new AssertionError("Incomplete saved debugger trace");
            }
            java.nio.file.Files.writeString(java.nio.file.Paths.get(getScriptArgs()[0], "trace.ready"),
                "Saved synthetic x86-64 trace: " + trace.getTimeManager().getAllSnapshots().size() +
                " snapshots, two threads, stack/register/memory changes; no live debugger session\\n");
        }
        finally { trace.release(this); }""")
    (output / "CreateUiDemoTrace.java").write_text(adapted, encoding="utf-8")
    manifest = {"schema": 1, "raw_x86_64_sha256": hashes,
                "trace_source": str(demo.relative_to(ROOT)).replace("\\", "/"),
                "trace_source_sha256": hashlib.sha256(original.encode()).hexdigest(),
                "adapted_trace_sha256": hashlib.sha256(adapted.encode()).hexdigest(),
                "merge": "staged independent inputs only; repository ancestry/conflict workflow still required"}
    (output / "inputs.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    prepare(Path(sys.argv[1]))
