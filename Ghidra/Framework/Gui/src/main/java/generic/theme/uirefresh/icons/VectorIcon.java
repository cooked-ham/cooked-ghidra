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

import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.net.URL;

import javax.swing.ImageIcon;

import resources.icons.UrlImageIcon;

/**
 * A 16 x 16 icon painted from a {@link Glyphs} vector at paint time, so it stays sharp at any
 * display scale.
 * <p>
 * It extends {@link UrlImageIcon} on purpose: Ghidra serializes an icon in a saved theme by its
 * original path, and any other icon class is written as {@code <JAVA ICON>} and lost. The path
 * points at a PNG fallback rendered from the same glyph at build time, so a theme copied through
 * the theme editor still loads a matching (bitmap) icon. Code that asks for the {@link Image}
 * (disabled-icon filters, image scaling) gets a multi-resolution image with 1x to 3x variants.
 */
public class VectorIcon extends UrlImageIcon {
	private static final double[] SCALES = { 1, 1.25, 1.5, 1.75, 2, 2.5, 3 };
	private final String glyph;
	private final Glyphs.Palette palette;

	public VectorIcon(String path, URL url, String glyph, Glyphs.Palette palette) {
		super(path, url);
		this.glyph = glyph;
		this.palette = palette;
	}

	public String getGlyph() {
		return glyph;
	}

	@Override
	protected ImageIcon createImageIcon() {
		Image[] variants = new Image[SCALES.length];
		for (int i = 0; i < SCALES.length; i++) {
			variants[i] = Glyphs.render(glyph, palette, SCALES[i]);
		}
		return new ImageIcon(new BaseMultiResolutionImage(variants), getOriginalPath());
	}

	@Override
	public int getIconWidth() {
		return 16;
	}

	@Override
	public int getIconHeight() {
		return 16;
	}

	@Override
	public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.translate(x, y);
			if (c != null && !c.isEnabled()) {
				g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.38f));
			}
			Glyphs.paint(glyph, g2, palette);
		}
		finally {
			g2.dispose();
		}
	}

	@Override
	public int hashCode() {
		return glyph.hashCode() * 31 + palette.key().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof VectorIcon v && v.glyph.equals(glyph) &&
			v.palette.key().equals(palette.key());
	}
}
