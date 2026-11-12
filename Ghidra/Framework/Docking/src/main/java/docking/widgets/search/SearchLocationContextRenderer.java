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
package docking.widgets.search;

import java.awt.*;

import javax.swing.*;
import javax.swing.text.View;

import docking.widgets.table.GTableCellRenderingData;
import ghidra.docking.settings.Settings;
import ghidra.util.layout.AbstractLayoutManager;
import ghidra.util.table.column.AbstractGColumnRenderer;

/**
 * A renderer for {@link SearchLocationContext}.  This renderer handles the complexity of rendering
 * html text with clipping.
 */
public abstract class SearchLocationContextRenderer
		extends AbstractGColumnRenderer<SearchLocationContext> {

	private final JPanel htmlContainer = new JPanel(new HtmlTruncatingLayout());
	private final JLabel ellipsisLabel = new JLabel("...");

	public SearchLocationContextRenderer() {
		setHTMLRenderingEnabled(true);
	}

	protected abstract SearchLocationContext getContext(GTableCellRenderingData data);

	@Override
	public Component getTableCellRendererComponent(GTableCellRenderingData data) {

		SearchLocationContext context = getContext(data);

		// Note: we do not include the line number prefix on the text, based on the assumption that
		// clients of this renderer will have a separate line number column.
		return renderHtmlContext(data, context, false);
	}

	public Component renderPlainContext(GTableCellRenderingData data,
			SearchLocationContext context) {

		super.getTableCellRendererComponent(data);

		// Note: we do not include the line number prefix on the text, based on the assumption that
		// clients of this renderer will have a separate line number column.
		String text = SearchLocationContext.get(context).getPlainText(false);
		setText(text);
		return this;
	}

	public Component renderHtmlContext(GTableCellRenderingData data,
			SearchLocationContext context, boolean showLineNumbers) {

		// initialize
		super.getTableCellRendererComponent(data);

		/*
		 	We have html context.  Build a renderer that is a panel with 2 children: the html label
		 	(this renderer object) and an ellipsis label that will be visible as needed. 
		 */

		// Note: we do not include the line number prefix on the text, based on the assumption that
		// clients of this renderer will have a separate line number column.
		context = SearchLocationContext.get(context);
		String html = context.getBoldMatchingText(showLineNumbers);
		setText(html);
		// Keep focus and padding around the whole cell, including the truncation marker.
		htmlContainer.setBorder(getBorder());
		setBorder(BorderFactory.createEmptyBorder());

		ellipsisLabel.setOpaque(true);
		ellipsisLabel.setFont(getFont());
		ellipsisLabel.setForeground(getForeground());
		ellipsisLabel.setBackground(getBackground());

		htmlContainer.setBackground(getBackground());
		htmlContainer.setToolTipText(context.getPlainText(showLineNumbers));
		htmlContainer.getAccessibleContext().setAccessibleName(
			getAccessibleContext().getAccessibleName());
		htmlContainer.getAccessibleContext().setAccessibleDescription(
			context.getPlainText(showLineNumbers));
		htmlContainer.removeAll();
		htmlContainer.add(this);
		htmlContainer.add(ellipsisLabel);

		return htmlContainer;
	}

	@Override
	public String getFilterString(SearchLocationContext rowObject, Settings settings) {
		return rowObject.getPlainText();
	}

	/**
	 * A layout manager that positions 2 labels: a leading label with html and a trailing label
	 * with an ellipsis, which may not be visible.  JLabels rendering html will not show an
	 * ellipsis when clipped.   We use these 2 labels here to show when the leading html label's
	 * text is clipped.
	 */
	private static class HtmlTruncatingLayout extends AbstractLayoutManager {

		@Override
		public Dimension preferredLayoutSize(Container parent) {

			Dimension d = new Dimension();
			int n = parent.getComponentCount();
			for (int i = 0; i < n; i++) {
				Component c = parent.getComponent(i);
				Dimension cd = c.getPreferredSize();
				d.width += cd.width;
				d.height = Math.max(d.height, cd.height);
			}

			Insets insets = parent.getInsets();
			d.width += insets.left + insets.right;
			d.height += insets.top + insets.bottom;
			return d;
		}

		@Override
		public void layoutContainer(Container parent) {
			// Assumption: the leading component is an html view; the trailing component is a
			// label with an ellipsis

			JComponent c1 = (JComponent) parent.getComponent(0);
			Dimension d = parent.getSize();
			Insets insets = parent.getInsets();
			int width = Math.max(0, d.width - insets.left - insets.right);
			int height = Math.max(0, d.height - insets.top - insets.bottom);

			View v = (View) c1.getClientProperty("html");
			Insets i = c1.getInsets();
			int availableWidth = Math.max(0, width - (i.left + i.right));
			int htmlw = v == null ? c1.getPreferredSize().width :
				(int) Math.ceil(v.getPreferredSpan(View.X_AXIS));

			JLabel c2 = (JLabel) parent.getComponent(1);
			Dimension c2d = c2.getPreferredSize();
			boolean isClipped = htmlw > availableWidth && width > 0;
			if (isClipped) {
				int markerWidth = Math.min(width, c2d.width);
				availableWidth = Math.max(0, width - markerWidth);
				int c2x = insets.left + availableWidth;
				int c2y = insets.top;
				c2.setBounds(c2x, c2y, markerWidth, height);
			}

			c2.setVisible(isClipped);

			int c1x = insets.left;
			int c1y = insets.top;
			c1.setBounds(c1x, c1y, availableWidth, height);
		}
	}
}
