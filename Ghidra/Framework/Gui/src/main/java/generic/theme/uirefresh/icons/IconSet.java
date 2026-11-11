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

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Icon;

import generic.jar.ResourceFile;
import ghidra.framework.Application;

/**
 * Maps stock icon ids to {@link Glyphs} (UI-040).
 * <p>
 * Mapping is by the stock <em>image file</em>, not by id: every id whose default value is that
 * file with no modifier (or only {@code [size(16,16)]}) gets the glyph. Ids that refer to another
 * id (e.g. {@code icon.plugin.console.clear = icon.clear}) follow automatically, so a short file
 * table covers many ids. Composite or modified stock icons keep their stock image. A few ids whose
 * stock value is a composite get a direct entry in {@link #BY_ID}.
 */
public final class IconSet {

	private static final Map<String, String> BY_FILE = new LinkedHashMap<>();
	private static final Map<String, String> BY_ID = new LinkedHashMap<>();
	private static final Pattern LINE = Pattern.compile(
		"^\\s*(icon\\.[\\w.\\-]+)\\s*=\\s*([\\w\\-./]+\\.(?:png|gif))\\s*(\\[size\\(16,\\s*16\\)\\])?\\s*(//.*)?$");

	private static void file(String glyph, String... files) {
		for (String f : files) {
			BY_FILE.put(f, glyph);
		}
	}

	static {
		file("save", "disk.png");
		file("saveAs", "disk_save_as.png");
		file("left", "left.png", "left.alternate.png", "2leftarrow.png", "smallLeftArrow.png");
		file("right", "right.png", "right.alternate.png", "2rightarrow.png", "smallRightArrow.png");
		file("up", "up.png", "arrow_up.png");
		file("down", "down.png", "arrow_down.png");
		file("refresh", "reload3.png", "view-refresh.png", "reload.png");
		file("add", "Plus2.png", "Plus.png", "add.png", "list-add.png");
		file("subtract", "list-remove.png", "Minus.png");
		file("delete", "edit-delete.png", "delete.png", "table_delete.png", "trash-empty.png");
		file("close", "x.gif", "close16.gif");
		file("closeHot", "pinkX.gif");
		file("copy", "page_white_copy.png", "edit-copy.png");
		file("paste", "page_paste.png", "edit-paste.png");
		file("cut", "edit-cut.png");
		file("search", "magnifier.png", "searchm_obj.gif", "find.png");
		file("zoomIn", "zoom_in.png");
		file("zoomOut", "zoom_out.png");
		file("filter", "filter_off.png");
		file("filterOn", "filter_on.png");
		file("configure", "exec.png", "applications-system.png", "conf.png", "settings16.gif");
		file("home", "go-home.png", "house.png");
		file("help", "help-browser.png", "redQuestionMark.png");
		file("info", "information.png", "info_small.png");
		file("warning", "warning.png", "dialog-warning.png", "software-update-urgent.png");
		file("error", "emblem-important.png", "dialog-warning_red.png");
		file("stop", "process-stop.png");
		file("notAllowed", "dialog-cancel.png", "no_small.png");
		file("folder", "closedSmallFolder.png", "closedFolder.png");
		file("folderOpen", "openSmallFolder.png", "openFolder.png");
		file("folderAdd", "folder_add.png");
		file("collapseAll", "collapse_all.png", "collapse.gif");
		file("expandAll", "expand_all.png", "expand.gif");
		file("menu", "menu16.gif", "menu.png");
		file("clear", "erase16.png");
		file("lock", "lock.png", "lock.gif", "kgpg.png");
		file("unlock", "unlock.png", "unlock.gif");
		file("check", "check.png", "tick.png", "checkmark_green.gif");
		file("run", "play.png", "media-playback-start.png", "resume.png");
		file("table", "table.png", "application_view_detail.png");
		file("sortAsc", "sortascending.png");
		file("sortDesc", "sortdescending.png");
		file("properties", "document-properties.png");
		file("rename", "textfield_rename.png");
		file("flag", "flag.png");
		file("pin", "pin.png");
		file("function", "FunctionScope.gif", "functions.gif");
		file("memory", "memory16.gif");
		file("dataTypes", "dataTypes.png");
		file("listing", "Browser.gif");
		file("console", "console.png");
		file("navigateIn", "locationIn.gif");
		file("navigateOut", "locationOut.gif");
		file("selection", "text_align_justify.png");
		file("bookmark", "B.gif", "bookmark.png");
		file("notes", "notes.gif");
		file("tree", "sitemap_color.png", "programTree.png");
		file("window", "application_xp.png");
		file("pending", "hourglass.png");
		// batch 2 (icon audit gaps)
		file("console", "monitor.png");
		file("decompiler", "decompileFunction.gif");
		file("clone", "camera-photo.png", "application_double.png");
		file("graph", "function_graph.png", "function_graph_code_flow.png");
		file("bytes", "binaryData.gif", "hexData.png");
		file("table", "dataW.gif", "table_go.png", "registerGroup.png", "field.header.png",
			"field.header.down.png");
		file("nextBlock", "NextSelectionBlock16.gif", "NextHighlightBlock16.gif");
		file("prevBlock", "PreviousSelectionBlock16.gif", "PreviousHighlightBlock16.gif");
		file("edit", "editbytes.gif", "page_edit.png", "accessories-text-editor.png");
		file("readOnly", "readOnly.png");
		file("hover", "hoverOn.gif");
		file("configure", "wrench.png");
		file("key", "key.png");
		file("scriptAdd", "script_add.png");
		file("run", "play_again.png");
		file("navigateIn", "references_to.gif");
		file("add", "layout_add.png", "vcAdd.png");
		file("up", "vcCheckIn.png");
		file("down", "vcCheckOut.png");
		file("undo", "vcUndoCheckOut.png");
		file("merge", "vcMerge.png", "Merge.png");
		file("move", "move.png");
		file("split", "verticalSplit.png");
		file("list", "text_list_bullets.png");
		file("link", "icon_link.gif");
		file("properties", "x-office-document-template.png");
		file("letterI", "I.gif");
		file("letterD", "D.gif");
		file("letterU", "U.gif");
		file("letterL", "L.gif");
		file("letterF", "F.gif");
		file("letterV", "V.png");
		// Dense views and project/dialog actions use the same optical grid as the shell.
		file("graph", "redspheregraph.png", "network-wireless-16.png", "network-wireless.png",
			"function_graph_flowchart.png", "function_graph_flowchart_left.png", "sleighGraph.png");
		file("window", "fullscreen_view.png", "view-fullscreen.png", "graph_view.png");
		file("configure", "preferences-system.png", "package_development.png");
		file("link", "brick_link.png");
		file("merge", "shape_handles.png", "shape_square_add.png");
		file("split", "shape_ungroup.png");
		file("edit", "id.png", "paintbrush.png", "palette.png");
		file("search", "magnifier.png", "magnifier--arrow.png");
		file("table", "table-e.png", "table-a.png", "table-s.png");
		file("folderOpen", "project-open.png");
		file("save", "project-save.png");
		file("home", "house.png");

		// stock values are composites (EMPTY_ICON{...}), so map the id itself
		BY_ID.put("icon.undo", "undo");
		BY_ID.put("icon.redo", "redo");
		BY_ID.put("icon.text.field.clear", "close");
		BY_ID.put("icon.widget.tabs.list", "chevronDown");
	}

	private IconSet() {
	}

	/** One audit row: id, stock file, glyph. */
	public record Mapping(String id, String stockFile, String glyph) {
	}

	/**
	 * Scans every {@code *.theme.properties} in the installation (all modules and extensions) and
	 * returns the ids to replace. Read once; cheap (plain text, no images loaded).
	 */
	public static synchronized List<Mapping> mappings() {
		if (cached != null) {
			return cached;
		}
		Map<String, Mapping> out = new TreeMap<>();
		List<ResourceFile> files;
		try {
			files = Application.findFilesByExtensionInApplication(".theme.properties");
		}
		catch (RuntimeException e) {
			files = List.of(); // headless or Application not initialized
		}
		for (ResourceFile rf : files) {
			try (BufferedReader r = new BufferedReader(
				new InputStreamReader(rf.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = r.readLine()) != null) {
					Matcher m = LINE.matcher(line);
					if (!m.matches()) {
						continue;
					}
					String fileName = m.group(2);
					String base = fileName.substring(fileName.lastIndexOf('/') + 1);
					String glyph = BY_FILE.get(base);
					if (glyph != null) {
						out.putIfAbsent(m.group(1), new Mapping(m.group(1), fileName, glyph));
					}
				}
			}
			catch (IOException e) {
				// skip unreadable file; stock icons stay
			}
		}
		BY_ID.forEach((id, glyph) -> out.put(id, new Mapping(id, "(composite)", glyph)));
		cached = List.copyOf(out.values());
		return cached;
	}

	private static List<Mapping> cached;

	/** Calls {@code setter} with a {@link VectorIcon} for every mapped id. Returns the count. */
	public static int apply(Glyphs.Palette palette, BiConsumer<String, Icon> setter) {
		Map<String, VectorIcon> byGlyph = new HashMap<>();
		int n = 0;
		for (Mapping m : mappings()) {
			VectorIcon icon = byGlyph.computeIfAbsent(m.glyph(), g -> create(g, palette));
			if (icon != null) {
				setter.accept(m.id(), icon);
				n++;
			}
		}
		return n;
	}

	private static VectorIcon create(String glyph, Glyphs.Palette palette) {
		if (!Glyphs.exists(glyph)) {
			return null;
		}
		String path = "images/uirefresh/" + palette.key() + "/" + glyph + ".png";
		URL url = IconSet.class.getClassLoader().getResource(path);
		if (url == null) {
			return null; // PNG fallbacks not built; keep stock rather than break saved themes
		}
		return new VectorIcon(path, url, glyph, palette);
	}
}
