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

/** UI Refresh Dark. Tokens from the UI Refresh design system (ghidra-ui-refresh docs/03). */
public class UiRefreshDarkTheme extends UiRefreshBaseTheme {
	private static final Tokens T = new Tokens(
		c(0x101318), // canvas
		c(0x171B22), // panel
		c(0x1D232C), // raised
		c(0x29313E), // hover
		c(0x253E5B), // selected
		c(0x222C3A), // selected, window not focused
		c(0x343C48), // border.subtle
		c(0xE9EDF4), // text.primary
		c(0xADB7C6), // text.secondary
		c(0x6E7888), // text.disabled
		c(0x82B1FF), // focus.ring
		c(0x7FA7F2), // accent
		c(0x0B1220), // text on accent
		c(0x91CBA6), c(0xE7BF79), c(0xE48D94), // positive, warning, error
		c(0x3B3A22), // listing highlight
		c(0x5A4A1E), // find highlight
		c(0x1D232C)); // current line

	public UiRefreshDarkTheme() {
		this("UI Refresh Dark");
	}

	protected UiRefreshDarkTheme(String name) {
		super(name, LafType.FLAT_DARK, true, T, Glyphs.DARK);
	}
}
