import unittest
from icon_audit import image_references, direct_references


class IconAuditTest(unittest.TestCase):
    def testCommentsDoNotCreateMissingResources(self):
        self.assertEqual(image_references("icon.graph = breakpoint-enable.png // missing breakpoints.png"),
                         [("icon.graph", "breakpoint-enable.png")])

    def testAliasesAndLookAndFeelKeysAreNotImages(self):
        self.assertEqual(image_references("icon.file = [icon]laf.icon.FileView.fileIcon"), [])
        self.assertEqual(image_references("icon.file = icon.fsbrowser.file.extension.png"), [])

    def testCppAndCompositeStateArtworkArePreserved(self):
        self.assertEqual(image_references("icon.cpp = images/oxygen/text-x-c++src.png"),
                         [("icon.cpp", "images/oxygen/text-x-c++src.png")])
        self.assertEqual(image_references("icon.state = EMPTY_ICON{ledgreen.png}[size(16,16)]"),
                         [("icon.state", "ledgreen.png")])

    def testBundledFallbacksAreNotStockBacklog(self):
        self.assertEqual(image_references("icon.add = images/uirefresh/graphite/add.png"), [])

    def testDirectLoaderSkipsCommentExamplesAndKeepsLineNumbers(self):
        text = '/* loadImageIcon("images/missing.png") */\nloadImageIcon("images/actual.png");'
        self.assertEqual(direct_references(text), [("loadImageIcon", "images/actual.png", 2)])


if __name__ == "__main__":
    unittest.main()
