import unittest
from classify_source_scope import references_type


class SourceScopeReferenceTest(unittest.TestCase):
    def testSameSimpleNameInAnotherPackageIsNotAttributed(self):
        body = 'package example.other; class Start { SocketSetupRunnable worker; }'
        self.assertFalse(references_type(body, 'example.editor', 'SocketSetupRunnable'))
        self.assertTrue(references_type(body, 'example.other', 'SocketSetupRunnable'))

    def testExplicitWildcardAndStaticImportsAreRecognized(self):
        for declaration in ('import example.editor.SocketSetupRunnable;',
                            'import example.editor.*;',
                            'import static example.editor.SocketSetupRunnable.start;'):
            body = declaration + '\nclass Start { SocketSetupRunnable worker; }'
            self.assertTrue(references_type(body, 'example.editor', 'SocketSetupRunnable'))
        self.assertTrue(references_type('class X { example.editor.SocketSetupRunnable worker; }',
                                        'example.editor', 'SocketSetupRunnable'))

    def testImportWithoutATypeReferenceDoesNotCreateEvidence(self):
        self.assertFalse(references_type('import example.editor.*; class Start {}',
                                         'example.editor', 'SocketSetupRunnable'))


if __name__ == '__main__':
    unittest.main()
