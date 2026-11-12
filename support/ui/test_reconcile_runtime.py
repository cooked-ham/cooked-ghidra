"""Guard against treating infrastructure/owner links as actual surface acceptance."""

import unittest
import tempfile
from pathlib import Path
from unittest.mock import patch

from reconcile_runtime import module_coverage, reconcile


def source(identity, name, kind="plugin", module="Ghidra/Features/Example"):
    return {"id": identity, "source": module + "/src/main/java/p/" + name.split(".")[0] + ".java",
            "surface": name, "kind": kind, "module": module, "fixture": "FX-CORE"}


def runtime(kind, cls, name, owner="ExamplePlugin", path="Menu / Open"):
    return {"kind": kind, "class": cls, "name": name, "owner": owner, "entry_path": path}


class ReconcileTest(unittest.TestCase):
    def testAnonymousBytecodeLineIsRetained(self):
        item = runtime("action", "p.ExamplePlugin$1", "Open")
        item.update(source_file="ExamplePlugin.java", source_line="73", callback_host="")
        result = reconcile([("new", [item])], [source("plugin", "ExamplePlugin")])[0]
        self.assertEqual(result["handler_line"], "73")
        self.assertEqual(result["handler_match"], "runtime class bytecode source line")

    def testCallbackHostTracesBuilderToActualSource(self):
        sources = [source("plugin", "ExamplePlugin"), source("builder", "ActionBuilder", "component")]
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / sources[0]["source"]
            path.parent.mkdir(parents=True)
            path.write_text('class ExamplePlugin {\n new ActionBuilder("Open", owner);\n}')
            item = runtime("action", "p.ActionBuilder$1", "Open", "ServiceOwner")
            item["callback_host"] = "p.ExamplePlugin"
            with patch("reconcile_runtime.ROOT", Path(temporary)):
                result = reconcile([("new", [item])], sources)[0]
        self.assertEqual(result["source_candidates"], "builder")
        self.assertEqual(result["owner_candidates"], "")
        self.assertEqual(result["handler_source"], sources[0]["source"])
        self.assertEqual(result["handler_line"], "2")

    def testAmbiguousCallbackAnchorStaysExplicit(self):
        sources = [source("plugin", "ExamplePlugin")]
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / sources[0]["source"]
            path.parent.mkdir(parents=True)
            path.write_text('new ActionBuilder("Open", owner); new ActionBuilder("Open", owner);')
            item = runtime("action", "p.ActionBuilder$1", "Open")
            item["callback_host"] = "p.ExamplePlugin"
            with patch("reconcile_runtime.ROOT", Path(temporary)):
                result = reconcile([("new", [item])], sources)[0]
        self.assertEqual(result["handler_line"], "")
        self.assertIn("ambiguous", result["handler_match"])

    def testMismatchedDebugSourceDoesNotClaimHandler(self):
        item = runtime("action", "p.ExamplePlugin$1", "Open")
        item.update(source_file="Different.java", source_line="73")
        result = reconcile([("new", [item])], [source("plugin", "ExamplePlugin")])[0]
        self.assertEqual(result["handler_source"], "")
        self.assertIn("required", result["handler_match"])

    def testNonPublicTopLevelActionUsesDebugSourceFile(self):
        item = runtime("action", "p.HelperAction", "Open")
        item.update(source_file="ExamplePlugin.java", source_line="82")
        result = reconcile([("new", [item])], [source("plugin", "ExamplePlugin")])[0]
        self.assertEqual(result["handler_line"], "82")
        self.assertEqual(result["handler_match"], "runtime class bytecode source line")

    def testBuilderActionKeepsOwnerAndImplementationSeparate(self):
        sources = [source("plugin", "ExamplePlugin"), source("infra", "ActionBuilder", "component",
                    "Ghidra/Framework/Docking")]
        rows = reconcile([("browser.csv", [runtime("plugin", "p.ExamplePlugin", "ExamplePlugin"),
            runtime("action", "p.ActionBuilder$1", "Open")])], sources)
        action = next(r for r in rows if r["kind"] == "action")
        self.assertEqual(action["source_candidates"], "infra")
        self.assertEqual(action["owner_candidates"], "plugin")
        self.assertIn("enclosing source file", action["class_match"])
        self.assertTrue(action["blockers"])

    def testCapturesDoNotLeakOwnersOrHideAmbiguity(self):
        sources = [source("a", "A"), source("b", "B")]
        captures = [("one", [runtime("plugin", "p.A", "Same"), runtime("plugin", "p.B", "Same"),
                             runtime("action", "external.Action", "Open", "Same")]),
                    ("two", [runtime("action", "external.Action", "Open", "Same")])]
        rows = [r for r in reconcile(captures, sources) if r["kind"] == "action"]
        self.assertEqual(rows[0]["owner_match"], "ambiguous runtime owner")
        self.assertEqual(rows[0]["owner_candidates"], "a;b")
        self.assertEqual(rows[1]["owner_candidates"], "")

    def testExactNestedDeclarationAndUnobservedModule(self):
        sources = [source("outer", "Outer", "component"),
                   source("nested", "Outer.Dialog", "dialog"),
                   source("optional", "Missing", module="Ghidra/Extensions/Optional")]
        rows = reconcile([("browser", [runtime("dialog", "p.Outer$Dialog", "Settings")])], sources)
        self.assertEqual(rows[0]["source_candidates"], "nested")
        coverage = module_coverage(sources, rows)
        optional = next(r for r in coverage if r["module"].endswith("Optional"))
        self.assertEqual(optional["gate"], "no runtime evidence")
        self.assertEqual(optional["registrations"], 0)

    def testInstalledDoesNotMeanRegisteredOrAccepted(self):
        sources = [source("a", "A")]
        coverage = module_coverage(sources, [], {"Ghidra/Features/Example"},
                                   ["Ghidra/Features/Example", "Ghidra/Extensions/Uninstalled"])
        example = next(r for r in coverage if r["module"].endswith("Example"))
        self.assertEqual(example["installation"], "active module root captured")
        self.assertEqual(example["gate"], "no runtime evidence")
        absent = next(r for r in coverage if r["module"].endswith("Uninstalled"))
        self.assertIn("absent", absent["installation"])
        self.assertTrue(absent["blockers"])


if __name__ == "__main__":
    unittest.main()
