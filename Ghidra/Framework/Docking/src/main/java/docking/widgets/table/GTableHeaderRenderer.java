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
package docking.widgets.table;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.*;

import docking.DockingChrome;
import generic.theme.GIcon;
import generic.theme.GThemeDefaults.Colors;
import generic.theme.Gui;
import resources.*;
import resources.icons.EmptyIcon;
import resources.icons.TranslateIcon;

public class GTableHeaderRenderer extends DefaultTableCellRenderer {

	private static final int PADDING_FOR_COLUMN_NUMBER = 8;
	private static final Icon UP_ICON =
		ResourceManager.getScaledIcon(Icons.SORT_ASCENDING_ICON, 14, 14);
	private static final Icon DOWN_ICON =
		ResourceManager.getScaledIcon(Icons.SORT_DESCENDING_ICON, 14, 14);
	private static final int DEFAULT_MIN_HEIGHT = UP_ICON.getIconHeight();

	private static final Icon EMPTY_ICON = new EmptyIcon(0, 0);
	private static final Icon FILTER_ICON =
		ResourceManager.getScaledIcon(new GIcon("icon.widget.filterpanel.filter.off"), 12, 12);

	private static final Icon PENDING_ICON = new GIcon("icon.widget.table.header.pending");

	private Icon primaryIcon = EMPTY_ICON;
	private Icon helpIcon = EMPTY_ICON;
	private double sortEmphasis = -1;
	private Image sortImage; // cached image

	private Component rendererComponent;

	/**
	 * Sets the an emphasis value for this column that is used to slightly enlarge and call out the
	 * sort for the column.  
	 * @param sortEmphasis the emphasis value
	 */
	public void setSortEmphasis(double sortEmphasis) {
		this.sortEmphasis = sortEmphasis;
		if (sortEmphasis < 0) {
			sortImage = null;
		}
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
			boolean hasFocus, int row, int column) {

		if (table == null) {
			return this; // not sure when this can happen, but Java does this internally
		}

		JTableHeader header = table.getTableHeader();
		TableCellRenderer delegate = header.getDefaultRenderer();

		rendererComponent =
			delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

		int modelIndex = table.convertColumnIndexToModel(column);
		TableModel model = table.getModel();
		VariableColumnTableModel variableModel = VariableColumnTableModel.from(model);
		if (variableModel != null) {
			String text = variableModel.getColumnDisplayName(modelIndex);
			if (rendererComponent instanceof JLabel) {
				((JLabel) rendererComponent).setText(text);
			}
		}

		primaryIcon = getIcon(model, modelIndex);
		helpIcon = getHelpIcon(table, column);
		if (DockingChrome.isModern() && rendererComponent instanceof JLabel label) {
			label.setHorizontalAlignment(SwingConstants.LEADING);
		}

		// The prevents accessibility from reading the table header text when the header is clipped
		// in between the row and column numbers, which looks confusing and is inconsistent.
		// We override getToolTipText() on the header which allows us to customize the tool tip
		// for the headers as needed. Setting this to the empty string overrides the client property
		// for tool tips, which is what the accessibility api uses.  The GUI tool tip is retrieved
		// from the getToolTipText() method.
		putClientProperty(TOOL_TIP_TEXT_KEY, "");

		return this;
	}


	@Override
	public void setBounds(int x, int y, int w, int h) {
		super.setBounds(x, y, w, h);
		rendererComponent.setBounds(x, y, w, h);
	}

	@Override
	public void validate() {
		super.validate();

		if (rendererComponent != null) {
			rendererComponent.validate();
		}
	}

	@Override
	public void paint(Graphics g) {

		updateClipping();

		// Note: we should not have to set the colors here.  That is usually done by the renderer
		// when getTableCellRendererComponent() is called.  Some Lafs, like the FlatLaf will change
		// colors when painting, after the renderer component has been configured.  To support that,
		// we must update the colors here as well.  
		rendererComponent.setBackground(getBackground());
		rendererComponent.setForeground(getForeground());

		// Our parent renderer pane will add us as a child.  Some Lafs, like Windows, need to be a
		// child of the renderer pane to work correctly.
		Container parent = getParent();
		Container rendererParent = rendererComponent.getParent();
		if (rendererParent != parent) {
			parent.add(rendererComponent);
		}

		if (UIManager.getBoolean("Docking.graphiteDepth") && rendererComponent instanceof JComponent jc) {
			docking.DockingChrome.paintSurface(g, getWidth(), getHeight(), getBackground(), getBackground());
			boolean opaque = jc.isOpaque();
			try {
				jc.setOpaque(false);
				jc.paint(g);
			}
			finally { jc.setOpaque(opaque); }
		}
		else rendererComponent.paint(g);

		// paint our items after the delegate call so that we paint on top
		paintChildren(g);
	}

	private void updateClipping() {
		if (!(rendererComponent instanceof JLabel label)) {
			return;
		}

		String text = label.getText();
		String clippedText = checkForClipping(label, text);
		if (text != null && !text.equals(clippedText)) {
			label.setText(clippedText);

			// set the tooltips on us, the wrapper renderer, since Java will ask us for the tooltip
			setToolTipText(text);
		}
	}

	private String checkForClipping(JLabel label, String text) {
		if (text == null) {
			return null;
		}
		if (DockingChrome.isModern()) {
			Insets insets = label.getInsets();
			int iconStart = getHelpIconLocation().x - primaryIcon.getIconWidth() - 6;
			int width = Math.max(0, iconStart - insets.left - insets.right);
			return SwingUtilities.layoutCompoundLabel(label, label.getFontMetrics(label.getFont()),
				text, label.getIcon(), label.getVerticalAlignment(), label.getHorizontalAlignment(),
				label.getVerticalTextPosition(), label.getHorizontalTextPosition(),
				new Rectangle(0, 0, width, getHeight()), new Rectangle(), new Rectangle(),
				label.getIconTextGap());
		}

		Point helpPoint = getHelpIconLocation();
		int padding = 5;

		int iconWidth = primaryIcon.getIconWidth();
		if (iconWidth == 0) {
			// no icon; no padding needed
			padding = 0;
		}

		int iconStartX = helpPoint.x - iconWidth - padding;

		FontMetrics metrics = label.getFontMetrics(label.getFont());
		Rectangle bounds = label.getBounds();

		// the icon x is calculated from the right; some padding so the text does not hit the icon
		int availableTextWidth = iconStartX - padding;

		//@formatter:off
		Rectangle viewBounds = new Rectangle(0, 0, availableTextWidth, bounds.height);
		Rectangle iconResult = new Rectangle();
		Rectangle textResult = new Rectangle();
		return SwingUtilities.layoutCompoundLabel(label, metrics, text, primaryIcon,
			label.getVerticalAlignment(), label.getHorizontalAlignment(),
			label.getVerticalTextPosition(), label.getHorizontalTextPosition(),
			viewBounds,
			iconResult,
			textResult,
			label.getIconTextGap());
		//@formatter:on
	}

	// creates an image from the given icon; used scaling the image
	private Image createImage(Icon icon) {

		if (sortImage != null) {
			return sortImage;
		}

		int w = icon.getIconWidth();
		int h = icon.getIconHeight();

		BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = (Graphics2D) bi.getGraphics();
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		icon.paintIcon(this, g2d, 0, 0);

		g2d.dispose();

		sortImage = bi;
		return bi;
	}

	// We have overridden paint children to add the sort column icon and the help icon, depending 
	// on if this column is sorted and/or hovered.
	@Override
	protected void paintChildren(Graphics g) {

		// The help icon paints at the end of the cell; place the main icon to the left of that
		Point helpPoint = getHelpIconLocation();
		int offset = DockingChrome.isModern() ? 6 : 4;
		int x = helpPoint.x - primaryIcon.getIconWidth() - offset;
		int y = getIconStartY(primaryIcon.getIconHeight());

		if (sortEmphasis <= 1.0) {
			// default icon painting; no emphasis
			primaryIcon.paintIcon(this, g, x, y);
			helpIcon.paintIcon(this, g, helpPoint.x, helpPoint.y);
			return;
		}

		//
		// This column has been emphasized.  We use the notion of emphasis to remind users that they
		// are using a  multi-column sort.  When users are toggling the sort direction of a given
		// column, it is easy to forget that other columns are also sorted, especially when those
		// columns are in the users peripheral vision.  TableUtils uses an animator to control the
		// emphasis for all sorted columns, other than the clicked column.  We hope that this 
		// emphasis creates enough movement in the users peripheral vision to serve as a gentle 
		// reminder that the table sort consists of more than just the clicked column.
		//
		// There is no emphasis applied to columns when only a single column is sorted.  See the 
		// paint method for details on how the emphasis is used.
		//

		// create an image and use the graphics for painting the scaled/emphasized version 
		Image image = createImage(primaryIcon);
		paintImage((Graphics2D) g, image, x, y);
	}

	// x,y are relative to the end of the component using 0,0 
	private void paintImage(Graphics2D g2d, Image image, int x, int y) {

		//
		// Currently, the sort emphasis is used to scale the sort icon.   This code will scale the
		// icon, up to a maximum.  The emphasis set on this column will grow and then shrink as the
		// values are updated by an animator.   The icon image being painted here will start at the
		// current icon location, grow to the max emphasis, and then shrink back to its original 
		// size.
		// 
		double max = 1.3D;
		double scale = sortEmphasis;
		scale = Math.min(max, scale);

		AffineTransform originalTransform = g2d.getTransform();
		try {

			AffineTransform cloned = (AffineTransform) originalTransform.clone();
			cloned.scale(scale, scale);

			g2d.setTransform(cloned);

			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

			// center the growing icon over the normal icon using the size delta and dividing by 2
			int iw = image.getWidth(null);
			int ih = image.getHeight(null);
			double dw = (iw * scale) - iw;
			double dh = (ih * scale) - ih;
			double halfDw = dw / 2;
			double halfDh = dh / 2;

			// as the image grows, we must move x,y back so it stays centered
			double sx = x / scale;
			double sy = y / scale;
			int fx = (int) Math.round(sx - halfDw);
			int fy = (int) Math.round(sy - halfDh);

			// to make the icon change more noticeable to the user, paint a small highlight behind
			// the icon being emphasized
			paintBgHighlight(g2d, scale, max, fx, fy, iw, ih);

			g2d.drawImage(image, fx, fy, null);
		}
		finally {
			g2d.setTransform(originalTransform);
		}
	}

	/**
	 * Paints a background highlight under the icon that will get painted.
	 * @param g2d the graphics
	 * @param scale the current scale, up to max 
	 * @param max the max emphasis size
	 * @param ix the icon x
	 * @param iy the icon y
	 * @param iw the icon width
	 * @param ih the icon height
	 */
	private void paintBgHighlight(Graphics2D g2d, double scale, double max, double ix, double iy,
			double iw, double ih) {

		// range from 0 to max (e.g., 0 to .3); highlight will fade in from full alpha
		double range = max - 1;
		double current = scale - 1;

		Composite originalComposite = g2d.getComposite();
		try {
			AlphaComposite alphaComposite = AlphaComposite.getInstance(
				AlphaComposite.SrcOver.getRule(), (float) current);

			g2d.setComposite(alphaComposite);
			g2d.setColor(Colors.FOREGROUND);

			// highlight size is a range from 0 to max, where max is currently .3; grow the 
			// highlight shape as the animation progresses 
			double percent = current / range;
			double bgpadding = 1;
			double bgw = (iw + bgpadding) * percent;
			double bgh = (ih + bgpadding) * percent;

			// center using the delta between the icon size and the current highlight size
			double halfpadding = (bgpadding / 2);
			double bgwd = (iw + bgpadding) - bgw;
			double bghd = (ih + bgpadding) - bgh;
			double halfw = bgwd / 2;
			double halfh = bghd / 2;
			double bgx = (ix - halfpadding) + halfw;
			double bgy = (iy - halfpadding) + halfh;

			g2d.fillRoundRect((int) bgx, (int) bgy, (int) bgw, (int) bgh, 6, 6);
		}
		finally {
			g2d.setComposite(originalComposite);
		}
	}

	private Point getHelpIconLocation() {

		int right = getWidth();
		int offset = 2;
		int helpIconExtent = GTableHeader.HELP_ICON_HEIGHT;

		// we want the icon on the right-hand size of the header, at the top
		int x = right - helpIconExtent - offset;
		return new Point(x, DockingChrome.isModern() ? getIconStartY(helpIconExtent) : offset);
	}

	@Override
	// overridden to enforce a minimum height for the icon we use
	public Dimension getPreferredSize() {
		Dimension preferredSize = super.getPreferredSize();
		if (rendererComponent != null) {
			Dimension size = rendererComponent.getPreferredSize();
			if (DockingChrome.isModern()) {
				size.width += primaryIcon.getIconWidth() + GTableHeader.HELP_ICON_HEIGHT + 8;
			}
			return size;
		}

		Border currentBorder = getBorder();
		int minHeight = DEFAULT_MIN_HEIGHT;
		if (currentBorder != null) {
			Insets borderInsets = currentBorder.getBorderInsets(this);
			minHeight += borderInsets.top + borderInsets.bottom;
		}
		preferredSize.height = Math.max(preferredSize.height, minHeight);
		return preferredSize;
	}

	private Icon getIcon(TableModel model, int columnModelIndex) {
		Icon icon = null;
		if (model instanceof SortedTableModel) {
			icon = getSortIcon(columnModelIndex, model);
		}
		if (isColumnFiltered(model, columnModelIndex)) {
			icon = combineWithFilterIcon(icon);
		}

		if (icon != null) {
			return icon;
		}
		return EMPTY_ICON;
	}

	private Icon combineWithFilterIcon(Icon icon2) {
		Icon icon1 = FILTER_ICON;
		if (icon2 == null) {
			return icon1;
		}

		int padding = 2;
		int w1 = icon1.getIconWidth();
		int w2 = icon2.getIconWidth();
		int h1 = icon1.getIconHeight();
		int fullWidth = w1 + padding + w2;
		MultiIcon icon = new MultiIcon(new EmptyIcon(fullWidth, h1));
		icon.addIcon(icon1);
		int rightShift = w1 + padding;
		icon.addIcon(new TranslateIcon(icon2, rightShift, 0));
		return icon;
	}

	private boolean isColumnFiltered(TableModel model, int columnModelIndex) {
		if (!(model instanceof RowObjectFilterModel<?> filterModel)) {
			return false;
		}
		TableFilter<?> tableFilter = filterModel.getTableFilter();
		if (tableFilter == null) {
			return false;
		}
		return tableFilter.hasColumnFilter(columnModelIndex);
	}

	private Icon getHelpIcon(JTable table, int currentColumnIndex) {

		JTableHeader tableHeader = table.getTableHeader();
		if (!(tableHeader instanceof GTableHeader tooltipTableHeader)) {
			return EMPTY_ICON;
		}

		int hoveredColumnIndex = tooltipTableHeader.getHoveredHeaderColumnIndex();
		if (hoveredColumnIndex != currentColumnIndex) {
			return EMPTY_ICON;
		}

		Icon icon = tooltipTableHeader.getHelpIcon();
		if (icon != null) {
			return icon;
		}
		return EMPTY_ICON;
	}

	private Icon getSortIcon(int realIndex, TableModel model) {
		SortedTableModel sortedModel = (SortedTableModel) model;
		TableSortState columnSortStates = sortedModel.getTableSortState();

		boolean sortPending = false;
		if (model instanceof AbstractSortedTableModel<?> abstractSortedModel) {
			sortPending = abstractSortedModel.isSortPending();
			if (sortPending) {
				TableSortState pendingTableState = abstractSortedModel.getPendingSortState();
				ColumnSortState pendingColumnState =
					pendingTableState.getColumnSortState(realIndex);
				if (pendingColumnState != null) {
					return getColumnIconForSortState(columnSortStates, pendingColumnState, true);
				}
			}
		}

		ColumnSortState sortState = columnSortStates.getColumnSortState(realIndex);
		if (sortState == null) {
			return null;
		}

		Icon icon = getColumnIconForSortState(columnSortStates, sortState, false);
		if (sortPending) {
			// indicate that the current sort is stale
			icon = ResourceManager.getDisabledIcon(icon, 65);
		}

		return icon;
	}

	private Icon getColumnIconForSortState(TableSortState columnSortStates,
			ColumnSortState sortState, boolean isPendingSort) {

		if (isPendingSort) {
			return PENDING_ICON;
		}

		Icon icon = (sortState.isAscending() ? UP_ICON : DOWN_ICON);
		if (columnSortStates.getSortedColumnCount() != 1) {
			MultiIcon multiIcon = new MultiIcon(icon);
			int sortOrder = sortState.getSortOrder();
			String numberString = Integer.toString(sortOrder);
			multiIcon.addIcon(new NumberPainterIcon(icon.getIconWidth() + PADDING_FOR_COLUMN_NUMBER,
				icon.getIconHeight(), numberString));
			icon = multiIcon;
		}

		return icon;
	}

	private int getIconStartY(int iconHeight) {

		int height = getHeight();
		int middle = height / 2;
		int halfHeight = iconHeight / 2;
		return middle - halfHeight;
	}

//==================================================================================================
// Inner Classes
//==================================================================================================

	private class NumberPainterIcon implements Icon {

		private static final String FONT_ID = "font.table.header.number";
		private final int iconWidth;
		private final int numberWidth;
		private final int iconHeight;
		private final String numberText;

		public NumberPainterIcon(int width, int height, String numberText) {
			this.iconWidth = width;
			this.iconHeight = height;
			this.numberText = numberText;

			Font font = Gui.getFont(FONT_ID);
			FontMetrics fontMetrics = getFontMetrics(font);
			numberWidth = fontMetrics.stringWidth(numberText);
		}

		@Override
		public int getIconHeight() {
			return iconHeight;
		}

		@Override
		public int getIconWidth() {
			return iconWidth + numberWidth;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {

			Font font = Gui.getFont(FONT_ID);
			g.setFont(font);
			g.setColor(Colors.FOREGROUND);
			FontMetrics fontMetrics = g.getFontMetrics();
			int numberHeight = fontMetrics.getAscent();

			// draw the number on the right...
			int padding = 2;
			int startX = x + (iconWidth - numberWidth) + padding;

			// note: padding here helps make up the difference between the number's actual height 
			// and the font metrics ascent
			int heightPadding = 2;
			int absoluteY = y + numberHeight - heightPadding;

			g.drawString(numberText, startX, absoluteY);
		}

	}
}
