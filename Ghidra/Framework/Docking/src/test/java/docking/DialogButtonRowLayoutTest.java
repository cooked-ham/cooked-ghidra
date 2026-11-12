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
package docking;

import static org.junit.Assert.*;

import java.awt.*;
import javax.swing.*;
import org.junit.Test;

public class DialogButtonRowLayoutTest {
	@Test
	public void testActionsRemainVisibleWhenDialogNarrowsAndThemeChanges() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			Object previous = UIManager.get(DockingChrome.UI_KEY);
			try {
				UIManager.put(DockingChrome.UI_KEY, true);
				JPanel outer = new JPanel(new BorderLayout());
				JPanel row = new JPanel(new DialogButtonRowLayout());
				for (String title : new String[] { "Apply Changes", "Cancel", "Restore Defaults" }) {
					row.add(new JButton(title));
				}
				outer.add(row, BorderLayout.SOUTH);
				for (int width : new int[] { 600, 200 }) {
					outer.setSize(width, 400);
					outer.doLayout();
					row.doLayout();
					for (Component button : row.getComponents()) {
						assertTrue(row.getBounds().toString(), button.getY() + button.getHeight() <= row.getHeight());
						assertTrue(button.getX() >= 0);
						assertTrue(button.getX() + button.getWidth() <= row.getWidth());
					}
					assertEquals(width == 600, row.getComponent(0).getY() == row.getComponent(2).getY());
				}
				UIManager.put(DockingChrome.UI_KEY, false);
				outer.setSize(600, 400);
				outer.doLayout();
				row.doLayout();
				assertEquals(row.getComponent(0).getY(), row.getComponent(2).getY());
				assertTrue(row.getComponent(0).getX() > 0);
			}
			finally {
				UIManager.put(DockingChrome.UI_KEY, previous);
			}
		});
	}
}
