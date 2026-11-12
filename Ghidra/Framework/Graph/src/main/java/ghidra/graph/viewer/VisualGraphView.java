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

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.*;
import javax.swing.event.ChangeListener;

import docking.DockingChrome;
import docking.widgets.ViewStatusPanel;
import docking.widgets.label.GDLabel;
import edu.uci.ics.jung.visualization.RenderContext;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.ScalingControl;
import generic.theme.Gui;
import ghidra.graph.VisualGraph;
import ghidra.graph.viewer.GraphComponent.SatellitePosition;
import ghidra.graph.viewer.event.mouse.VertexTooltipProvider;
import ghidra.graph.viewer.event.mouse.VisualGraphMousePlugin;
import ghidra.graph.viewer.layout.LayoutProvider;
import ghidra.graph.viewer.vertex.VertexClickListener;
import ghidra.graph.viewer.vertex.VertexFocusListener;

/**
 * A view object, where 'view' is used in the sense of the Model-View-Controller (MVC) pattern.
 * This class will contain all UI widgets need to display and interact with a graph.
 *
 * <p><b><u>Implementation Note:</u></b>
 * <ol>
 * 	<li>The graph of this component can be null, changing to non-null values over the
 * lifetime of this view.  This allows this view to be installed in a UI component, with the
 * contents changing as needed.
 * 	</li>
 *  <li>
 * 	When the graph is {@link #setGraph(VisualGraph) set}, the view portion of the class is
 * 	recreated.
 *  </li>
 *  <li>
 *  At any given point in time there may not be a {@link #graphComponent}.  This means that
 *  this class must maintain settings state that it will apply when the component is created.
 *  This state is atypical and makes this class a bit harder to understand.
 *  </li>
 * </ol>
 *
 * @param <V> the vertex type
 * @param <E> the edge type
 * @param <G> the graph type
 */
//@formatter:off
public class VisualGraphView<V extends VisualVertex,
							 E extends VisualEdge<V>,
							 G extends VisualGraph<V, E>> {
//@formatter:on

	private static final float ZOOM_OUT_AMOUNT = .9f;
	private static final float ZOOM_IN_AMOUNT = 1.1f;

	private static final String MESSAGE_FONT_ID = "font.visualgraph.view.label.message";

	private JPanel viewPanel;
	private JPanel viewContentPanel;
	private GraphNavigationBar navigationBar;
	private JPanel footerPanel;
	private final ChangeListener zoomListener = _ -> refreshGraphControls();

	/*
	 * This panel is what we give to clients when they wish to show an undocked satellite.
	 * As graph data is updated, we set and clear the contents of this panel as needed.  This
	 * allows the client to initialize the satellite window once, with updates controlled by
	 * this class.
	 *
	 * Note: this panel will be empty when docked and when the viewer is not yet built
	 */
	private JPanel undockedSatelliteContentPanel;

	// this can be null
	private G graph;
	protected GraphComponent<V, E, G> graphComponent;

	private VertexFocusListener<V> clientFocusListener;
	private final VertexFocusListener<V> internalFocusListener = v -> {
		if (clientFocusListener != null) {
			clientFocusListener.vertexFocused(v);
		}
	};

	private VertexClickListener<V, E> clientVertexClickListener;
	private final VertexClickListener<V, E> internalVertexClickListener = (v, info) ->
		clientVertexClickListener != null && clientVertexClickListener.vertexDoubleClicked(v, info);

	private final List<GraphSatelliteListener> satelliteListeners = new ArrayList<>();

	// this internal listener is the way we manage keeping our state in sync with the
	// graph component, as well as how we notify the client listener
	private final GraphSatelliteListener internalSatelliteListener = (docked, visible) -> {

		// keep our internal state in-sync
		showSatellite = visible;
		satelliteDocked = docked;
		satelliteListeners.forEach(l -> l.satelliteVisibilityChanged(docked, visible));
	};

	private boolean satelliteDocked = true;
	private boolean showSatellite = true;

	private boolean showPopups = true;
	private VertexTooltipProvider<V, E> tooltipProvider;

	private GraphPerspectiveInfo<V, E> graphInfo;
	private PathHighlightMode vertexHoverHighlightMode = PathHighlightMode.OFF;
	private PathHighlightMode vertexFocusHighlightMode = PathHighlightMode.OFF;

	protected LayoutProvider<V, E, G> layoutProvider;
	private final ScalingControl scaler = new VisualGraphScalingControl();
	private SatellitePosition satellitePosition = SatellitePosition.LOWER_RIGHT;

	public VisualGraphView() {
		build();
	}

	private void build() {
		viewPanel = new JPanel(new BorderLayout()) {
			@Override
			public Dimension getPreferredSize() {
				return new Dimension(1000, 1000);
			}

			@Override
			public Dimension getMinimumSize() {
				return getPreferredSize();
			}
		};

		viewContentPanel = new JPanel(new BorderLayout());
		viewPanel.add(viewContentPanel);
		navigationBar = new GraphNavigationBar(this::zoomOutGraph, this::resetGraphZoom,
			this::zoomInGraph, this::zoomToWindow);
		footerPanel = new JPanel(new BorderLayout());
		footerPanel.add(navigationBar, BorderLayout.SOUTH);
		viewPanel.add(footerPanel, BorderLayout.SOUTH);

		undockedSatelliteContentPanel = new JPanel(new BorderLayout());
	}

	public JComponent getViewComponent() {
		return viewPanel;
	}

	protected void setSouthComponent(JComponent component) {
		footerPanel.removeAll();
		footerPanel.add(navigationBar, BorderLayout.SOUTH);
		if (component != null) {
			footerPanel.add(component, BorderLayout.CENTER);
		}
		footerPanel.revalidate();
	}

	protected void removeSatellite() {
		undockedSatelliteContentPanel.removeAll();
		undockedSatelliteContentPanel.validate();
	}

	/**
	 * Called when the options used by this graph view have changed
	 */
	public void optionsChanged() {
		if (graphComponent != null) { // will be null after being closed
			graphComponent.optionsChanged();
		}
	}

	/**
	 * Sets the given layout provider, <b>but does not actually perform a layout</b>.
	 * @param newLayoutProvider the new provider
	 */
	public void setLayoutProvider(LayoutProvider<V, E, G> newLayoutProvider) {
		this.layoutProvider = newLayoutProvider;
	}

	public void setGraph(G graph) {
		stopAllAnimation();
		this.graph = graph;
		installGraphViewer();
	}

	public void addSatelliteListener(GraphSatelliteListener l) {
		if (l != null) {
			satelliteListeners.add(l);
		}
	}

	public void setVertexFocusListener(VertexFocusListener<V> l) {
		clientFocusListener = l;
	}

	/**
	 * Sets a listener that allows clients to be notified of vertex double-clicks.  Normal
	 * mouse processing is handled by the {@link VisualGraphMousePlugin} class.  This is a
	 * convenience method so that clients do not have to deal with the mouse plugin.
	 *
	 * @param l the listener
	 */
	public void setVertexClickListener(VertexClickListener<V, E> l) {
		clientVertexClickListener = l;
	}

	private void stopAllAnimation() {
		VisualGraphViewUpdater<V, E> updater = getViewUpdater();
		if (updater != null) {
			updater.stopAllAnimation();
		}
	}

	protected void installGraphViewer() {
		GraphComponent<V, E, G> newGraphComponent = new GraphComponent<>(graph);
		setGraphComponent(newGraphComponent);
	}

	protected void setGraphComponent(GraphComponent<V, E, G> newComponent) {

		disposeViewer();

		this.graphComponent = newComponent;

		//
		// Initialize
		//
		graphComponent.setPopupsVisible(showPopups);

		if (graphInfo != null) {
			graphComponent.setGraphPerspective(graphInfo);
			graphInfo = null;
		}

		graphComponent.setVertexHoverPathHighlightMode(vertexHoverHighlightMode);
		graphComponent.setVertexFocusPathHighlightMode(vertexFocusHighlightMode);

		GraphViewer<V, E> viewer = graphComponent.getPrimaryViewer();
		viewer.getRenderContext().getMultiLayerTransformer().addChangeListener(zoomListener);
		SatelliteGraphViewer<V, E> satellite = graphComponent.getSatelliteViewer();
		satellite.setBorder(DockingChrome.surfaceBorder(BorderFactory.createEmptyBorder()));
		satellite.getAccessibleContext().setAccessibleName("Graph overview");
		if (tooltipProvider != null) {
			viewer.setVertexTooltipProvider(tooltipProvider);
		}

		//
		// Wire to the UI
		//
		JComponent component = graphComponent.getComponent();
		Rectangle viewPanelBounds = viewContentPanel.getBounds();
		component.setBounds(viewPanelBounds);
		component.doLayout();
		viewContentPanel.removeAll();
		viewContentPanel.add(component);
		viewContentPanel.validate();

		undockedSatelliteContentPanel.removeAll();

		graphComponent.setVertexFocusListener(internalFocusListener);
		graphComponent.setVertexClickListener(internalVertexClickListener);

		graphComponent.setSatelliteLisetener(internalSatelliteListener);
		graphComponent.setInitialSatelliteState(showSatellite, satelliteDocked);

		if (!satelliteDocked) {
			// we must update the undocked panel's component since it may have an old reference
			undockedSatelliteContentPanel.add(graphComponent.getSatelliteContentComponent());
			undockedSatelliteContentPanel.validate();
		}
		graphComponent.setSatellitePosition(satellitePosition);
		refreshGraphControls();
	}

	private void refreshGraphControls() {
		if (navigationBar != null) {
			navigationBar.setGraphState(graphComponent != null && graph != null &&
				graph.getVertexCount() > 0, getZoom());
		}
	}

	private void resetGraphZoom() {
		VisualizationViewer<V, E> viewer = getPrimaryGraphViewer();
		if (viewer != null) {
			scaler.scale(viewer, (float) (1d / getZoom()), viewer.getCenter());
		}
	}

	/*
	 * Sets the contents of the view's content panel to be the given component
	 */
	protected void setContent(Component c) {
		viewContentPanel.removeAll();
		viewContentPanel.add(c);
		viewPanel.validate();
		repaint();
	}

	protected <T> T getWithBusyCursor(Supplier<T> s) {
		Cursor originalCursor = viewPanel.getCursor();
		try {
			viewPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

			return s.get();
		}
		finally {
			viewPanel.setCursor(originalCursor);
		}
	}

	public G getVisualGraph() {
		return graph;
	}

	/**
	 * Returns the primary viewer of the graph (as opposed to the satellite viewer).   The
	 * viewer returned is responsible for maintaining view information for a given graph.
	 *
	 * @return the primary viewer
	 */
	public GraphViewer<V, E> getPrimaryGraphViewer() {
		if (graphComponent == null) {
			return null;
		}
		return graphComponent.getPrimaryViewer();
	}

	public SatelliteGraphViewer<V, E> getSatelliteViewer() {
		return graphComponent.getSatelliteViewer();
	}

	/**
	 * Sets the perspective for this view
	 *
	 * @param newPerspective the new perspective
	 */
	public void setGraphPerspective(GraphPerspectiveInfo<V, E> newPerspective) {
		if (graphComponent != null) {
			graphComponent.setGraphPerspective(newPerspective);
			graphInfo = null;
		}
		else {
			graphInfo = newPerspective;
		}
	}

	public GraphPerspectiveInfo<V, E> generateGraphPerspective() {
		RenderContext<V, E> context = graphComponent.getRenderContext();
		return new GraphPerspectiveInfo<>(context, getZoom());
	}

	private double getZoom() {
		if (graphComponent == null) {
			if (graphInfo != null) {
				return graphInfo.getZoom();
			}
			return 1d;
		}
		return GraphViewerUtils.getGraphScale(getPrimaryGraphViewer());
	}

	/*
	 * Sets the entire view to be the given message instead of a graph.  This differs from
	 * setStatusMessage() in that the status is overlayed on the graph.
	 */
	public void showErrorView(String errorMessage) {

		stopAllAnimation();
		this.graph = null;

		removeSatellite();

		viewContentPanel.removeAll();
		viewContentPanel.paintImmediately(viewContentPanel.getBounds());
		JLabel messageLabel = new GDLabel(errorMessage);
		Gui.registerFont(messageLabel, MESSAGE_FONT_ID);
		messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
		messageLabel.setFocusable(true); // we have to have something focusable in our provider
		JPanel stockMessage = new JPanel(new BorderLayout());
		stockMessage.add(messageLabel, BorderLayout.NORTH);
		ViewStatusPanel status = new ViewStatusPanel(stockMessage, true);
		status.setStatus("Graph unavailable", errorMessage);
		viewContentPanel.add(status, BorderLayout.CENTER);
		viewContentPanel.validate();
		disposeViewer();
	}

	/**
	 * Sets a message to be painted on the viewer.  This is useful to show a text message to the
	 * user.  Passing null will clear the message.
	 *
	 * @param message the status message
	 */
	public void setStatusMessage(String message) {
		if (graphComponent != null) {
			graphComponent.setStatusMessage(message);
		}
	}

	public GraphComponent<V, E, G> getGraphComponent() {
		return graphComponent;
	}

	/**
	 * Returns whether the satellite intended to be visible.  If this component is built, then
	 * a result of true means that the satellite is showing.  If the component is not yet
	 * built, then a result of true means that the satellite will be made visible when the
	 * component is built.
	 *
	 * @return true if visible
	 */
	public boolean isSatelliteVisible() {
		return showSatellite;
	}

	public void setSatelliteVisible(boolean visible) {

		if (showSatellite == visible) {
			return; // nothing to do
		}

		this.showSatellite = visible;
		if (graphComponent != null) {
			graphComponent.setSatelliteVisible(visible);
		}
	}

	public void setSatelliteDocked(boolean docked) {

		if (satelliteDocked == docked) {
			return; // nothing to do
		}

		this.satelliteDocked = docked;
		if (graphComponent == null) {
			return;
		}

		graphComponent.setSatelliteDocked(docked);

		if (!docked) {
			undockedSatelliteContentPanel.removeAll();
			undockedSatelliteContentPanel.add(graphComponent.getSatelliteContentComponent());
			undockedSatelliteContentPanel.validate();
		}
	}

	public void setSatellitePosition(SatellitePosition position) {
		satellitePosition = position;
		if (graphComponent != null) {
			graphComponent.setSatellitePosition(position);
		}
	}

	public SatellitePosition getSatellitePosition() {
		return satellitePosition;
	}

	/**
	 * Returns whether the satellite intended to be docked.  If this component is built, then
	 * a result of true means that the satellite is docked.  If the component is not yet
	 * built, then a result of true means that the satellite will be made docked when the
	 * component is built.
	 *
	 * @return true if visible
	 */
	public boolean isSatelliteDocked() {
		return satelliteDocked;
	}

	public void setPopupsVisible(boolean visible) {
		this.showPopups = visible;
		if (graphComponent != null) {
			graphComponent.setPopupsVisible(visible);
		}
	}

	public boolean arePopupsVisible() {
		return showPopups;
	}

	@SuppressWarnings("unused") // Retained extension/test API.
	public boolean arePopupsEnabled() {
		return arePopupsVisible();
	}

	public JComponent getUndockedSatelliteComponent() {
		return undockedSatelliteContentPanel;
	}

	public boolean isSatelliteComponent(Component c) {
		if (graphComponent != null) {
			return graphComponent.isSatelliteComponent(c);
		}
		return false;
	}

	public void setVertexHoverPathHighlightMode(PathHighlightMode mode) {
		this.vertexHoverHighlightMode = mode;
		if (graphComponent != null) {
			graphComponent.setVertexHoverPathHighlightMode(mode);
		}
	}

	public void setVertexFocusPathHighlightMode(PathHighlightMode mode) {
		this.vertexFocusHighlightMode = mode;
		if (graphComponent != null) {
			graphComponent.setVertexFocusPathHighlightMode(mode);
		}
	}

	public PathHighlightMode getVertexFocusPathHighlightMode() {
		return vertexFocusHighlightMode;
	}

	public PathHighlightMode getVertexHoverPathHighlightMode() {
		return vertexHoverHighlightMode;
	}

	public void setTooltipProvider(VertexTooltipProvider<V, E> provider) {
		this.tooltipProvider = provider;
		if (graphComponent != null) {
			GraphViewer<V, E> viewer = graphComponent.getPrimaryViewer();
			viewer.setVertexTooltipProvider(provider);
		}
	}

	public void zoomOutGraph() {
		VisualizationViewer<V, E> primaryViewer = getPrimaryGraphViewer();
		scaler.scale(primaryViewer, ZOOM_OUT_AMOUNT, primaryViewer.getCenter());
	}

	public void zoomInGraph() {
		VisualizationViewer<V, E> primaryViewer = getPrimaryGraphViewer();
		scaler.scale(primaryViewer, ZOOM_IN_AMOUNT, primaryViewer.getCenter());
	}

	public void zoomToVertex(V v) {
		VisualGraphViewUpdater<V, E> updater = getViewUpdater();
		updater.zoomInCompletely(v);
	}

	public void zoomToWindow() {
		VisualGraphViewUpdater<V, E> updater = getViewUpdater();
		updater.fitGraphToViewerNow();
	}

	public VisualGraphViewUpdater<V, E> getViewUpdater() {
		if (graphComponent == null) {
			return null;
		}
		GraphViewer<V, E> viewer = getPrimaryGraphViewer();
		return viewer.getViewUpdater();
	}

	@SuppressWarnings("unused") // Retained extension/test API.
	public Point getVertexPointInViewSpace(V v) {
		return GraphViewerUtils.getPointInViewSpaceForVertex(getPrimaryGraphViewer(), v);
	}

	public Point translatePointFromVertexToViewSpace(V v, Point p) {
		return GraphViewerUtils
				.translatePointFromVertexRelativeSpaceToViewSpace(getPrimaryGraphViewer(), v, p);
	}

	public Rectangle translateRectangleFromVertexToViewSpace(V v, Rectangle r) {
		return GraphViewerUtils.translateRectangleFromVertexRelativeSpaceToViewSpace(
			getPrimaryGraphViewer(), v, r);
	}

	public MouseEvent translateMouseEventFromVertexToViewSpace(V v, MouseEvent e) {
		Point viewerPoint = translatePointFromVertexToViewSpace(v, e.getPoint());
		VisualizationViewer<V, E> newSource = getPrimaryGraphViewer();
		return new MouseEvent(newSource, e.getID(), e.getWhen(), e.getModifiersEx(),
			(int) viewerPoint.getX(), (int) viewerPoint.getY(), e.getClickCount(),
			e.isPopupTrigger(), e.getButton());
	}

	public boolean isScaledPastInteractionThreshold() {
		if (graphComponent == null) {
			// I think this is some sort of timing issue
			return true;// not sure what to return here...default to true?
		}

		return GraphViewerUtils.isScaledPastVertexInteractionThreshold(getPrimaryGraphViewer());
	}

	protected void maybeTwinkleVertex(V twinkleVertex, boolean doTwinkle) {
		if (!doTwinkle) {
			return;
		}
		graphComponent.twinkleVertex(twinkleVertex);
	}

	public void requestFocus() {
		viewPanel.requestFocus();
	}

	public void repaint() {
		viewPanel.repaint();
	}

	public V getFocusedVertex() {
		if (graph == null) {
			return null;
		}
		return graph.getFocusedVertex();
	}

	public Set<V> getSelectedVertices() {
		if (graph == null) {
			return Collections.emptySet();
		}
		return graph.getSelectedVertices();
	}

	public LayoutProvider<V, E, G> getLayoutProvider() {
		return layoutProvider;
	}

	/**
	 * Effectively clears this display.  This method is not called dispose, as that implies
	 * the end of an object's lifecycle.  This object can be re-used after this method is
	 * called.
	 */
	public void cleanup() {
		disposeViewer();

		// do not do this, the component gets re-used between graph loads--must keep the listener
		// clientSatelliteListener = Optional.empty();
	}

	protected void disposeViewer() {

		if (graphComponent != null) {
			graphComponent.getPrimaryViewer().getRenderContext().getMultiLayerTransformer()
				.removeChangeListener(zoomListener);
			graphComponent.dispose();
			graphComponent = null;
		}

		removeSatellite();
		refreshGraphControls();
	}

}
