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

import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;

import generic.theme.GColor;
import generic.theme.GThemeDefaults.Colors.Palette;

/**
 * Opt-in modern chrome for docked providers: a thin rounded frame instead of the raised bevel,
 * a flat header with an accent edge instead of the gradient, and a header separator line.
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

	private static final Border FRAME = new FrameBorder(false);
	private static final Border TAB_STACK = new FrameBorder(true);
	private static final Border HEADER = new HeaderBorder();
	private static final Border TITLE_PADDING = new TitlePadding();

	private DockingChrome() {
	}

	public static boolean isModern() {
		return Boolean.getBoolean(SYSTEM_PROPERTY) || UIManager.getBoolean(UI_KEY);
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

		@Override
		public boolean isBorderOpaque() {
			return false;
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
