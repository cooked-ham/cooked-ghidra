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
package ghidra.graph.viewer.renderer;

import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.algorithms.layout.StaticLayout;
import edu.uci.ics.jung.visualization.BasicVisualizationServer;
import edu.uci.ics.jung.visualization.RenderContext;
import ghidra.graph.graphs.AbstractTestVertex;
import ghidra.graph.graphs.TestEdge;
import ghidra.graph.graphs.TestVertex;
import ghidra.graph.support.TestVisualGraph;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class VisualGraphRendererTest {

    @Test
    public void testLabelPlacementsResetForEveryRepaint() {
        List<String> events = new ArrayList<>();
        EdgeLabelLayout<AbstractTestVertex, TestEdge> labels = new EdgeLabelLayout<>() {
            @Override
            public void reset() {
                events.add("reset");
            }

            @Override
            public void labelEdge(RenderContext<AbstractTestVertex, TestEdge> rc,
                                  Layout<AbstractTestVertex, TestEdge> layout, TestEdge edge, String label) {
                events.add(label);
            }
        };
        VisualGraphRenderer<AbstractTestVertex, TestEdge> renderer = new VisualGraphRenderer<>(labels) {
            @Override
            public void renderEdge(RenderContext<AbstractTestVertex, TestEdge> rc,
                                   Layout<AbstractTestVertex, TestEdge> layout, TestEdge edge) {
                // Only label placement is relevant to this render pass.
            }

            @Override
            public void renderVertex(RenderContext<AbstractTestVertex, TestEdge> rc,
                                     Layout<AbstractTestVertex, TestEdge> layout, AbstractTestVertex vertex) {
                // No vertex painting needed.
            }

            @Override
            public void renderVertexLabel(RenderContext<AbstractTestVertex, TestEdge> rc,
                                          Layout<AbstractTestVertex, TestEdge> layout, AbstractTestVertex vertex) {
                // No vertex label painting needed.
            }
        };
        TestVisualGraph graph = new TestVisualGraph();
        graph.addEdge(new TestEdge(new TestVertex("entry"), new TestVertex("exit")));
        Layout<AbstractTestVertex, TestEdge> layout = new StaticLayout<>(graph);
        RenderContext<AbstractTestVertex, TestEdge> context =
                new BasicVisualizationServer<>(layout).getRenderContext();
        context.setEdgeLabelTransformer(_ -> "flow");
        renderer.render(context, layout);
        renderer.render(context, layout);
        assertEquals(List.of("reset", "flow", "reset", "flow"), events);
    }
}
