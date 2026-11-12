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
package ghidra.graph.viewer.vertex;

import com.google.common.base.Function;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.visualization.Layer;
import edu.uci.ics.jung.visualization.RenderContext;
import ghidra.graph.viewer.VisualEdge;
import ghidra.graph.viewer.VisualVertex;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * Shape helpers shared by the vertex and edge renderers, which both need a vertex's shape in
 * view space.
 */
public final class VertexShapes {

    private VertexShapes() {
        // utility class
    }

    /**
     * Returns the raw (layout space) shape for the given vertex.  If the shaper is a
     * {@link VisualGraphVertexShapeTransformer}, then the full or compact shape is used, as
     * requested; otherwise the shaper's single shape is used.
     *
     * @param shaper the shape transformer
     * @param vertex the vertex
     * @param full   true to get the full shape; false to get the compact shape
     * @param <V>    the vertex type
     * @return the shape
     */
    @SuppressWarnings("unchecked")
    public static <V extends VisualVertex> Shape getVertexShape(Function<? super V, Shape> shaper,
                                                                V vertex, boolean full) {

        if (shaper instanceof VisualGraphVertexShapeTransformer) {
            // use the viewable shape here, as it is visually pleasing
            VisualGraphVertexShapeTransformer<V> vgShaper =
                    (VisualGraphVertexShapeTransformer<V>) shaper;
            return full ? vgShaper.transformToFullShape(vertex)
                    : vgShaper.transformToCompactShape(vertex);
        }
        return shaper.apply(vertex);
    }

    /**
     * Takes the given shape and translates its coordinates to the view space
     *
     * @param rc     the render context
     * @param layout the model space layout
     * @param vertex the vertex
     * @param shape  the shape to translate
     * @param <V>    the vertex type
     * @param <E>    the edge type
     * @return the new shape
     */
    public static <V extends VisualVertex, E extends VisualEdge<V>> Shape transformFromLayoutToView(
            RenderContext<V, E> rc, Layout<V, E> layout, V vertex, Shape shape) {

        Point2D p = layout.apply(vertex);
        p = rc.getMultiLayerTransformer().transform(Layer.LAYOUT, p);

        // create a transform that translates to the location of the vertex to be rendered
        AffineTransform xform = AffineTransform.getTranslateInstance((float) p.getX(), (float) p.getY());
        return xform.createTransformedShape(shape);
    }
}
