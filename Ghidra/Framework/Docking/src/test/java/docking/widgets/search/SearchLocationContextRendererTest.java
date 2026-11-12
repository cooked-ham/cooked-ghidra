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
package docking.widgets.search;

import static org.junit.Assert.*;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import org.junit.Test;

import docking.DockingChrome;
import docking.widgets.table.GTableCellRenderingData;
import generic.test.AbstractGuiTest;
import ghidra.docking.settings.SettingsImpl;

public class SearchLocationContextRendererTest extends AbstractGuiTest {

	@Test
	public void testNarrowSelectedContextAndThemeSwitch() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Object previous = UIManager.get(DockingChrome.UI_KEY);
			try {
				JTable table = new JTable(new DefaultTableModel(new Object[] { "Context" }, 1));
				table.setSelectionForeground(Color.WHITE);
				table.setSelectionBackground(Color.BLACK);
				SearchLocationContext context = new SearchLocationContextBuilder()
					.append("namespace::function ").appendMatch("needle")
					.append(" with additional context that must be clipped").build();
				SearchLocationContextRenderer renderer = new SearchLocationContextRenderer() {
					@Override
					protected SearchLocationContext getContext(GTableCellRenderingData data) {
						return (SearchLocationContext) data.getValue();
					}
				};
				GTableCellRenderingData data = new GTableCellRenderingData(table, 0, new SettingsImpl());
				data.setRowData(0, context);
				data.setCellData(context, 0, true, true);
				for (boolean modern : new boolean[] { true, false, true }) {
					UIManager.put(DockingChrome.UI_KEY, modern);
					for (int width : new int[] { 600, 100, 20, 4, 0 }) {
						JPanel cell = (JPanel) renderer.getTableCellRendererComponent(data);
						cell.setSize(width, 22);
						cell.doLayout();
						assertEquals(context.getPlainText(false), cell.getToolTipText());
						for (Component child : cell.getComponents()) {
							assertTrue(child.getWidth() >= 0);
							assertTrue(child.getHeight() >= 0);
							if (child.isVisible() && width >= 20) {
								assertTrue(child.getX() + child.getWidth() <= width);
							}
						}
						JLabel marker = (JLabel) cell.getComponent(1);
						assertEquals(table.getSelectionForeground(), marker.getForeground());
						assertEquals(renderer.getFont(), marker.getFont());
						Insets insets = cell.getInsets();
						assertEquals(width > insets.left + insets.right && width < 600,
							marker.isVisible());
					}
					data.setCellData(null, 0, false, false);
					JPanel empty = (JPanel) renderer.getTableCellRendererComponent(data);
					empty.setSize(100, 22);
					empty.doLayout();
					assertFalse(empty.getComponent(1).isVisible());
					data.setCellData(context, 0, true, true);
				}
			}
			finally {
				UIManager.put(DockingChrome.UI_KEY, previous);
			}
		});
	}
}
