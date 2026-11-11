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
package docking;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.Objects;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;

import generic.theme.GColor;
import generic.theme.GThemeDefaults.Colors.Palette;

/**
 * Opt-in modern chrome for docked providers: a thin rounded frame instead of the raised bevel,
 * a softly shaded header with an accent edge, a header separator line, underlined
 * tabs in dock tab stacks, flat underlined tabs in {@code GTabPanel} (open programs), and a flat
 * status bar with separators instead of bevel boxes.
 * <p>
 * Off by default. On when the active theme sets the boolean theme property
 * {@code [laf.boolean]Docking.modernChrome = true}, or with
 * {@code -Dghidra.docking.modernChrome=true}. The decision is made at paint and layout time, so a
 * live theme switch flips the chrome without rebuilding the dock tree. Layout serialization,
 * drag and drop and provider APIs are unchanged.
 */
public final class DockingChrome {
	public static final String UI_KEY = "Docking.modernChrome";
	public static final String SYSTEM_PROPERTY = "ghidra.docking.modernChrome";

	static final Color FRAME_COLOR = new GColor("color.border.docking.frame");
	static final Color ACCENT_COLOR = new GColor("color.bg.docking.header.accent");

	/** Arc of the provider frame, in logical pixels. */
	static final int FRAME_ARC = 10;
	/** Width of the accent edge on the active header. */
	static final int ACCENT_WIDTH = 3;

	/** Height of the underline on a selected tab. */
	public static final int TAB_UNDERLINE = 2;

	/**
	 * FlatLaf client properties for dock tab stacks while the modern chrome is on: underlined
	 * tabs (the stacked look of an IDE tool window) instead of the global card tabs, no tab
	 * separators, a fixed tab height. Plain strings, so this class does not depend on FlatLaf and
	 * other look and feels just ignore them.
	 */
	private static final Map<String, Object> TAB_STACK_PROPERTIES = Map.of(
		"JTabbedPane.tabType", "underlined",
		"JTabbedPane.showTabSeparators", Boolean.FALSE,
		"JTabbedPane.hasFullBorder", Boolean.FALSE,
		"JTabbedPane.showContentSeparator", Boolean.TRUE,
		"JTabbedPane.tabHeight", 26);

	private static final Border FRAME = new FrameBorder(false);
	private static final Border TAB_STACK = new FrameBorder(true);
	private static final Border HEADER = new HeaderBorder();
	private static final Border TITLE_PADDING = new TitlePadding();

	private DockingChrome() {
	}

	public static boolean isModern() {
		return Boolean.getBoolean(SYSTEM_PROPERTY) || UIManager.getBoolean(UI_KEY);
	}

	/** Color of frames, separators and inactive underlines (theme id {@code color.border.docking.frame}). */
	public static Color frameColor() {
		return FRAME_COLOR;
	}

	/** Accent for the focused header edge and the active tab underline. */
	public static Color accentColor() {
		return ACCENT_COLOR;
	}

	/** Paint a quiet vertical surface gradient; high contrast themes use a solid surface. */
	public static void paintSurface(Graphics g, int width, int height, Color top, Color bottom) {
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setPaint(UIManager.getBoolean("Docking.chromeDepth")
					? new GradientPaint(0, 0, top, 0, Math.max(1, height), bottom) : top);
			g2.fillRect(0, 0, width, height);
		}
		finally {
			g2.dispose();
		}
	}

	/**
	 * Sets or clears the modern tab-stack client properties on a dock tab stack. Call it from
	 * {@code updateUI()} before {@code super.updateUI()}: a theme switch installs the new theme
	 * values before it updates component UIs, so the new UI is built with the right properties
	 * and a live switch back to a stock theme restores the stock tabs.
	 * @param tabbedPane the tab stack
	 */
	public static void styleTabStack(JTabbedPane tabbedPane) {
		boolean modern = isModern();
		for (Map.Entry<String, Object> e : TAB_STACK_PROPERTIES.entrySet()) {
			Object want = modern ? e.getValue() : null;
			if (!Objects.equals(tabbedPane.getClientProperty(e.getKey()), want)) {
				tabbedPane.putClientProperty(e.getKey(), want);
			}
		}
	}

	/**
	 * Border for a status bar field. Stock: lowered bevel. Modern: no box; a field that follows
	 * another one gets a thin leading separator. Same insets either way, so the status bar does
	 * not change height on a theme switch.
	 * @param separator true for fields after the first one
	 * @return the border
	 */
	public static Border statusFieldBorder(boolean separator) {
		return new StatusFieldBorder(separator);
	}

	/**
	 * Outer border of the status bar: a gap above it, and in modern mode a hairline that
	 * separates the status bar from the dock area.
	 * @param gap the gap above the status fields
	 * @return the border
	 */
	public static Border statusBarBorder(int gap) {
		return new StatusBarBorder(gap);
	}

	/** Border for a single docked provider. Stock: raised bevel. */
	public static Border frameBorder() {
		return FRAME;
	}

	/** Border for a tab stack. Stock: none (as before). */
	public static Border tabStackBorder() {
		return TAB_STACK;
	}

	/** Border for a provider header. Stock: 1 px gray line. */
	public static Border headerBorder() {
		return HEADER;
	}

	/** Padding inside the header title area. Stock: none. */
	static Border titlePadding() {
		return TITLE_PADDING;
	}

	private static Graphics2D aa(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		return g2;
	}

	private static class FrameBorder extends AbstractBorder {
		private final boolean tabStack;
		private final Border bevel = new javax.swing.border.BevelBorder(
			javax.swing.border.BevelBorder.RAISED);

		FrameBorder(boolean tabStack) {
			this.tabStack = tabStack;
		}

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
			if (!isModern()) {
				if (!tabStack) {
					bevel.paintBorder(c, g, x, y, w, h);
				}
				return;
			}
			Graphics2D g2 = aa(g);
			try {
				g2.setColor(FRAME_COLOR);
				g2.setStroke(new BasicStroke(1f));
				g2.draw(new RoundRectangle2D.Float(x + 1.5f, y + 1.5f, w - 3f, h - 3f, FRAME_ARC,
					FRAME_ARC));
			}
			finally {
				g2.dispose();
			}
		}

		@Override
		public Insets getBorderInsets(Component c, Insets insets) {
			int v = isModern() ? 3 : (tabStack ? 0 : 2);
			insets.set(v, v, v, v);
			return insets;
		}
	}

	private static class HeaderBorder extends AbstractBorder {
		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
			if (isModern()) {
				g.setColor(FRAME_COLOR);
				g.fillRect(x, y + h - 1, w, 1);
				return;
			}
			g.setColor(Palette.GRAY);
			g.drawRect(x, y, w - 1, h - 1);
		}

		@Override
		public Insets getBorderInsets(Component c, Insets insets) {
			if (isModern()) {
				insets.set(0, 0, 1, 0);
			}
			else {
				insets.set(1, 1, 1, 1);
			}
			return insets;
		}
	}

	private static class StatusFieldBorder extends AbstractBorder {
		private final boolean separator;
		private final Border bevel = BorderFactory.createLoweredBevelBorder();

		StatusFieldBorder(boolean separator) {
			this.separator = separator;
		}

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
			if (!isModern()) {
				bevel.paintBorder(c, g, x, y, w, h);
				return;
			}
			if (separator) {
				g.setColor(FRAME_COLOR);
				g.fillRect(x, y + 3, 1, Math.max(0, h - 6));
			}
		}

		@Override
		public Insets getBorderInsets(Component c, Insets insets) {
			// a bevel border is 2 px on every side
			insets.set(2, 2, 2, 2);
			return insets;
		}
	}

	private static class StatusBarBorder extends AbstractBorder {
		private final int gap;

		StatusBarBorder(int gap) {
			this.gap = gap;
		}

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
			if (isModern()) {
				g.setColor(FRAME_COLOR);
				g.fillRect(x, y, w, 1);
			}
		}

		@Override
		public Insets getBorderInsets(Component c, Insets insets) {
			insets.set(gap, 0, 0, 0);
			return insets;
		}
	}

	private static class TitlePadding extends AbstractBorder {
		@Override
		public Insets getBorderInsets(Component c, Insets insets) {
			if (isModern()) {
				insets.set(3, ACCENT_WIDTH + 3, 3, 2);
			}
			else {
				insets.set(0, 0, 0, 0);
			}
			return insets;
		}
	}
}
