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

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import generic.theme.BooleanPropertyValue;
import generic.theme.DiscoverableGTheme;
import generic.theme.LafType;
import generic.theme.SystemThemeIds;
import generic.theme.uirefresh.icons.Glyphs;
import generic.theme.uirefresh.icons.IconSet;

/**
 * Shared implementation of the UI Refresh themes. A concrete theme only supplies a {@link Tokens}
 * palette (UI Refresh design system). Everything else (which Ghidra id and which FlatLaf
 * {@code laf.color.*} key a token drives, typography, geometry opt-in) lives here so dark, light
 * and later high-contrast cannot drift apart.
 */
abstract class UiRefreshBaseTheme extends DiscoverableGTheme implements UiRefreshTheme {

	/** Design tokens. Same names as the design system table. */
	record Tokens(Color canvas, Color panel, Color raised, Color hover, Color selected,
			Color selectedInactive, Color border, Color textPrimary, Color textSecondary,
			Color textDisabled, Color focus, Color accent, Color accentFg, Color positive,
			Color warning, Color error, Color highlight, Color findHighlight, Color currentLine) {
	}

	static Color c(int rgb) {
		return new Color(rgb);
	}

	protected UiRefreshBaseTheme(String name, LafType laf, boolean dark, Tokens t,
			Glyphs.Palette icons) {
		super(name, laf);
		GeometryDefaults.install();
		applyMarkers(t);
		applySurfaces(t);
		applyFlatKeys(t);
		applyListing(t, dark);
		applyTypography();
		IconSet.apply(icons, this::setIcon);
	}

	// ---- opt-in markers ----------------------------------------------------------------------
	private void applyMarkers(Tokens t) {
		// geometry opt-in that survives "save as" in the theme editor (see UiRefreshTheme)
		setColor(GEOMETRY_COLOR_ID, t.canvas);
		addProperty(new BooleanPropertyValue(GEOMETRY_PROPERTY, true));
		// modern dock chrome; read by docking.DockingChrome at paint and layout time
		addProperty(new BooleanPropertyValue("Docking.modernChrome", true));
		addProperty(new BooleanPropertyValue("Docking.chromeDepth", true));
		setColor("color.border.docking.frame", t.border);
		setColor("color.bg.docking.header.accent", t.accent);
	}

	// ---- Ghidra-level ids -------------------------------------------------------------------
	private void applySurfaces(Tokens t) {
		// Custom renderers and read-only widgets resolve system ids rather than FlatLaf keys.
		setColor(SystemThemeIds.BG_CONTROL_ID, t.canvas);
		setColor(SystemThemeIds.BG_VIEW_ID, t.panel);
		setColor(SystemThemeIds.BG_TOOLTIP_ID, t.raised);
		setColor(SystemThemeIds.BG_VIEW_SELECTED_ID, t.selected);
		setColor(SystemThemeIds.BG_BORDER_ID, t.border);
		setColor(SystemThemeIds.FG_CONTROL_ID, t.textPrimary);
		setColor(SystemThemeIds.FG_VIEW_ID, t.textPrimary);
		setColor(SystemThemeIds.FG_TOOLTIP_ID, t.textPrimary);
		setColor(SystemThemeIds.FG_VIEW_SELECTED_ID, t.textPrimary);
		setColor(SystemThemeIds.FG_DISABLED_ID, t.textDisabled);
		setColor("color.bg", t.panel);
		setColor("color.fg", t.textPrimary);
		setColor("color.bg.selection", t.selected);
		setColor("color.bg.highlight", t.highlight);
		setColor("color.bg.currentline", t.currentLine);
		setColor("color.bg.find.highlight", t.findHighlight);
		setColor("color.bg.find.highlight.active", t.accent);

		// dock title bars and tabs: active = selected surface, inactive = raised, never mid grey
		setColor("color.bg.header.active", t.selected);
		setColor("color.fg.header.active", t.textPrimary);
		setColor("color.bg.header.inactive", t.raised);
		setColor("color.fg.header.inactive", t.textSecondary);
		// open-program tabs (GTabPanel): the selected tab takes the content surface it sits on;
		// active vs inactive is the underline color painted by the dock chrome, plus bold text
		setColor("color.bg.widget.tabs.selected.active", t.panel);
		setColor("color.bg.widget.tabs.selected.inactive", t.panel);
		setColor("color.bg.widget.tabs.unselected", t.canvas);
		setColor("color.bg.widget.tabs.highlighted", t.hover);
		setColor("color.fg.widget.tabs.selected.active", t.textPrimary);
		setColor("color.fg.widget.tabs.selected.inactive", t.textPrimary);
		setColor("color.fg.widget.tabs.unselected", t.textSecondary);
		setColor("color.bg.widget.tabs.list", t.raised);
		setColor("color.fg.widget.tabs.list", t.textPrimary);
		setColor("color.bg.widget.tabs.more.tabs.hover", t.hover);

		// filter fields: stock paints them bright yellow
		setColor("color.bg.filterfield", t.raised);
		setColor("color.fg.filterfield", t.textPrimary);
		setColor("color.bg.formatted.field.editing", t.raised);
		setColor("color.fg.formatted.field.editing", t.textPrimary);
		setColor("color.bg.textfield.hint.valid", t.panel);
		setColor("color.fg.textfield.hint", t.textSecondary);
		setColor("color.bg.fieldpanel", t.panel);
		setColor("color.bg.fieldpanel.selection", t.selected);
		setColor("color.bg.fieldpanel.highlight", t.highlight);
		setColor("color.bg.uneditable", t.panel);
		setColor("color.bg.table.row", t.panel);
		setColor("color.bg.table.row.alt", t.raised);
		setColor("color.bg.filechooser", t.panel);
		setColor("color.bg.filechooser.shortcut", t.raised);

		setColor("color.border.bevel.highlight", t.border);
		setColor("color.border.bevel.shadow", t.border);
		setColor("color.border.button.focused", t.focus);
		setColor("color.fg.button", t.textPrimary);

		setColor("color.fg.messages.error", t.error);
		setColor("color.fg.messages.warning", t.warning);
		setColor("color.fg.dialog.status.error", t.error);
		setColor("color.fg.dialog.status.warning", t.warning);
	}

	// ---- FlatLaf component keys (installed as laf.color.<UIDefaults key>) ---------------------
	private void applyFlatKeys(Tokens t) {
		flat("Panel.background", t.canvas);
		flat("Viewport.background", t.panel);
		flat("ScrollPane.background", t.panel);
		flat("SplitPane.background", t.canvas);
		flat("OptionPane.background", t.canvas);
		// FlatLaf ties Label.background to Panel.background; keep that, or opaque labels (the
		// status bar message) show FlatLaf's stock grey
		flat("Label.background", t.canvas);
		flat("Label.foreground", t.textPrimary);
		flat("Label.disabledForeground", t.textDisabled);
		flat("Component.borderColor", t.border);
		flat("Component.disabledBorderColor", t.border);
		flat("Component.focusColor", t.focus);
		flat("Component.focusedBorderColor", t.focus);
		flat("Component.accentColor", t.accent);
		flat("Separator.foreground", t.border);

		flat("Button.background", t.raised);
		flat("Button.foreground", t.textPrimary);
		flat("Button.hoverBackground", t.hover);
		flat("Button.pressedBackground", t.selected);
		flat("Button.borderColor", t.border);
		flat("Button.default.background", t.accent);
		flat("Button.default.foreground", t.accentFg);
		flat("Button.toolbar.hoverBackground", t.hover);
		flat("Button.toolbar.pressedBackground", t.selected);

		for (String k : new String[] { "TextField", "FormattedTextField", "PasswordField",
			"TextArea", "TextPane", "EditorPane", "ComboBox", "Spinner" }) {
			flat(k + ".background", t.raised);
			flat(k + ".foreground", t.textPrimary);
		}
		flat("TextField.caretForeground", t.textPrimary);
		flat("TextPane.inactiveBackground", t.panel);
		flat("EditorPane.inactiveBackground", t.panel);
		flat("TextComponent.selectionBackground", t.selected);
		flat("TextComponent.selectionForeground", t.textPrimary);
		flat("ComboBox.buttonBackground", t.raised);
		flat("ComboBox.buttonArrowColor", t.textSecondary);

		for (String k : new String[] { "List", "Table", "Tree" }) {
			flat(k + ".background", t.panel);
			flat(k + ".foreground", t.textPrimary);
			flat(k + ".selectionBackground", t.selected);
			flat(k + ".selectionForeground", t.textPrimary);
			flat(k + ".selectionInactiveBackground", t.selectedInactive);
			flat(k + ".selectionInactiveForeground", t.textPrimary);
		}
		flat("Table.gridColor", t.border);
		flat("TableHeader.background", t.raised);
		flat("TableHeader.foreground", t.textSecondary);
		flat("TableHeader.bottomSeparatorColor", t.border);
		flat("TableHeader.separatorColor", t.border);
		flat("Tree.hash", t.border);

		flat("MenuBar.background", t.canvas);
		flat("MenuBar.foreground", t.textPrimary);
		for (String k : new String[] { "Menu", "MenuItem", "CheckBoxMenuItem",
			"RadioButtonMenuItem", "PopupMenu" }) {
			flat(k + ".background", t.raised);
			flat(k + ".foreground", t.textPrimary);
		}
		for (String k : new String[] { "Menu", "MenuItem", "CheckBoxMenuItem",
			"RadioButtonMenuItem" }) {
			flat(k + ".selectionBackground", t.hover);
			flat(k + ".selectionForeground", t.textPrimary);
			flat(k + ".acceleratorForeground", t.textSecondary);
			flat(k + ".disabledForeground", t.textDisabled);
		}
		flat("Popup.borderColor", t.border);
		flat("PopupMenu.borderColor", t.border);
		flat("PopupMenu.separatorColor", t.border);

		flat("ToolBar.background", t.canvas);
		flat("ToolBar.borderColor", t.border);
		flat("ToolBar.foreground", t.textPrimary);

		flat("TabbedPane.background", t.canvas);
		flat("TabbedPane.foreground", t.textSecondary);
		flat("TabbedPane.selectedBackground", t.panel);
		flat("TabbedPane.selectedForeground", t.textPrimary);
		flat("TabbedPane.underlineColor", t.accent);
		flat("TabbedPane.inactiveUnderlineColor", t.border);
		flat("TabbedPane.hoverColor", t.hover);
		flat("TabbedPane.focusColor", t.selected);
		flat("TabbedPane.contentAreaColor", t.border);
		flat("TabbedPane.tabSeparatorColor", t.border);

		flat("ScrollBar.thumb", t.hover);
		flat("ScrollBar.hoverThumbColor", t.border);
		flat("ScrollBar.pressedThumbColor", t.textDisabled);
		flat("ScrollBar.track", t.canvas);

		flat("ToolTip.background", t.raised);
		flat("ToolTip.foreground", t.textPrimary);
		flat("ProgressBar.foreground", t.accent);
		flat("ProgressBar.background", t.hover);
		flat("TitledBorder.titleColor", t.textSecondary);
	}

	private void flat(String key, Color c) {
		setColor("laf.color." + key, c);
	}

	// ---- Listing semantics (only ids verified present in base.listing.theme.properties) -------
	private void applyListing(Tokens t, boolean dark) {
		Color comment = dark ? c(0x8FB6A0) : c(0x2F7D5B);
		setColor("color.fg.listing.address", t.textSecondary);
		setColor("color.fg.listing.bytes", dark ? c(0x7C8CA5) : c(0x5C6A82));
		setColor("color.fg.listing.function.name", t.accent);
		setColor("color.fg.listing.comment.auto", t.textSecondary);
		setColor("color.fg.listing.comment.eol", comment);
		setColor("color.fg.listing.comment.post", comment);
		setColor("color.fg.listing.comment.pre", comment);
		setColor("color.fg.listing.comment.plate", t.textSecondary);
	}

	// ---- Typography: first installed family wins, Java logical fonts as the floor ------------
	private void applyTypography() {
		Set<String> installed = new HashSet<>(Arrays.asList(
			GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
		String ui = firstInstalled(installed, "Segoe UI Variable Text", "Segoe UI", "SF Pro Text",
			"Inter", "SansSerif");
		String semi = firstInstalled(installed, "Segoe UI Semibold", ui);
		String mono = firstInstalled(installed, "Cascadia Mono", "JetBrains Mono", "Consolas",
			"Monospaced");

		setFont(SystemThemeIds.FONT_CONTROL_ID, new Font(ui, Font.PLAIN, 13));
		setFont(SystemThemeIds.FONT_VIEW_ID, new Font(ui, Font.PLAIN, 13));
		setFont(SystemThemeIds.FONT_MENU_ID, new Font(ui, Font.PLAIN, 13));
		setFont("font.monospaced", new Font(mono, Font.PLAIN, 13));
		setFont("font.widget.tabs", new Font(ui, Font.PLAIN, 12));
		setFont("font.widget.tabs.selected",
			new Font(semi, semi.equals(ui) ? Font.BOLD : Font.PLAIN, 12));
		setFont("font.listing.header", new Font(ui, Font.PLAIN, 12));
		setFont("font.table.header.number", new Font(semi, Font.PLAIN, 12));
	}

	private static String firstInstalled(Set<String> installed, String... families) {
		for (String f : families) {
			if (installed.contains(f)) {
				return f;
			}
		}
		return families[families.length - 1];
	}
}
