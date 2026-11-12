param(
    [string]$OriginalProbe = 'E:\ghidra-ui-refresh\extension\GhidraUiRefresh\src\main\java\uirefresh\UiRefreshHarnessPlugin.java',
    [string]$Tag = ('search-graph-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
)
$ErrorActionPreference = 'Stop'
if ($Tag -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Use a simple capture tag.' }
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$sourceDir = Join-Path $workspace "build\verification\$Tag-source"
if (Test-Path -LiteralPath $sourceDir) { throw 'Choose a new capture tag.' }
$null = New-Item -ItemType Directory -Path $sourceDir
$source = [IO.File]::ReadAllText($OriginalProbe)
$marker = 'if (want.equals("(polish)")) {'
if (!$source.Contains($marker)) { throw 'Probe command dispatcher changed.' }
$source = $source.Replace($marker, @'
if (want.equals("(memory)")) {
                    reviewMemorySearch(out, lines);
                    i++;
                    continue;
                }
                if (want.equals("(polish)")) {
'@)
$review = @'
    private void reviewMemorySearch(String out, java.util.List<String> lines) throws Exception {
        final ghidra.features.base.memsearch.gui.MemorySearchProvider[] provider =
            new ghidra.features.base.memsearch.gui.MemorySearchProvider[1];
        onEdt(() -> {
            var service = tool.getService(ghidra.app.services.MemorySearchService.class);
            if (service == null) throw new IllegalStateException("Memory Search service missing");
            var navigatable = tool.getService(ghidra.app.services.GoToService.class).getDefaultNavigatable();
            service.createMemorySearchProvider(navigatable, "ff ee dd cc",
                new ghidra.features.base.memsearch.gui.SearchSettings(), false);
            provider[0] = (ghidra.features.base.memsearch.gui.MemorySearchProvider)
                tool.getComponentProvider("Memory Search");
        });
        Thread.sleep(500);
        onEdt(() -> {
            fitTestWindow(javax.swing.SwingUtilities.getWindowAncestor(provider[0].getComponent()));
            saveComponent(provider[0].getComponent(), out + ".memory-ready.png");
            provider[0].search();
            saveComponent(provider[0].getComponent(), out + ".memory-loading.png");
        });
        waitForMemorySearch(provider[0]);
        onEdt(() -> {
            if (provider[0].getResultsPanel().getMatchCount() != 0)
                throw new IllegalStateException("Empty search fixture unexpectedly matched");
            saveComponent(provider[0].getComponent(), out + ".memory-empty.png");
            provider[0].setSearchInput("55");
            provider[0].search();
        });
        waitForMemorySearch(provider[0]);
        onEdt(() -> {
            int matches = provider[0].getResultsPanel().getMatchCount();
            if (matches == 0) throw new IllegalStateException("Populated memory search has no matches");
            lines.add("memory,matches=" + matches);
            saveComponent(provider[0].getComponent(), out + ".memory-results.png");
            memoryFilter(provider[0].getResultsPanel()).setFilterText("missing_result_fixture");
        });
        Thread.sleep(1500);
        onEdt(() -> {
            if (provider[0].getResultsPanel().getMatchCount() != 0)
                throw new IllegalStateException("Memory result filter did not finish");
            saveComponent(provider[0].getComponent(), out + ".memory-filtered.png");
            lines.add("memory,ready/loading/empty/results/filtered captured");
            tool.showComponentProvider(provider[0], false);
        });
    }

    private void waitForMemorySearch(ghidra.features.base.memsearch.gui.MemorySearchProvider provider)
            throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            java.util.concurrent.atomic.AtomicBoolean busy = new java.util.concurrent.atomic.AtomicBoolean();
            onEdt(() -> busy.set(provider.isBusy()));
            if (!busy.get()) return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("Memory search did not complete");
    }

    private docking.widgets.table.GTableFilterPanel<?> memoryFilter(java.awt.Container parent) {
        if (parent instanceof docking.widgets.table.GTableFilterPanel<?> filter) return filter;
        for (java.awt.Component child : parent.getComponents()) {
            if (child instanceof java.awt.Container container) {
                var filter = memoryFilter(container);
                if (filter != null) return filter;
            }
        }
        return null;
    }
'@
$lastBrace = $source.LastIndexOf('}')
$source = $source.Substring(0, $lastBrace) + $review + "`n}"
$probe = Join-Path $sourceDir 'UiRefreshHarnessPlugin.java'
[IO.File]::WriteAllText($probe, $source)
& (Join-Path $PSScriptRoot 'capture-reference.ps1') -Tag $Tag -ProbeSource $probe `
    -Sequence 'Graphite Preview,(openprogram),(polish),(memory),Flat Dark Theme,(startup),Graphite Preview,(startup)'
