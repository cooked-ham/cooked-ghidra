/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.graph.viewer;

import docking.DockingChrome;
import edu.uci.ics.jung.algorithms.layout.StaticLayout;
import generic.test.AbstractGuiTest;
import ghidra.graph.graphs.AbstractTestVertex;
import ghidra.graph.graphs.LabelTestVertex;
import ghidra.graph.graphs.TestEdge;
import ghidra.graph.support.TestGraphLayout;
import ghidra.graph.support.TestVisualGraph;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;

import static org.junit.Assert.*;

public class GraphNavigationBarTest extends AbstractGuiTest {
    @Test
    public void testGraphZoomResetFitAndUnavailableState() {
        runSwing(() -> {
            Object previous = UIManager.get(DockingChrome.UI_KEY);
            UIManager.put(DockingChrome.UI_KEY, true);
            VisualGraphView<AbstractTestVertex, TestEdge, TestVisualGraph> view = new VisualGraphView<>();
            JFrame frame = new JFrame("Graph navigation regression");
            try {
                TestVisualGraph graph = new TestVisualGraph();
                graph.addVertex(new LabelTestVertex("Function entry"));
                graph.setLayout(new TestGraphLayout(new StaticLayout<>(graph)));
                // Function Graph installs its progress monitor through this extension point.
                view.setSouthComponent(new JPanel());
                frame.add(view.getViewComponent());
                frame.setSize(640, 480);
                frame.setVisible(true);
                view.setGraph(graph);
                GraphNavigationBar bar = findComponent(view.getViewComponent(), GraphNavigationBar.class);
                assertNotNull(bar);
                JButton reset = (JButton) bar.getComponent(1);
                JButton plus = (JButton) bar.getComponent(2);
                plus.doClick();
                double scale = GraphViewerUtils.getGraphScale(view.getPrimaryGraphViewer());
                assertEquals(Math.round(scale * 100) + "%", reset.getText());
                reset.doClick();
                assertEquals(1d, GraphViewerUtils.getGraphScale(view.getPrimaryGraphViewer()), .001);
                ((JButton) bar.getComponent(3)).doClick();
                assertTrue(GraphViewerUtils.getGraphScale(view.getPrimaryGraphViewer()) > 0);
                frame.setSize(220, 480);
                frame.validate();
                bar.doLayout();
                for (Component control : bar.getComponents()) {
                    assertTrue(control.getX() >= 0);
                    assertTrue(control.getY() + control.getHeight() <= bar.getHeight());
                }
                view.showErrorView("No function at this location");
                for (Component control : bar.getComponents()) {
                    assertFalse(control.isEnabled());
                }
                UIManager.put(DockingChrome.UI_KEY, false);
                bar.updateUI();
                assertFalse(bar.isVisible());
                UIManager.put(DockingChrome.UI_KEY, true);
                bar.updateUI();
                assertTrue(bar.isVisible());
            } finally {
                view.cleanup();
                frame.dispose();
                UIManager.put(DockingChrome.UI_KEY, previous);
            }
        });
    }
}
