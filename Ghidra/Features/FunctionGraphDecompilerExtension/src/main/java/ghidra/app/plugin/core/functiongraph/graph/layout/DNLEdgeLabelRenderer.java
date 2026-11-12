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
package ghidra.app.plugin.core.functiongraph.graph.layout;

import com.google.common.base.Predicate;
import docking.DockingChrome;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.util.Context;
import edu.uci.ics.jung.graph.util.Pair;
import edu.uci.ics.jung.visualization.Layer;
import edu.uci.ics.jung.visualization.MultiLayerTransformer;
import edu.uci.ics.jung.visualization.RenderContext;
import edu.uci.ics.jung.visualization.renderers.EdgeLabelRenderer;
import edu.uci.ics.jung.visualization.transform.shape.GraphicsDecorator;
import ghidra.app.plugin.core.functiongraph.graph.FGEdge;
import ghidra.app.plugin.core.functiongraph.graph.vertex.FGVertex;
import ghidra.graph.viewer.renderer.EdgeLabelLayout;
import ghidra.graph.viewer.vertex.VertexShapes;
import ghidra.graph.viewer.vertex.VisualGraphVertexShapeTransformer;
import ghidra.program.model.symbol.FlowType;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * An edge label renderer used with the {@link DecompilerNestedLayout}
 *
 * @param <V> the vertex type
 * @param <E> the edge type
 */
class DNLEdgeLabelRenderer<V extends FGVertex, E extends FGEdge>
        implements EdgeLabelLayout<V, E> {

	private static final int DEFAULT_EDGE_OFFSET = 20;

    private final VisualGraphVertexShapeTransformer<V> vertexShapeTransformer =
		new VisualGraphVertexShapeTransformer<>();

    private final double edgeOffset;
    private final List<Rectangle2D> occupiedBounds = new ArrayList<>();
    private boolean vertexBoundsCached;

	DNLEdgeLabelRenderer(double condenseFactor) {
		this.edgeOffset = DEFAULT_EDGE_OFFSET * (1 - condenseFactor);
	}

    /**
     * Keeps the layout's label anchor when it is clear. Otherwise, move the label the shortest
     * distance along one axis to clear every card and prior label. Coordinates share the vertex
     * renderer's layout transform; the graphics context applies the remaining view zoom to both.
     */
    static Point2D avoidVertexOverlap(Point2D preferred, Dimension size,
                                      List<Rectangle2D> vertices) {
        Rectangle2D label = new Rectangle2D.Double(preferred.getX(), preferred.getY(),
                size.width, size.height);
        if (vertices.stream().noneMatch(bounds -> bounds.intersects(label))) {
            return preferred;
        }

        Point2D best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (Rectangle2D vertex : vertices) {
            Point2D[] candidates = {
                    new Point2D.Double(vertex.getMinX() - size.width, preferred.getY()),
                    new Point2D.Double(vertex.getMaxX(), preferred.getY()),
                    new Point2D.Double(preferred.getX(), vertex.getMinY() - size.height),
                    new Point2D.Double(preferred.getX(), vertex.getMaxY())
            };
            for (Point2D candidate : candidates) {
                double distance = preferred.distanceSq(candidate);
                if (distance >= bestDistance) {
                    continue;
                }
                label.setRect(candidate.getX(), candidate.getY(), size.width, size.height);
                if (vertices.stream().noneMatch(bounds -> bounds.intersects(label))) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        // At least one extreme boundary is clear for finite card bounds.
        return best == null ? preferred : best;
	}

    @Override
    public void reset() {
        occupiedBounds.clear();
        vertexBoundsCached = false;
    }

	@Override
	public void labelEdge(RenderContext<V, E> rc, Layout<V, E> layout, E e, String text) {

		Graph<V, E> jungGraph = layout.getGraph();
		if (!rc.getEdgeIncludePredicate().apply(Context.getInstance(jungGraph, e))) {
			return;
		}

		Pair<V> endpoints = jungGraph.getEndpoints(e);
		V startv = endpoints.getFirst();
		V endv = endpoints.getSecond();

		Predicate<Context<Graph<V, E>, V>> includeVertex = rc.getVertexIncludePredicate();
		if (!includeVertex.apply(Context.getInstance(jungGraph, startv)) ||
			!includeVertex.apply(Context.getInstance(jungGraph, endv))) {
			return;
		}

		Point2D start = layout.apply(startv);
		MultiLayerTransformer multiLayerTransformer = rc.getMultiLayerTransformer();
		start = multiLayerTransformer.transform(Layer.LAYOUT, start);

		Shape vertexShape = vertexShapeTransformer.apply(startv);
		Rectangle vertexBounds = vertexShape.getBounds();
		int xDisplacement = rc.getLabelOffset();

		Point2D labelPointOffset = new Point2D.Double();

		// note: location is centered
		double cx = start.getX();
		double cy = start.getY();

		EdgeLabelRenderer labelRenderer = rc.getEdgeLabelRenderer();
		Font font = rc.getEdgeFontTransformer().apply(e);
		boolean isSelected = rc.getPickedEdgeState().isPicked(e);
		Component component = labelRenderer.getEdgeLabelRendererComponent(rc.getScreenDevice(),
			text, font, isSelected, e);
		int labelWidth = component.getPreferredSize().width;

		List<Point2D> articulationPoints = e.getArticulationPoints();
		if (articulationPoints.isEmpty()) {
			double vertexBottom = start.getY() + (vertexBounds.height >> 1); // location is centered
			double textY = (int) (vertexBottom + edgeOffset); // below the vertex; above the bend
			double textX = (int) (start.getX() + xDisplacement); // right of the edge
			labelPointOffset.setLocation(textX, textY);
		}
		else if (articulationPoints.size() == 1) {
			// articulation must have been removed
			return;
		}
		else {

            Point2D bend1 = articulationPoints.getFirst();
			bend1 = multiLayerTransformer.transform(Layer.LAYOUT, bend1);

			double vertexSide = cx + (vertexBounds.width >> 1);
			double vertexBottom = cy + (vertexBounds.height >> 1);

			double bx1 = bend1.getX();

			FlowType flow = e.getFlowType();
			boolean isRight = flow.isFallthrough() || flow.isUnConditional();

			if (articulationPoints.size() == 2) {

				double textX = (int) (vertexSide + edgeOffset); // right of the vertex
				double textY = (int) (cy + edgeOffset); // above the edge
				labelPointOffset.setLocation(textX, textY);
			}
			else { // 3 or 4 articulations

				double textY = (int) (vertexBottom + edgeOffset); // below the vertex; above the bend
				double textX = (int) (bx1 + xDisplacement); // right of the edge
				if (!isRight) {
					textX = bx1 - xDisplacement - labelWidth;
				}

				labelPointOffset.setLocation(textX, textY);
			}
		}

		Dimension d = component.getPreferredSize();
        if (DockingChrome.isModern()) {
            cacheVertexBounds(rc, layout);
            labelPointOffset = avoidVertexOverlap(labelPointOffset, d, occupiedBounds);
            occupiedBounds.add(new Rectangle2D.Double(labelPointOffset.getX() - 2,
                    labelPointOffset.getY() - 2, d.width + 4, d.height + 4));
        }

		GraphicsDecorator g = rc.getGraphicsContext();
		AffineTransform old = g.getTransform();
		AffineTransform xform = new AffineTransform(old);
		xform.translate(labelPointOffset.getX(), labelPointOffset.getY());

		g.setTransform(xform);
        try {
            g.draw(component, rc.getRendererPane(), 0, 0, d.width, d.height, true);
        } finally {
            g.setTransform(old);
        }

		// debug
        //labelArticulations(g, rc, e);
    }

    private void cacheVertexBounds(RenderContext<V, E> rc, Layout<V, E> layout) {
        if (!vertexBoundsCached) {
            Graph<V, E> jungGraph = layout.getGraph();
            MultiLayerTransformer multiLayerTransformer = rc.getMultiLayerTransformer();
            for (V vertex : jungGraph.getVertices()) {
                if (!rc.getVertexIncludePredicate().apply(Context.getInstance(jungGraph, vertex))) {
                    continue;
                }
                Shape shape = VertexShapes.getVertexShape(rc.getVertexShapeTransformer(), vertex, true);
                Point2D location = multiLayerTransformer.transform(Layer.LAYOUT, layout.apply(vertex));
                Rectangle2D bounds = AffineTransform
                        .getTranslateInstance((float) location.getX(), (float) location.getY())
                        .createTransformedShape(shape).getBounds2D();
                // Leave a little space around card borders and selection outlines.
                occupiedBounds.add(new Rectangle2D.Double(bounds.getX() - 4,
                        bounds.getY() - 4, bounds.getWidth() + 8, bounds.getHeight() + 8));
            }
            vertexBoundsCached = true;
        }
    }

	@SuppressWarnings("unused") // used during debug
    private void labelArticulations(GraphicsDecorator g, RenderContext<V, E> rc, E e) {

		int offset = 5;
		int counter = 1;
		List<Point2D> points = e.getArticulationPoints();
		for (Point2D p : points) {

			MultiLayerTransformer multiLayerTransformer = rc.getMultiLayerTransformer();
			p = multiLayerTransformer.transform(Layer.LAYOUT, p);

			EdgeLabelRenderer labelRenderer = rc.getEdgeLabelRenderer();
			Font font = rc.getEdgeFontTransformer().apply(e);
			boolean isSelected = rc.getPickedEdgeState().isPicked(e);
            Component component = labelRenderer.getEdgeLabelRendererComponent(rc.getScreenDevice(),
				"p" + counter++, font, isSelected, e);

			Dimension d = component.getPreferredSize();
			AffineTransform old = g.getTransform();
			AffineTransform xform = new AffineTransform(old);
			xform.translate(p.getX() + offset, p.getY());
			g.setTransform(xform);
			g.draw(component, rc.getRendererPane(), 0, 0, d.width, d.height, true);
			g.setTransform(old);
		}
	}
}
