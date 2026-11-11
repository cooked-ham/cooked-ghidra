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
package generic.theme.uirefresh;

import java.util.*;

import com.formdev.flatlaf.FlatLaf;

import generic.theme.GTheme;
import generic.theme.ThemeManager;

/**
 * Owns the FlatLaf non-color defaults (corner arcs, tab type, scrollbar shape, popup and tooltip
 * geometry) for every {@link UiRefreshTheme}. This is UI-036.
 * <p>
 * Why this is a lazy map and not a plain {@code setGlobalExtraDefaults(map)} call:
 * <ul>
 * <li>{@code FlatLaf.globalExtraDefaults} is process-global and survives Ghidra's
 * {@code UIManager.getDefaults().clear()}, so a plain map leaks into every stock theme chosen
 * afterwards (reproduced with a live theme-switch probe).</li>
 * <li>Ghidra's {@code ThemeListener} fires <em>after</em> {@code installLookAndFeel()} and
 * {@code updateComponentUIs()}, so a listener that sets or clears the map is one theme too late.</li>
 * <li>FlatLaf reads the map on every LAF install through {@code Properties.putAll(map)}, and
 * {@code ApplicationThemeManager.setTheme} assigns the new active theme before that install. So a
 * map that decides what to return at read time, from the active theme, is correct in the same
 * pass, in both directions, with no listener and no LAF re-install.</li>
 * </ul>
 * Defaults that another extension already put in the global map are preserved underneath ours.
 */
final class GeometryDefaults extends AbstractMap<String, String> {

	private final Map<String, String> others;
	private static final Map<String, String> GEOMETRY = buildGeometry();

	private GeometryDefaults(Map<String, String> others) {
		this.others = others == null ? Map.of() : new HashMap<>(others);
	}

	/** Idempotent. Call from anything that is guaranteed to be constructed before the LAF install. */
	static synchronized void install() {
		if (!(FlatLaf.getGlobalExtraDefaults() instanceof GeometryDefaults)) {
			FlatLaf.setGlobalExtraDefaults(new GeometryDefaults(FlatLaf.getGlobalExtraDefaults()));
		}
	}

	private static GTheme activeTheme() {
		ThemeManager tm = ThemeManager.getInstance();
		return tm == null ? null : tm.getActiveTheme();
	}

	/**
	 * True for a UI Refresh theme class, and for any theme (e.g. a copy saved through the theme
	 * editor) that carries the boolean property {@code uirefresh.geometry = true}.
	 */
	static boolean isGeometryActive() {
		GTheme active = activeTheme();
		if (active instanceof UiRefreshTheme) {
			return true;
		}
		if (active == null) {
			return false;
		}
		if (active.containsColor(UiRefreshTheme.GEOMETRY_COLOR_ID)) {
			return true;
		}
		try {
			return Boolean.TRUE.equals(active.getResolvedProperty(UiRefreshTheme.GEOMETRY_PROPERTY));
		}
		catch (RuntimeException e) {
			return false;
		}
	}

	private Map<String, String> current() {
		if (!isGeometryActive()) {
			return others;
		}
		Map<String, String> m = new LinkedHashMap<>(others);
		m.putAll(GEOMETRY);
		if (activeTheme() instanceof UiRefreshTheme t) {
			m.putAll(t.geometryOverrides());
		}
		return m;
	}

	@Override
	public Set<Entry<String, String>> entrySet() {
		return Collections.unmodifiableMap(current()).entrySet();
	}

	@Override
	public int size() {
		return current().size();
	}

	@Override
	public String get(Object key) {
		return current().get(key);
	}

	@Override
	public boolean containsKey(Object key) {
		return current().containsKey(key);
	}

	private static Map<String, String> buildGeometry() {
		Map<String, String> d = new LinkedHashMap<>();
		d.put("Component.arc", "10");
		d.put("Button.arc", "10");
		d.put("TextComponent.arc", "8");
		d.put("CheckBox.arc", "6");
		d.put("Component.focusWidth", "1");
		d.put("ScrollBar.width", "12");
		d.put("ScrollBar.thumbArc", "999");
		d.put("ScrollBar.trackArc", "999");
		d.put("ScrollBar.thumbInsets", "2,2,2,2");
		d.put("TabbedPane.tabType", "card");
		d.put("TabbedPane.tabArc", "10");
		d.put("TabbedPane.tabSelectionArc", "10");
		d.put("TabbedPane.tabInsets", "6,12,6,12");
		d.put("List.selectionArc", "8");
		d.put("Tree.selectionArc", "8");
		d.put("Table.selectionArc", "8");
		d.put("MenuItem.selectionArc", "8");
		d.put("Menu.selectionArc", "8");
		d.put("PopupMenu.borderCornerRadius", "10");
		d.put("Popup.borderCornerRadius", "10");
		d.put("PopupMenu.roundedBorder", "true");
		d.put("Button.toolbar.spacingInsets", "2,2,2,2");
		d.put("ToolBar.hoverButtonGroupArc", "10");
		d.put("ProgressBar.arc", "999");
		d.put("TitledBorder.border", "1,1,1,1,$Component.borderColor,1,12");
		d.put("TitledBorder.titleColor", "$Label.disabledForeground");
		d.put("ToolTip.border", "4,8,4,8,$Component.borderColor,1,8");
		d.put("Popup.dropShadowPainted", "true");
		d.put("ToolTip.arc", "8");
		// UI-041 frames. NOT "ScrollPane.arc": with an arc FlatLaf resizes the viewport inside
		// layoutContainer, and ProgramTreePanel.stateChanged calls validate() on every viewport
		// change, so the two recurse until StackOverflowError (seen in the Code Browser). Scroll
		// panes keep a square 1 px frame in the subtle border color; the rounded frame is drawn
		// one level up, around each dock provider (docking.DockingChrome).
		d.put("TableHeader.bottomSeparatorColor", "$Component.borderColor");
		d.put("Table.showHorizontalLines", "false");
		d.put("Table.intercellSpacing", "0,0");
		d.put("Table.rowHeight", "22");
		d.put("Tree.rowHeight", "22");
		d.put("TableHeader.height", "26");
		return Collections.unmodifiableMap(d);
	}
}
