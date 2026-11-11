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

import generic.theme.LafType;
import generic.theme.BooleanPropertyValue;
import generic.theme.uirefresh.icons.Glyphs;

/**
 * UI Refresh High Contrast. Near-black surfaces, white text (7:1 or better), a yellow focus ring
 * and visible 1 px borders. Color is never the only cue: the focus ring is thicker (geometry
 * override below) and the active dock header carries an accent edge (dock chrome patch).
 */
public class UiRefreshHighContrastTheme extends UiRefreshBaseTheme {
	private static final Tokens T = new Tokens(
		c(0x000000), // canvas
		c(0x0A0A0A), // panel
		c(0x161616), // raised
		c(0x2E2E2E), // hover
		c(0x00407F), // selected
		c(0x2A3340), // selected, window not focused
		c(0x9A9A9A), // border (3:1 against panel, a real boundary here)
		c(0xFFFFFF), // text.primary
		c(0xE6E6E6), // text.secondary
		c(0xA6A6A6), // text.disabled
		c(0xFFD400), // focus.ring
		c(0x8CC8FF), // accent
		c(0x000000), // text on accent
		c(0x7CF29A), c(0xFFD27A), c(0xFF9C9C), // positive, warning, error
		c(0x3D3D00), // listing highlight
		c(0x5C4600), // find highlight
		c(0x1C1C1C)); // current line

	public UiRefreshHighContrastTheme() {
		super("UI Refresh High Contrast", LafType.FLAT_DARK, true, T, Glyphs.HIGH_CONTRAST);
		addProperty(new BooleanPropertyValue("Docking.chromeDepth", false));
	}

	@Override
	public Map<String, String> geometryOverrides() {
		return Map.of(
			"Component.focusWidth", "2",
			"Component.innerFocusWidth", "1",
			"Button.innerFocusWidth", "1",
			"ScrollBar.width", "14",
			"Table.showHorizontalLines", "true");
	}
}
