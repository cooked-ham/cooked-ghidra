"""Small regression fixtures for discovery identity and nested/optional source coverage."""

from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

import inventory


class InventoryDiscoveryTest(unittest.TestCase):
    def testSpecialistFixtureRoutes(self):
        self.assertEqual(inventory.fixture("Ghidra/Features/VersionTrackingBSim"), "FX-VT")
        self.assertEqual(inventory.fixture("Ghidra/Features/GraphFunctionCalls"), "FX-GRAPH")
        self.assertEqual(inventory.fixture("Ghidra/Features/Base",
            "Ghidra/Features/Base/src/main/java/ghidra/app/merge/ListingMergePanel.java"), "FX-REPO")
        self.assertEqual(inventory.fixture("Ghidra/Framework/Project",
            "Ghidra/Framework/Project/src/main/java/p/VersionHistoryDialog.java"), "FX-REPO")
        self.assertEqual(inventory.fixture("Ghidra/Extensions/Sample"), "FX-OPTIONAL")

    def setUp(self):
        self.directory = TemporaryDirectory()
        self.original_root = inventory.ROOT
        inventory.ROOT = Path(self.directory.name)
        self.files = []

    def tearDown(self):
        inventory.ROOT = self.original_root
        self.directory.cleanup()

    def put(self, name, content):
        path = inventory.ROOT / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        if name not in self.files:
            self.files.append(name)

    def testNestedInheritanceAndOptionalBuildModule(self):
        self.put("Ghidra/Extensions/Optional/build.gradle", "")
        self.put("Ghidra/Extensions/Optional/src/main/java/p/Outer.java", """
            import javax.swing.JPanel;
            class Outer extends JPanel {
                class Inner extends Outer { }
                void first() { class Local extends JPanel { } }
                void second() { class Local extends JPanel { } }
            }
            // class Phantom extends JPanel { }
            class Other { String example = "class Fake extends JPanel { }"; }
        """)
        modules, rows, _ = inventory.discover(self.files)
        self.assertIn("Ghidra/Extensions/Optional", modules)
        self.assertEqual({r["surface"] for r in rows},
                         {"Outer", "Outer.Inner", "Outer.Local", "Outer.Local #2"})
        self.assertTrue(all(r["fixture"] == "FX-OPTIONAL" for r in rows))
        self.assertEqual(len(rows), len({r["id"] for r in rows}))

    def testIdsSurviveUnrelatedLineInsertion(self):
        self.put("Ghidra/Features/Example/Module.manifest", "")
        name = "Ghidra/Features/Example/src/main/java/p/Example.java"
        text = 'class Example extends ComponentProvider { void menu() { new MenuData("Open"); } }'
        self.put(name, text)
        _, before, _ = inventory.discover(self.files)
        self.put(name, "// Added documentation\n\n" + text)
        _, after, _ = inventory.discover(self.files)
        self.assertEqual([r["id"] for r in before], [r["id"] for r in after])
        self.assertNotEqual(before[0]["source_hash"], after[0]["source_hash"])
        self.assertNotEqual(before[0]["line"], after[0]["line"])

    def testCustomRendererAndScriptWorkflowRemainBlocked(self):
        self.put("Ghidra/Features/Example/Module.manifest", "")
        self.put("Ghidra/Features/Example/src/main/java/p/Cell.java",
                 "class Cell implements TableCellRenderer { }")
        self.put("Ghidra/Features/Example/ghidra_scripts/Prompt.py", "askString('Name', 'Value')")
        _, rows, _ = inventory.discover(self.files)
        self.assertEqual({r["kind"] for r in rows}, {"renderer", "script-workflow"})
        self.assertEqual(rows[0]["rendering_route"], "custom; individual review")
        self.assertTrue(all(r["status"] == "inventoried" and r["blockers"] for r in rows))


if __name__ == "__main__":
    unittest.main()
