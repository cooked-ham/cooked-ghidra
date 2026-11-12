"""Guard against treating infrastructure/owner links as actual surface acceptance."""

import unittest

from reconcile_runtime import module_coverage, reconcile


def source(identity, name, kind="plugin", module="Ghidra/Features/Example"):
    return {"id": identity, "source": module + "/src/main/java/p/" + name.split(".")[0] + ".java",
            "surface": name, "kind": kind, "module": module, "fixture": "FX-CORE"}


def runtime(kind, cls, name, owner="ExamplePlugin", path="Menu / Open"):
    return {"kind": kind, "class": cls, "name": name, "owner": owner, "entry_path": path}


class ReconcileTest(unittest.TestCase):
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
