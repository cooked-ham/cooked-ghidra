// Disposable capture harness; compiled into the probe extension, never product jars.
package uirefresh;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import generic.theme.ThemeManager;
import ghidra.app.plugin.core.functiongraph.FGProvider;
import ghidra.app.plugin.core.functiongraph.graph.vertex.GroupedFunctionGraphVertex;
import ghidra.app.plugin.core.functiongraph.mvc.FGView;
import ghidra.app.services.GoToService;
import ghidra.app.services.ProgramManager;
import ghidra.framework.plugintool.PluginTool;

public final class GraphReview {
    private GraphReview() {
    }

    public static void run(PluginTool tool, String out, List<String> lines) throws Exception {
        FGProvider[] provider = new FGProvider[1];
        onEdt(() -> {
            provider[0] = (FGProvider) tool.getComponentProvider("Function Graph");
            if (provider[0] == null) throw new AssertionError("Function Graph provider missing");
            tool.showComponentProvider(provider[0], true);
            var program = tool.getService(ProgramManager.class).getCurrentProgram();
            var functions = program.getFunctionManager().getFunctions(true);
            if (!functions.hasNext()) throw new AssertionError("Fixture has no functions");
            tool.getService(GoToService.class).goTo(functions.next().getEntryPoint());
        });
        FGView view = (FGView) provider[0].getView();
        waitForGraph(view, 4);
        onEdt(() -> {
            view.getViewUpdater().setGraphScale(1);
            shot(provider[0], out + ".graph-labels.png");
            var controller = view.getController();
            var vertices = new ArrayList<>(controller.getFunctionGraphData().getFunctionGraph().getVertices());
            vertices.sort(Comparator.comparing(v -> v.getVertexAddress()));
            var branches = new LinkedHashSet<>(vertices.subList(1, 3));
            Point2D first = vertices.get(1).getLocation();
            Point2D second = vertices.get(2).getLocation();
            view.getViewUpdater().groupVertices(controller, "If / Else branches", branches,
                new Point2D.Double((first.getX() + second.getX()) / 2,
                    (first.getY() + second.getY()) / 2));
        });
        waitForGraph(view, 3);
        onEdt(() -> {
            var graph = view.getController().getFunctionGraphData().getFunctionGraph();
            var group = graph.getVertices().stream()
                .filter(v -> v instanceof GroupedFunctionGraphVertex)
                .map(v -> (GroupedFunctionGraphVertex) v).findFirst().orElseThrow();
            if (group.getVertices().size() != 2) throw new AssertionError("Expected two grouped branches");
            view.getPrimaryGraphViewer().getPickedVertexState().clear();
            view.getViewUpdater().setGraphScale(1);
            shot(provider[0], out + ".graph-grouped.png");
            view.getPrimaryGraphViewer().getPickedVertexState().pick(group, true);
            shot(provider[0], out + ".graph-grouped-selected.png");
        });
        switchTheme("Flat Dark Theme");
        onEdt(() -> shot(provider[0], out + ".graph-grouped-stock.png"));
        switchTheme("Graphite Preview");
        onEdt(() -> {
            view.getViewUpdater().setGraphScale(.08);
            shot(provider[0], out + ".graph-grouped-overview.png");
            view.getViewUpdater().setGraphScale(1);
            view.getController().ungroupAllVertices();
        });
        waitForGraph(view, 4);
        onEdt(() -> {
            var graph = view.getController().getFunctionGraphData().getFunctionGraph();
            if (graph.getVertices().stream().anyMatch(v -> v instanceof GroupedFunctionGraphVertex))
                throw new AssertionError("Ungrouping left grouped vertices");
            shot(provider[0], out + ".graph-ungrouped.png");
            lines.add("graph,4 vertices / 2 grouped branches / 3 vertices / ungrouped to 4; modern-stock-modern");
        });
    }

    private static void waitForGraph(FGView view, int vertices) throws Exception {
        for (int attempt = 0; attempt < 150; attempt++) {
            AtomicBoolean ready = new AtomicBoolean();
            onEdt(() -> {
                var data = view.getController().getFunctionGraphData();
                ready.set(data.hasResults() && data.getFunctionGraph().getVertexCount() == vertices &&
                    !view.getViewUpdater().isBusy());
            });
            if (ready.get()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Graph did not settle with " + vertices + " vertices");
    }

    private static void switchTheme(String name) throws Exception {
        onEdt(() -> {
            ThemeManager manager = ThemeManager.getInstance();
            manager.setTheme(manager.getAllThemes().stream()
                .filter(theme -> theme.getName().equals(name)).findFirst().orElseThrow());
        });
        Thread.sleep(1200);
    }

    private static void shot(FGProvider provider, String path) {
        Component component = SwingUtilities.getWindowAncestor(provider.getComponent());
        if (!(component instanceof Window)) throw new AssertionError("Graph window missing");
        component.validate();
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(),
            BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        }
        finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(image, "png", new File(path));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void onEdt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }
}
