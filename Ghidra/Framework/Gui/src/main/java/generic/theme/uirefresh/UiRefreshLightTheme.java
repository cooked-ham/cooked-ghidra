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

/** UI Refresh Light. Same structure as the dark theme, not an inverted copy. */
public class UiRefreshLightTheme extends UiRefreshBaseTheme {
	private static final Tokens T = new Tokens(
		c(0xF3F5F8), // canvas
		c(0xFFFFFF), // panel
		c(0xEEF1F6), // raised
		c(0xE1E7F0), // hover
		c(0xD2E3FC), // selected
		c(0xE3EAF4), // selected, window not focused
		c(0xCBD3DE), // border.subtle
		c(0x18202D), // text.primary
		c(0x566274), // text.secondary
		c(0x9AA4B3), // text.disabled
		c(0x2F6FE4), // focus.ring
		c(0x2862D0), // accent
		c(0xFFFFFF), // text on accent
		c(0x2F7D5B), c(0x9A6A12), c(0xB3363F), // positive, warning, error
		c(0xFFF1B8), // listing highlight
		c(0xFFE08A), // find highlight
		c(0xEAF1FB)); // current line

	public UiRefreshLightTheme() {
		super("UI Refresh Light", LafType.FLAT_LIGHT, false, T, Glyphs.LIGHT);
	}
}
