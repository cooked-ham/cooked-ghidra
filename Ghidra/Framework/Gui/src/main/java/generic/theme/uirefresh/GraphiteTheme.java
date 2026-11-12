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

import generic.theme.LafType;
import generic.theme.uirefresh.icons.Glyphs;

/** Separate, opt-in visual reference for the graphite identity. Does not migrate preferences. */
public class GraphiteTheme extends UiRefreshBaseTheme {

	private static final Tokens TOKENS = new Tokens(
		c(0x171615), // application canvas
		c(0x201E1C), // quiet code and data surface
		c(0x2B2723), // raised controls
		c(0x3B332B), // hover
		c(0x51402B), // selection, distinct from the amber action fill
		c(0x38322C), // inactive selection
		c(0x88796A), // control outlines
		c(0xF2EDE5), // primary text
		c(0xC3B7A8), // secondary text
		c(0x968A7C), // disabled text
		c(0x8ECAD4), // keyboard focus, distinct from navigation and warning
		c(0xE8AD63), // amber navigation and primary action
		c(0x21180E), // text on amber
		c(0x9ACAAB), c(0xF1D982), c(0xF09B94), // success, warning, error
		c(0x3E3828), // analysis highlight
		c(0x5B4827), // search highlight
		c(0x292521)); // current line

	public GraphiteTheme() {
		super("Graphite Preview", LafType.FLAT_DARK, true, TOKENS, Glyphs.GRAPHITE);
		setColor("color.graphite.copper", c(0xCB9474));
		setColor("color.graphite.chrome.top", c(0x44372D));
		setColor("color.graphite.chrome.bottom", c(0x2B2723));
		setColor("color.graphite.chrome.highlight", c(0x77604B));
		setColor("color.graphite.chrome.shadow", c(0x100F0E));
		addProperty(new generic.theme.BooleanPropertyValue("Docking.graphiteDepth", true));
	}
}
