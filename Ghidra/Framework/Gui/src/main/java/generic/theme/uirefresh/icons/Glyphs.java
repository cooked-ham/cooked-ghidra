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
package generic.theme.uirefresh.icons;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Original UI Refresh glyph set (UI-040). Every glyph is drawn with Java2D on a 16 x 16 grid, one
 * stroke weight (1.3 units, round caps and joins), ink for the shape and color only for status or
 * the one accent detail. Drawn at paint time, so it is sharp at any scale (125 %, 150 %, 200 %).
 * <p>
 * No Ghidra dependency on purpose: {@link #main} renders the PNG fallbacks at build time without
 * starting Ghidra.
 */
public final class Glyphs {

	/** Colors a glyph may use. One per theme. */
	public record Palette(String key, Color ink, Color soft, Color accent, Color positive,
			Color warning, Color error) {
	}

	@FunctionalInterface
	interface Painter {
		void paint(Graphics2D g, Palette p);
	}

	static final float W = 1.3f;
	private static final Map<String, Painter> GLYPHS = new LinkedHashMap<>();

	private Glyphs() {
	}

	public static Set<String> names() {
		return Collections.unmodifiableSet(GLYPHS.keySet());
	}

	public static boolean exists(String name) {
		return GLYPHS.containsKey(name);
	}

	/** Paints glyph {@code name} into a 16 x 16 box at the current origin. */
	public static void paint(String name, Graphics2D g, Palette p) {
		Painter painter = GLYPHS.get(name);
		if (painter == null) {
			return;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setStroke(new BasicStroke(W, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		painter.paint(g, p);
	}

	public static BufferedImage render(String name, Palette p, double scale) {
		int px = (int) Math.ceil(16 * scale);
		BufferedImage img = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.scale(scale, scale);
		paint(name, g, p);
		g.dispose();
		return img;
	}

	// ---- palettes, kept in step with the theme tokens -------------------------------------------
	public static final Palette DARK = new Palette("dark", new Color(0xC9D1DE),
		new Color(0xC9D1DE, false).darker(), new Color(0x7FA7F2), new Color(0x91CBA6),
		new Color(0xE7BF79), new Color(0xE48D94));
	public static final Palette GRAPHITE = new Palette("graphite", new Color(0xF2EDE5),
		new Color(0xC3B7A8), new Color(0xE8AD63), new Color(0x9ACAAB),
		new Color(0xF1D982), new Color(0xF09B94));
	public static final Palette LIGHT = new Palette("light", new Color(0x3A4556),
		new Color(0x8792A3), new Color(0x2862D0), new Color(0x2F7D5B), new Color(0xB07A10),
		new Color(0xC23B44));
	public static final Palette HIGH_CONTRAST = new Palette("hc", new Color(0xFFFFFF),
		new Color(0xBDBDBD), new Color(0x8CC8FF), new Color(0x7CF29A), new Color(0xFFD27A),
		new Color(0xFF9C9C));

	// ---- drawing helpers ----------------------------------------------------------------------
	private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
		g.draw(new Line2D.Double(x1, y1, x2, y2));
	}

	private static void poly(Graphics2D g, boolean close, double... xy) {
		Path2D p = new Path2D.Double();
		p.moveTo(xy[0], xy[1]);
		for (int i = 2; i < xy.length; i += 2) {
			p.lineTo(xy[i], xy[i + 1]);
		}
		if (close) {
			p.closePath();
		}
		g.draw(p);
	}

	private static void rrect(Graphics2D g, double x, double y, double w, double h, double r) {
		g.draw(new RoundRectangle2D.Double(x, y, w, h, r, r));
	}

	private static void fillRrect(Graphics2D g, double x, double y, double w, double h, double r) {
		g.fill(new RoundRectangle2D.Double(x, y, w, h, r, r));
	}

	private static void circle(Graphics2D g, double cx, double cy, double r) {
		g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
	}

	private static void dot(Graphics2D g, double cx, double cy, double r) {
		g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
	}

	private static Color alpha(Color c, double a) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) Math.round(a * 255));
	}

	private static void arrowHead(Graphics2D g, double x, double y, double dx, double dy) {
		// open chevron pointing along (dx, dy) with its tip at (x, y)
		double len = 3.2;
		double nx = -dy;
		poly(g, false, x - dx * len + nx * len, y - dy * len + dx * len, x, y,
			x - dx * len - nx * len, y - dy * len - dx * len);
	}

	private static void arrow(Graphics2D g, double x1, double y1, double x2, double y2) {
		line(g, x1, y1, x2, y2);
		double d = Math.hypot(x2 - x1, y2 - y1);
		arrowHead(g, x2, y2, (x2 - x1) / d, (y2 - y1) / d);
	}

	private static void page(Graphics2D g, Palette p, double x, double y, double w, double h) {
		Path2D s = new Path2D.Double();
		s.moveTo(x, y + 1);
		s.quadTo(x, y, x + 1, y);
		s.lineTo(x + w - 3, y);
		s.lineTo(x + w, y + 3);
		s.lineTo(x + w, y + h - 1);
		s.quadTo(x + w, y + h, x + w - 1, y + h);
		s.lineTo(x + 1, y + h);
		s.quadTo(x, y + h, x, y + h - 1);
		s.closePath();
		g.setColor(alpha(p.ink, 0.12));
		g.fill(s);
		g.setColor(p.ink);
		g.draw(s);
		poly(g, false, x + w - 3, y, x + w - 3, y + 3, x + w, y + 3);
	}

	private static void folder(Graphics2D g, Palette p, boolean open) {
		Path2D s = new Path2D.Double();
		s.moveTo(1.5, 4);
		s.quadTo(1.5, 3, 2.5, 3);
		s.lineTo(6, 3);
		s.lineTo(7.5, 4.5);
		s.lineTo(13.5, 4.5);
		s.quadTo(14.5, 4.5, 14.5, 5.5);
		s.lineTo(14.5, 12);
		s.quadTo(14.5, 13, 13.5, 13);
		s.lineTo(2.5, 13);
		s.quadTo(1.5, 13, 1.5, 12);
		s.closePath();
		g.setColor(alpha(p.warning, 0.28));
		g.fill(s);
		g.setColor(p.ink);
		g.draw(s);
		if (open) {
			g.setColor(alpha(p.warning, 0.55));
			Path2D lid = new Path2D.Double();
			lid.moveTo(3.5, 7);
			lid.lineTo(15.2, 7);
			lid.lineTo(13.5, 13);
			lid.lineTo(1.5, 13);
			lid.closePath();
			g.fill(lid);
			g.setColor(p.ink);
			g.draw(lid);
		}
		else {
			line(g, 1.5, 6.5, 14.5, 6.5);
		}
	}

	private static void letter(Graphics2D g, Palette p, String ch) {
		g.setColor(alpha(p.accent, 0.18));
		fillRrect(g, 1.5, 1.5, 13, 13, 4);
		g.setColor(p.accent);
		rrect(g, 1.5, 1.5, 13, 13, 4);
		Font f = new Font(Font.SANS_SERIF, Font.BOLD, 10);
		g.setFont(f);
		FontMetrics fm = g.getFontMetrics(f);
		Rectangle2D b = fm.getStringBounds(ch, g);
		g.setColor(p.ink);
		g.drawString(ch, (float) (8 - b.getWidth() / 2), (float) (8 + fm.getAscent() * 0.36));
	}

	private static void gear(Graphics2D g, Palette p) {
		double cx = 8;
		double cy = 8;
		double r = 6.2;
		Path2D s = new Path2D.Double();
		int teeth = 8;
		for (int i = 0; i < teeth * 2; i++) {
			double a = Math.PI * i / teeth;
			double rr = (i % 2 == 0) ? r : r * 0.74;
			double a1 = a - Math.PI / teeth / 2, a2 = a + Math.PI / teeth / 2;
			double x1 = cx + rr * Math.cos(a1), y1 = cy + rr * Math.sin(a1);
			double x2 = cx + rr * Math.cos(a2), y2 = cy + rr * Math.sin(a2);
			if (i == 0) {
				s.moveTo(x1, y1);
			}
			else {
				s.lineTo(x1, y1);
			}
			s.lineTo(x2, y2);
		}
		s.closePath();
		g.setColor(p.ink);
		g.draw(s);
		circle(g, cx, cy, r * 0.32);
	}

	private static void magnifier(Graphics2D g, Palette p) {
		g.setColor(p.ink);
		circle(g, 6.5, 6.5, 4.2);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		line(g, 9.7, 9.7, 13.8, 13.8);
		g.setStroke(new BasicStroke(W, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
	}

	// ---- the set ----------------------------------------------------------------------------
	static {
		GLYPHS.put("save", (g, p) -> {
			Path2D s = new Path2D.Double();
			s.moveTo(2.5, 2);
			s.lineTo(11.5, 2);
			s.lineTo(14, 4.5);
			s.lineTo(14, 13);
			s.quadTo(14, 14, 13, 14);
			s.lineTo(3, 14);
			s.quadTo(2, 14, 2, 13);
			s.lineTo(2, 3);
			s.quadTo(2, 2, 3, 2);
			s.closePath();
			g.setColor(alpha(p.accent, 0.18));
			g.fill(s);
			g.setColor(p.ink);
			g.draw(s);
			rrect(g, 5, 2, 6, 3.6, 1);
			rrect(g, 4.5, 8.5, 7, 5.5, 1);
		});
		GLYPHS.put("saveAs", (g, p) -> {
			GLYPHS.get("save").paint(g, p);
			g.setColor(p.accent);
			g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			line(g, 9.5, 14.5, 15, 9);
		});
		GLYPHS.put("left", (g, p) -> {
			g.setColor(p.ink);
			arrow(g, 13.5, 8, 2.8, 8);
		});
		GLYPHS.put("right", (g, p) -> {
			g.setColor(p.ink);
			arrow(g, 2.5, 8, 13.2, 8);
		});
		GLYPHS.put("up", (g, p) -> {
			g.setColor(p.ink);
			arrow(g, 8, 13.5, 8, 2.8);
		});
		GLYPHS.put("down", (g, p) -> {
			g.setColor(p.ink);
			arrow(g, 8, 2.5, 8, 13.2);
		});
		GLYPHS.put("refresh", (g, p) -> {
			g.setColor(p.ink);
			g.draw(new Arc2D.Double(2.5, 2.5, 11, 11, 70, 280, Arc2D.OPEN));
			arrowHead(g, 10.4, 3.1, 0.95, 0.3);
		});
		GLYPHS.put("undo", (g, p) -> {
			g.setColor(p.ink);
			Path2D s = new Path2D.Double();
			s.moveTo(3, 6.5);
			s.lineTo(10, 6.5);
			s.curveTo(14.5, 6.5, 14.5, 13, 10, 13);
			s.lineTo(6.5, 13);
			g.draw(s);
			arrowHead(g, 3, 6.5, -1, 0);
		});
		GLYPHS.put("redo", (g, p) -> {
			g.setColor(p.ink);
			Path2D s = new Path2D.Double();
			s.moveTo(13, 6.5);
			s.lineTo(6, 6.5);
			s.curveTo(1.5, 6.5, 1.5, 13, 6, 13);
			s.lineTo(9.5, 13);
			g.draw(s);
			arrowHead(g, 13, 6.5, 1, 0);
		});
		GLYPHS.put("add", (g, p) -> {
			g.setColor(p.positive);
			g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			line(g, 8, 3, 8, 13);
			line(g, 3, 8, 13, 8);
		});
		GLYPHS.put("subtract", (g, p) -> {
			g.setColor(p.ink);
			g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			line(g, 3, 8, 13, 8);
		});
		GLYPHS.put("delete", (g, p) -> {
			g.setColor(p.ink);
			poly(g, false, 3, 4.5, 13, 4.5);
			poly(g, false, 6.3, 4.5, 6.3, 2.8, 9.7, 2.8, 9.7, 4.5);
			Path2D bin = new Path2D.Double();
			bin.moveTo(4.2, 4.5);
			bin.lineTo(5, 13.2);
			bin.quadTo(5.1, 14, 6, 14);
			bin.lineTo(10, 14);
			bin.quadTo(10.9, 14, 11, 13.2);
			bin.lineTo(11.8, 4.5);
			g.setColor(alpha(p.error, 0.2));
			g.fill(bin);
			g.setColor(p.ink);
			g.draw(bin);
			g.setColor(p.error);
			line(g, 6.9, 7, 6.9, 11.5);
			line(g, 9.1, 7, 9.1, 11.5);
		});
		GLYPHS.put("close", (g, p) -> {
			g.setColor(p.ink);
			line(g, 4.5, 4.5, 11.5, 11.5);
			line(g, 11.5, 4.5, 4.5, 11.5);
		});
		GLYPHS.put("closeHot", (g, p) -> {
			g.setColor(alpha(p.error, 0.3));
			fillRrect(g, 1.5, 1.5, 13, 13, 5);
			g.setColor(p.ink);
			line(g, 4.8, 4.8, 11.2, 11.2);
			line(g, 11.2, 4.8, 4.8, 11.2);
		});
		GLYPHS.put("copy", (g, p) -> {
			page(g, p, 5, 4.5, 8.5, 10);
			g.setColor(p.ink);
			poly(g, false, 3, 11.5, 2.5, 11.5, 2.5, 2.5, 9.5, 2.5, 9.5, 3);
		});
		GLYPHS.put("paste", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 2.5, 3, 9, 11.5, 2);
			g.setColor(alpha(p.accent, 0.3));
			fillRrect(g, 4.5, 1.5, 5, 3, 1.2);
			g.setColor(p.ink);
			rrect(g, 4.5, 1.5, 5, 3, 1.2);
			page(g, p, 7.5, 7, 7, 8);
		});
		GLYPHS.put("cut", (g, p) -> {
			g.setColor(p.ink);
			circle(g, 4.5, 12, 2.2);
			circle(g, 11.5, 12, 2.2);
			line(g, 5.8, 10.2, 11.5, 2);
			line(g, 10.2, 10.2, 4.5, 2);
		});
		GLYPHS.put("search", Glyphs::magnifier);
		GLYPHS.put("zoomIn", (g, p) -> {
			magnifier(g, p);
			line(g, 4.5, 6.5, 8.5, 6.5);
			line(g, 6.5, 4.5, 6.5, 8.5);
		});
		GLYPHS.put("zoomOut", (g, p) -> {
			magnifier(g, p);
			line(g, 4.5, 6.5, 8.5, 6.5);
		});
		GLYPHS.put("filter", (g, p) -> {
			g.setColor(p.ink);
			poly(g, true, 2, 3, 14, 3, 9.3, 8.6, 9.3, 13.5, 6.7, 12, 6.7, 8.6);
		});
		GLYPHS.put("filterOn", (g, p) -> {
			Path2D s = new Path2D.Double();
			s.moveTo(2, 3);
			s.lineTo(14, 3);
			s.lineTo(9.3, 8.6);
			s.lineTo(9.3, 13.5);
			s.lineTo(6.7, 12);
			s.lineTo(6.7, 8.6);
			s.closePath();
			g.setColor(alpha(p.accent, 0.45));
			g.fill(s);
			g.setColor(p.accent);
			g.draw(s);
		});
		GLYPHS.put("configure", Glyphs::gear);
		GLYPHS.put("home", (g, p) -> {
			g.setColor(p.ink);
			poly(g, false, 1.8, 8, 8, 2.2, 14.2, 8);
			poly(g, false, 3.8, 6.5, 3.8, 13.8, 12.2, 13.8, 12.2, 6.5);
			g.setColor(p.accent);
			rrect(g, 6.6, 9.5, 2.8, 4.3, 0.8);
		});
		GLYPHS.put("help", (g, p) -> statusBadge(g, p.accent, "help"));
		GLYPHS.put("info", (g, p) -> statusBadge(g, p.accent, "info"));
		GLYPHS.put("warning", (g, p) -> statusBadge(g, p.warning, "warning"));
		GLYPHS.put("error", (g, p) -> statusBadge(g, p.error, "error"));
		GLYPHS.put("stop", (g, p) -> statusBadge(g, p.error, "stop"));
		GLYPHS.put("notAllowed", (g, p) -> statusBadge(g, p.error, "not"));
		GLYPHS.put("folder", (g, p) -> folder(g, p, false));
		GLYPHS.put("folderOpen", (g, p) -> folder(g, p, true));
		GLYPHS.put("folderAdd", (g, p) -> {
			folder(g, p, false);
			g.setColor(p.positive);
			g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			line(g, 10.5, 8, 10.5, 12.5);
			line(g, 8.25, 10.25, 12.75, 10.25);
		});
		GLYPHS.put("collapseAll", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 2.5, 2.5, 11, 11, 3);
			line(g, 5.5, 8, 10.5, 8);
		});
		GLYPHS.put("expandAll", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 2.5, 2.5, 11, 11, 3);
			line(g, 5.5, 8, 10.5, 8);
			line(g, 8, 5.5, 8, 10.5);
		});
		GLYPHS.put("chevronDown", (g, p) -> {
			g.setColor(p.ink);
			poly(g, false, 4.5, 6.5, 8, 10, 11.5, 6.5);
		});
		GLYPHS.put("chevronUp", (g, p) -> {
			g.setColor(p.ink);
			poly(g, false, 4.5, 9.5, 8, 6, 11.5, 9.5);
		});
		GLYPHS.put("menu", (g, p) -> {
			g.setColor(p.ink);
			poly(g, false, 4.5, 6.5, 8, 10, 11.5, 6.5);
		});
		GLYPHS.put("clear", (g, p) -> {
			Path2D s = new Path2D.Double();
			s.moveTo(9.5, 2.5);
			s.lineTo(14, 7);
			s.lineTo(8, 13);
			s.lineTo(5, 13);
			s.lineTo(2.5, 10.5);
			s.closePath();
			g.setColor(alpha(p.accent, 0.25));
			g.fill(s);
			g.setColor(p.ink);
			g.draw(s);
			line(g, 6, 6, 10.5, 10.5);
			line(g, 8, 13.5, 14, 13.5);
		});
		GLYPHS.put("lock", (g, p) -> {
			g.setColor(p.ink);
			g.draw(new Arc2D.Double(5, 2, 6, 8, 0, 180, Arc2D.OPEN));
			line(g, 5, 6, 5, 7.5);
			line(g, 11, 6, 11, 7.5);
			g.setColor(alpha(p.warning, 0.3));
			fillRrect(g, 3, 7.5, 10, 7, 2);
			g.setColor(p.ink);
			rrect(g, 3, 7.5, 10, 7, 2);
			dot(g, 8, 11, 1.3);
		});
		GLYPHS.put("unlock", (g, p) -> {
			g.setColor(p.ink);
			g.draw(new Arc2D.Double(5, 1.5, 6, 8, 20, 160, Arc2D.OPEN));
			line(g, 5, 5.5, 5, 7.5);
			rrect(g, 3, 7.5, 10, 7, 2);
			dot(g, 8, 11, 1.3);
		});
		GLYPHS.put("check", (g, p) -> {
			g.setColor(p.positive);
			g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			poly(g, false, 2.8, 8.5, 6.3, 12, 13.2, 4.2);
		});
		GLYPHS.put("run", (g, p) -> {
			Path2D t = new Path2D.Double();
			t.moveTo(4.5, 2.8);
			t.lineTo(13, 8);
			t.lineTo(4.5, 13.2);
			t.closePath();
			g.setColor(alpha(p.positive, 0.45));
			g.fill(t);
			g.setColor(p.positive);
			g.draw(t);
		});
		GLYPHS.put("table", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 1.5, 2.5, 13, 11, 2.5);
			g.setColor(alpha(p.accent, 0.3));
			g.fill(new Rectangle2D.Double(2.2, 3.2, 11.6, 2.8));
			g.setColor(p.ink);
			line(g, 1.5, 6, 14.5, 6);
			line(g, 1.5, 9.7, 14.5, 9.7);
			line(g, 6, 6, 6, 13.5);
		});
		GLYPHS.put("sortAsc", (g, p) -> {
			g.setColor(p.ink);
			arrow(g, 4.5, 13, 4.5, 3);
			line(g, 8, 4, 14, 4);
			line(g, 8, 8, 12.5, 8);
			line(g, 8, 12, 11, 12);
		});
		GLYPHS.put("sortDesc", (g, p) -> {
			g.setColor(p.ink);
			arrow(g, 4.5, 3, 4.5, 13);
			line(g, 8, 4, 11, 4);
			line(g, 8, 8, 12.5, 8);
			line(g, 8, 12, 14, 12);
		});
		GLYPHS.put("properties", (g, p) -> {
			page(g, p, 2.5, 1.5, 11, 13);
			g.setColor(p.ink);
			line(g, 5, 6.5, 11, 6.5);
			line(g, 5, 9, 11, 9);
			line(g, 5, 11.5, 9, 11.5);
		});
		GLYPHS.put("rename", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 1.5, 4.5, 13, 7, 2);
			g.setColor(p.accent);
			line(g, 9, 2.5, 9, 13.5);
			line(g, 7.5, 2.5, 10.5, 2.5);
			line(g, 7.5, 13.5, 10.5, 13.5);
			g.setColor(p.ink);
			line(g, 4, 8, 6.5, 8);
		});
		GLYPHS.put("flag", (g, p) -> {
			g.setColor(p.ink);
			line(g, 3.5, 2, 3.5, 14.5);
			Path2D f = new Path2D.Double();
			f.moveTo(3.5, 2.5);
			f.curveTo(7, 1, 9, 4.5, 13, 3);
			f.lineTo(13, 9);
			f.curveTo(9, 10.5, 7, 7, 3.5, 8.5);
			g.setColor(alpha(p.error, 0.45));
			g.fill(f);
			g.setColor(p.error);
			g.draw(f);
		});
		GLYPHS.put("pin", (g, p) -> {
			g.setColor(p.ink);
			poly(g, true, 6, 2, 10, 2, 9.3, 6.5, 11.5, 9, 4.5, 9, 6.7, 6.5);
			line(g, 8, 9, 8, 14.5);
		});
		GLYPHS.put("function", (g, p) -> {
			g.setColor(alpha(p.accent, 0.18));
			fillRrect(g, 1.5, 1.5, 13, 13, 4);
			g.setColor(p.accent);
			rrect(g, 1.5, 1.5, 13, 13, 4);
			Path2D f = new Path2D.Double();
			f.moveTo(10.5, 4);
			f.curveTo(9, 3, 7.6, 3.6, 7.4, 5.5);
			f.lineTo(6.6, 11);
			f.curveTo(6.4, 12.6, 5, 13, 4, 12.2);
			g.setColor(p.ink);
			g.draw(f);
			line(g, 5.2, 7, 9.8, 7);
		});
		GLYPHS.put("memory", (g, p) -> {
			g.setColor(alpha(p.accent, 0.18));
			fillRrect(g, 3.5, 3.5, 9, 9, 2);
			g.setColor(p.ink);
			rrect(g, 3.5, 3.5, 9, 9, 2);
			for (double v : new double[] { 6, 8, 10 }) {
				line(g, v, 1.5, v, 3.5);
				line(g, v, 12.5, v, 14.5);
				line(g, 1.5, v, 3.5, v);
				line(g, 12.5, v, 14.5, v);
			}
		});
		GLYPHS.put("dataTypes", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 1.5, 2.5, 5.5, 5.5, 1.5);
			rrect(g, 9, 2.5, 5.5, 5.5, 1.5);
			g.setColor(alpha(p.accent, 0.3));
			fillRrect(g, 5.2, 9.5, 5.5, 5, 1.5);
			g.setColor(p.accent);
			rrect(g, 5.2, 9.5, 5.5, 5, 1.5);
			g.setColor(p.ink);
			poly(g, false, 4.3, 8, 4.3, 9, 8, 9, 8, 9.5);
			poly(g, false, 11.7, 8, 11.7, 9, 8, 9);
		});
		GLYPHS.put("listing", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 1.5, 2, 13, 12, 2.5);
			g.setColor(p.accent);
			line(g, 4, 5.5, 6, 5.5);
			line(g, 4, 8, 6, 8);
			line(g, 4, 10.5, 6, 10.5);
			g.setColor(p.ink);
			line(g, 7.8, 5.5, 12, 5.5);
			line(g, 7.8, 8, 11, 8);
			line(g, 7.8, 10.5, 12, 10.5);
		});
		GLYPHS.put("console", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 1.5, 2.5, 13, 11, 2.5);
			g.setColor(p.accent);
			poly(g, false, 4, 6, 6.5, 8, 4, 10);
			g.setColor(p.ink);
			line(g, 8, 10.5, 11.5, 10.5);
		});
		GLYPHS.put("navigateIn", (g, p) -> {
			g.setColor(p.ink);
			poly(g, false, 9, 2.5, 13.5, 2.5, 13.5, 13.5, 9, 13.5);
			g.setColor(p.accent);
			arrow(g, 2, 8, 10.5, 8);
		});
		GLYPHS.put("navigateOut", (g, p) -> {
			g.setColor(p.ink);
			poly(g, false, 7, 2.5, 2.5, 2.5, 2.5, 13.5, 7, 13.5);
			g.setColor(p.accent);
			arrow(g, 5.5, 8, 14, 8);
		});
		GLYPHS.put("selection", (g, p) -> {
			g.setColor(p.ink);
			line(g, 2.5, 3.5, 13.5, 3.5);
			g.setColor(alpha(p.accent, 0.35));
			fillRrect(g, 1.5, 5.8, 13, 4.4, 1.5);
			g.setColor(p.ink);
			line(g, 3.5, 8, 12.5, 8);
			line(g, 2.5, 12.5, 13.5, 12.5);
		});
		GLYPHS.put("bookmark", (g, p) -> {
			Path2D b = new Path2D.Double();
			b.moveTo(4, 2);
			b.lineTo(12, 2);
			b.lineTo(12, 14);
			b.lineTo(8, 10.8);
			b.lineTo(4, 14);
			b.closePath();
			g.setColor(alpha(p.accent, 0.3));
			g.fill(b);
			g.setColor(p.accent);
			g.draw(b);
		});
		GLYPHS.put("notes", (g, p) -> {
			page(g, p, 2.5, 1.5, 11, 13);
			g.setColor(p.warning);
			line(g, 5, 6.5, 11, 6.5);
			line(g, 5, 9, 11, 9);
			line(g, 5, 11.5, 8.5, 11.5);
		});
		GLYPHS.put("tree", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 1.5, 1.5, 5, 4, 1.2);
			rrect(g, 9.5, 6, 5, 4, 1.2);
			rrect(g, 9.5, 11, 5, 4, 1.2);
			poly(g, false, 4, 5.5, 4, 13, 9.5, 13);
			line(g, 4, 8, 9.5, 8);
		});
		GLYPHS.put("window", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 1.5, 2.5, 13, 11, 2.5);
			g.setColor(alpha(p.accent, 0.35));
			g.fill(new Rectangle2D.Double(2.2, 3.2, 11.6, 2.6));
			g.setColor(p.ink);
			line(g, 1.5, 6, 14.5, 6);
		});
		GLYPHS.put("pending", (g, p) -> {
			g.setColor(p.ink);
			circle(g, 8, 8, 6.2);
			poly(g, false, 8, 4.3, 8, 8, 10.6, 9.6);
		});
		// ---- batch 2: provider and toolbar icons found by the visible-icon audit ----
		GLYPHS.put("decompiler", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 1.5, 2, 13, 12, 2.5);
			g.setColor(p.accent);
			Path2D l = new Path2D.Double();
			l.moveTo(6.5, 4.5);
			l.quadTo(5, 4.5, 5, 6);
			l.lineTo(5, 7);
			l.quadTo(5, 8, 4, 8);
			l.quadTo(5, 8, 5, 9);
			l.lineTo(5, 10);
			l.quadTo(5, 11.5, 6.5, 11.5);
			g.draw(l);
			g.draw(AffineTransform.getTranslateInstance(16, 0).createTransformedShape(
				AffineTransform.getScaleInstance(-1, 1).createTransformedShape(l)));
		});
		GLYPHS.put("clone", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 1.5, 1.5, 9, 8, 2);
			g.setColor(alpha(p.accent, 0.3));
			fillRrect(g, 5.5, 6.5, 9, 8, 2);
			g.setColor(p.accent);
			rrect(g, 5.5, 6.5, 9, 8, 2);
		});
		GLYPHS.put("graph", (g, p) -> {
			g.setColor(p.ink);
			line(g, 8, 5, 4, 11);
			line(g, 8, 5, 12, 11);
			g.setColor(alpha(p.accent, 0.35));
			fillRrect(g, 5.5, 1.5, 5, 4, 1.2);
			g.setColor(p.accent);
			rrect(g, 5.5, 1.5, 5, 4, 1.2);
			g.setColor(p.ink);
			rrect(g, 1.5, 10.5, 5, 4, 1.2);
			rrect(g, 9.5, 10.5, 5, 4, 1.2);
		});
		GLYPHS.put("bytes", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 1.5, 2, 13, 12, 2.5);
			g.setColor(p.accent);
			for (double y : new double[] { 5.5, 8, 10.5 }) {
				line(g, 4, y, 5.5, y);
				line(g, 7.3, y, 8.8, y);
				line(g, 10.5, y, 12, y);
			}
		});
		GLYPHS.put("nextBlock", (g, p) -> {
			g.setColor(alpha(p.accent, 0.35));
			fillRrect(g, 2, 10.5, 12, 3.5, 1.2);
			g.setColor(p.ink);
			arrow(g, 8, 1.8, 8, 8.6);
		});
		GLYPHS.put("prevBlock", (g, p) -> {
			g.setColor(alpha(p.accent, 0.35));
			fillRrect(g, 2, 2, 12, 3.5, 1.2);
			g.setColor(p.ink);
			arrow(g, 8, 14.2, 8, 7.4);
		});
		GLYPHS.put("edit", (g, p) -> {
			Path2D pen = new Path2D.Double();
			pen.moveTo(10.5, 2.5);
			pen.lineTo(13.5, 5.5);
			pen.lineTo(5.5, 13.5);
			pen.lineTo(2.5, 13.5);
			pen.lineTo(2.5, 10.5);
			pen.closePath();
			g.setColor(alpha(p.accent, 0.25));
			g.fill(pen);
			g.setColor(p.ink);
			g.draw(pen);
			line(g, 8.8, 4.2, 11.8, 7.2);
		});
		GLYPHS.put("readOnly", (g, p) -> {
			GLYPHS.get("edit").paint(g, p);
			g.setColor(p.error);
			line(g, 2, 2, 14, 14);
		});
		GLYPHS.put("hover", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 5.5, 1.5, 9, 6, 2);
			Path2D c = new Path2D.Double();
			c.moveTo(2, 6);
			c.lineTo(2, 14);
			c.lineTo(4.2, 11.8);
			c.lineTo(6, 14.5);
			c.lineTo(7.3, 13.7);
			c.lineTo(5.6, 11.1);
			c.lineTo(8.5, 11);
			c.closePath();
			g.setColor(alpha(p.accent, 0.4));
			g.fill(c);
			g.setColor(p.ink);
			g.draw(c);
		});
		GLYPHS.put("key", (g, p) -> {
			g.setColor(p.ink);
			circle(g, 5, 11, 3);
			line(g, 7.2, 8.8, 13.5, 2.5);
			line(g, 11, 5, 13, 7);
			line(g, 12.5, 3.5, 14.2, 5.2);
		});
		GLYPHS.put("scriptAdd", (g, p) -> {
			page(g, p, 2, 1.5, 9.5, 12);
			g.setColor(p.positive);
			g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			line(g, 12, 9, 12, 15);
			line(g, 9, 12, 15, 12);
		});
		GLYPHS.put("merge", (g, p) -> {
			g.setColor(p.ink);
			Path2D m = new Path2D.Double();
			m.moveTo(3.5, 2);
			m.lineTo(3.5, 5);
			m.curveTo(3.5, 8, 8, 8, 8, 10.5);
			m.moveTo(12.5, 2);
			m.lineTo(12.5, 5);
			m.curveTo(12.5, 8, 8, 8, 8, 10.5);
			g.draw(m);
			arrow(g, 8, 10, 8, 14.2);
		});
		GLYPHS.put("move", (g, p) -> {
			g.setColor(p.ink);
			arrow(g, 8, 8, 8, 1.8);
			arrow(g, 8, 8, 8, 14.2);
			arrow(g, 8, 8, 1.8, 8);
			arrow(g, 8, 8, 14.2, 8);
		});
		GLYPHS.put("split", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 1.5, 2.5, 13, 11, 2.5);
			g.setColor(p.accent);
			g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
				new float[] { 1.6f, 1.6f }, 0));
			line(g, 8, 3.5, 8, 12.5);
		});
		GLYPHS.put("list", (g, p) -> {
			g.setColor(p.accent);
			dot(g, 3.2, 4, 1.1);
			dot(g, 3.2, 8, 1.1);
			dot(g, 3.2, 12, 1.1);
			g.setColor(p.ink);
			line(g, 6, 4, 14, 4);
			line(g, 6, 8, 14, 8);
			line(g, 6, 12, 14, 12);
		});
		GLYPHS.put("link", (g, p) -> {
			g.setColor(p.ink);
			g.draw(new RoundRectangle2D.Double(1.5, 5.5, 7.5, 5, 5, 5));
			g.setColor(p.accent);
			g.draw(new RoundRectangle2D.Double(7, 5.5, 7.5, 5, 5, 5));
		});
		GLYPHS.put("letterI", (g, p) -> letter(g, p, "I"));
		GLYPHS.put("letterD", (g, p) -> letter(g, p, "D"));
		GLYPHS.put("letterU", (g, p) -> letter(g, p, "U"));
		GLYPHS.put("letterL", (g, p) -> letter(g, p, "L"));
		GLYPHS.put("letterF", (g, p) -> letter(g, p, "F"));
		GLYPHS.put("letterV", (g, p) -> letter(g, p, "V"));
		GLYPHS.put("letterB", (g, p) -> letter(g, p, "B"));
		GLYPHS.put("letterC", (g, p) -> letter(g, p, "C"));
		// debugger and trace artwork
		GLYPHS.put("stepInto", (g, p) -> {
			g.setColor(p.ink);
			arrow(g, 8, 2.5, 8, 10);
			g.setColor(p.accent);
			dot(g, 8, 13, 1.6);
		});
		GLYPHS.put("stepBack", (g, p) -> {
			g.setColor(p.ink);
			arrow(g, 13, 8, 5, 8);
			line(g, 2.8, 3.5, 2.8, 12.5);
		});
		GLYPHS.put("record", (g, p) -> {
			g.setColor(p.error);
			circle(g, 8, 8, 6);
			dot(g, 8, 8, 3);
		});
		GLYPHS.put("breakpoint", (g, p) -> {
			g.setColor(alpha(p.error, 0.35));
			g.fill(new Ellipse2D.Double(2.5, 2.5, 11, 11));
			g.setColor(p.error);
			circle(g, 8, 8, 5.5);
		});
		GLYPHS.put("breakpointMixed", (g, p) -> {
			g.setColor(alpha(p.error, 0.35));
			g.fill(new Arc2D.Double(2.5, 2.5, 11, 11, 90, 180, Arc2D.CHORD));
			g.setColor(p.error);
			circle(g, 8, 8, 5.5);
			line(g, 8, 2.5, 8, 13.5);
		});
		GLYPHS.put("stack", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 2.5, 2.5, 11, 3, 1.5);
			rrect(g, 2.5, 6.5, 11, 3, 1.5);
			g.setColor(p.accent);
			rrect(g, 2.5, 10.5, 11, 3, 1.5);
		});
		GLYPHS.put("modules", (g, p) -> {
			g.setColor(p.ink);
			rrect(g, 2.5, 2.5, 5, 5, 1.5);
			rrect(g, 8.5, 2.5, 5, 5, 1.5);
			rrect(g, 2.5, 8.5, 5, 5, 1.5);
			g.setColor(p.accent);
			rrect(g, 8.5, 8.5, 5, 5, 1.5);
		});
		GLYPHS.put("sync", (g, p) -> {
			g.setColor(p.ink);
			arrow(g, 5, 13, 5, 3);
			g.setColor(p.accent);
			arrow(g, 11, 3, 11, 13);
		});
		// tags
		GLYPHS.put("tag", (g, p) -> {
			Path2D t = new Path2D.Double();
			t.moveTo(2.5, 3.5);
			t.lineTo(8.5, 3.5);
			t.lineTo(13.5, 8.5);
			t.lineTo(8.5, 13.5);
			t.lineTo(2.5, 7.5);
			t.closePath();
			g.setColor(alpha(p.accent, 0.25));
			g.fill(t);
			g.setColor(p.ink);
			g.draw(t);
			dot(g, 5.5, 6, 1.3);
		});
		GLYPHS.put("tagRemove", (g, p) -> {
			Path2D t = new Path2D.Double();
			t.moveTo(2.5, 3.5);
			t.lineTo(8.5, 3.5);
			t.lineTo(13.5, 8.5);
			t.lineTo(8.5, 13.5);
			t.lineTo(2.5, 7.5);
			t.closePath();
			g.setColor(p.ink);
			g.draw(t);
			g.setColor(p.error);
			line(g, 9.5, 2.5, 14, 7);
			line(g, 14, 2.5, 9.5, 7);
		});
		// file kinds (file system browser)
		GLYPHS.put("fileCode", (g, p) -> {
			page(g, p, 2.5, 1.5, 11, 13);
			g.setColor(p.accent);
			poly(g, false, 6.6, 7.2, 5, 9, 6.6, 10.8);
			poly(g, false, 9.4, 7.2, 11, 9, 9.4, 10.8);
		});
		GLYPHS.put("fileImage", (g, p) -> {
			page(g, p, 2.5, 1.5, 11, 13);
			g.setColor(p.accent);
			poly(g, false, 4.5, 12, 7, 8.5, 9, 10.5, 10, 9.5, 11.5, 12);
			dot(g, 10.2, 6.6, 1.3);
		});
		GLYPHS.put("fileArchive", (g, p) -> {
			page(g, p, 2.5, 1.5, 11, 13);
			g.setColor(p.accent);
			line(g, 8, 4.5, 8, 6);
			line(g, 8, 7, 8, 8.5);
			rrect(g, 6.7, 9.6, 2.6, 3, 1);
		});
		// data-type and symbol-tree kinds
		GLYPHS.put("array", (g, p) -> {
			g.setColor(p.ink);
			poly(g, false, 5.5, 2.5, 3, 2.5, 3, 13.5, 5.5, 13.5);
			poly(g, false, 10.5, 2.5, 13, 2.5, 13, 13.5, 10.5, 13.5);
			g.setColor(p.accent);
			dot(g, 6.4, 8, 1.2);
			dot(g, 9.6, 8, 1.2);
		});
		GLYPHS.put("union", (g, p) -> {
			g.setColor(alpha(p.accent, 0.25));
			g.fill(new Ellipse2D.Double(1.8, 4, 8, 8));
			g.fill(new Ellipse2D.Double(6.2, 4, 8, 8));
			g.setColor(p.ink);
			circle(g, 5.8, 8, 4);
			circle(g, 10.2, 8, 4);
		});
		GLYPHS.put("pointer", (g, p) -> {
			Path2D t = new Path2D.Double();
			t.moveTo(4, 2.5);
			t.lineTo(4, 12.5);
			t.lineTo(6.6, 10);
			t.lineTo(8.6, 14);
			t.lineTo(10.2, 13.2);
			t.lineTo(8.3, 9.4);
			t.lineTo(11.8, 9.2);
			t.closePath();
			g.setColor(alpha(p.accent, 0.3));
			g.fill(t);
			g.setColor(p.ink);
			g.draw(t);
		});
		GLYPHS.put("book", (g, p) -> book(g, p, false));
		GLYPHS.put("bookOpen", (g, p) -> book(g, p, true));
	}

	private static void statusBadge(Graphics2D g, Color c, String kind) {
		g.setColor(alpha(c, 0.22));
		if (kind.equals("warning")) {
			Path2D t = new Path2D.Double();
			t.moveTo(8, 1.8);
			t.lineTo(14.8, 13.8);
			t.lineTo(1.2, 13.8);
			t.closePath();
			g.fill(t);
			g.setColor(c);
			g.draw(t);
			line(g, 8, 6, 8, 9.4);
			dot(g, 8, 11.6, 1.2);
			return;
		}
		dot(g, 8, 8, 6.5);
		g.setColor(c);
		circle(g, 8, 8, 6.5);
		switch (kind) {
			case "info" -> {
				dot(g, 8, 4.9, 1.2);
				line(g, 8, 7.2, 8, 11.5);
			}
			case "error" -> {
				line(g, 8, 4.4, 8, 8.8);
				dot(g, 8, 11.3, 1.2);
			}
			case "help" -> {
				Path2D q = new Path2D.Double();
				q.moveTo(6, 6.2);
				q.curveTo(6, 3.8, 10.2, 3.8, 10.2, 6.2);
				q.curveTo(10.2, 7.8, 8, 7.8, 8, 9.4);
				g.draw(q);
				dot(g, 8, 11.6, 1.2);
			}
			case "stop" -> {
				line(g, 5.6, 5.6, 10.4, 10.4);
				line(g, 10.4, 5.6, 5.6, 10.4);
			}
			case "not" -> line(g, 3.6, 12.4, 12.4, 3.6);
		}
	}

	private static void book(Graphics2D g, Palette p, boolean open) {
		g.setColor(p.ink);
		if (open) {
			poly(g, true, 8, 4, 8, 13, 1.8, 11.8, 1.8, 2.8);
			poly(g, true, 8, 4, 8, 13, 14.2, 11.8, 14.2, 2.8);
			g.setColor(p.accent);
			line(g, 3.6, 5.5, 6.2, 6);
			line(g, 12.4, 5.5, 9.8, 6);
		}
		else {
			rrect(g, 3.5, 2.5, 9, 11, 1.5);
			g.setColor(p.accent);
			line(g, 5.8, 2.8, 5.8, 13.2);
			g.setColor(p.ink);
			line(g, 8, 6, 10.5, 6);
		}
	}

	/**
	 * PNG fallback writer:
	 * {@code java -cp <classes> generic.theme.uirefresh.icons.Glyphs src/main/resources/images/uirefresh}
	 * writes {@code <dir>/<palette>/<glyph>.png} (16 px), the files a saved theme loads. Rerun it
	 * after changing a glyph and list new files in Gui's certification.manifest.
	 */
	public static void main(String[] args) throws IOException {
		File root = new File(args[0]);
		for (Palette p : java.util.List.of(DARK, LIGHT, HIGH_CONTRAST, GRAPHITE)) {
			File dir = new File(root, p.key());
			Files.createDirectories(dir.toPath());
			for (String n : GLYPHS.keySet()) {
				ImageIO.write(render(n, p, 1), "png", new File(dir, n + ".png"));
			}
		}
		System.out.printf("glyphs=%d%n", GLYPHS.size());
	}
}
