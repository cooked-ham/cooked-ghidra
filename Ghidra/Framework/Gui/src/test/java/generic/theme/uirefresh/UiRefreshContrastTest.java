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

import static org.junit.Assert.*;

import java.awt.Color;

import org.junit.Test;

import generic.theme.GTheme;

public class UiRefreshContrastTest {
	@Test
	public void testSyntaxAndGraphTextContrast() {
		for (GTheme theme : new GTheme[] { new UiRefreshDarkTheme(), new UiRefreshLightTheme(),
			new UiRefreshHighContrastTheme(), new UiRefreshCompactDarkTheme(),
			new UiRefreshCompactLightTheme(), new GraphiteTheme() }) {
			Color background = theme.getColor("color.bg.decompiler").get(theme);
			for (String role : new String[] { "keyword", "comment", "error", "variable", "constant",
				"type", "parameter", "global", "special", "external.function" }) {
				Color foreground = theme.getColor("color.fg.decompiler." + role).get(theme);
				assertContrast(theme, role, foreground, background);
			}
			Color label = theme.getColor("color.fg.plugin.functiongraph.label.picked").get(theme);
			for (String role : new String[] { "entry", "exit", "group", "picked" }) {
				background = theme.getColor("color.bg.plugin.functiongraph.vertex." + role).get(theme);
				assertContrast(theme, "graph " + role, label, background);
			}
		}
	}

	private static void assertContrast(GTheme theme, String role, Color fg, Color bg) {
		double first = luminance(fg);
		double second = luminance(bg);
		double ratio = (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
		assertTrue(theme.getName() + " " + role + " contrast=" + ratio, ratio >= 4.5);
	}

	private static double luminance(Color color) {
		return .2126 * linear(color.getRed()) + .7152 * linear(color.getGreen()) +
			.0722 * linear(color.getBlue());
	}

	private static double linear(int channel) {
		double value = channel / 255.0;
		return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
	}
}
