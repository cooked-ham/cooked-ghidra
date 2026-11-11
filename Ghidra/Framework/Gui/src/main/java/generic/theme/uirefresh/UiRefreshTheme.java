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

import java.util.Map;

/**
 * Marker for themes that want the UI Refresh geometry (see {@link GeometryDefaults}).
 * <p>
 * A theme saved or copied through Ghidra's theme editor becomes a plain file theme and no longer
 * implements this interface. It still opts in through a marker color id,
 * {@value #GEOMETRY_COLOR_ID}, which every UI Refresh theme sets and which the editor writes into
 * the saved file like any other color. (A {@code [laf.boolean]} property would be the natural
 * carrier, but 12.2's {@code ThemeWriter} does not write properties, so it would be lost on save.)
 * A hand-written theme file can opt in with either line:
 * <pre>
 * color.uirefresh.geometry = #000000
 * [laf.boolean]uirefresh.geometry = true
 * </pre>
 */
interface UiRefreshTheme {
	String GEOMETRY_PROPERTY = "uirefresh.geometry";
	String GEOMETRY_COLOR_ID = "color.uirefresh.geometry";

	/** Theme-specific FlatLaf keys layered over the shared geometry (e.g. thicker focus ring). */
	default Map<String, String> geometryOverrides() {
		return Map.of();
	}
}
