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
package ghidra.util.bean;

import static org.junit.Assert.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.ButtonModel;
import javax.swing.UIManager;

import org.junit.Before;
import org.junit.Test;

import docking.ActionContext;
import docking.DefaultActionContext;
import docking.action.*;
import docking.menu.DialogToolbarButton;
import docking.test.AbstractDockingTest;
import docking.widgets.EmptyBorderButton;
import resources.ResourceManager;

public class EmptyBorderButtonTest extends AbstractDockingTest {

	private EmptyBorderButton emptyBorderButton;
	private ButtonModel buttonModel;

	@Before
	public void setUp() throws Exception {
		emptyBorderButton = new EmptyBorderButton();
		buttonModel = emptyBorderButton.getModel();
	}

	@Test
	public void testButtonBorderOnRollover() {
		assertEquals(emptyBorderButton.getBorder(), EmptyBorderButton.NO_BUTTON_BORDER);

		buttonModel.setRollover(true);
		setRollover(true);
		assertEquals(emptyBorderButton.getBorder(), EmptyBorderButton.RAISED_BUTTON_BORDER);

		setRollover(false);
		assertEquals(emptyBorderButton.getBorder(), EmptyBorderButton.NO_BUTTON_BORDER);

		// no changes when disabled
		emptyBorderButton.setEnabled(false);
		setRollover(true);
		assertEquals(emptyBorderButton.getBorder(), EmptyBorderButton.NO_BUTTON_BORDER);
	}

	@Test
	public void testButtonBorderOnPress() {
		assertEquals(emptyBorderButton.getBorder(), EmptyBorderButton.NO_BUTTON_BORDER);

		// just pressing the button does not change the border...
		setButtonPressed(true);
		assertEquals(emptyBorderButton.getBorder(), EmptyBorderButton.NO_BUTTON_BORDER);

		// ...it must also be armed (or under a rollover)
		setButtonArmed(true);
		assertEquals(emptyBorderButton.getBorder(), EmptyBorderButton.LOWERED_BUTTON_BORDER);

		setButtonPressed(false);
		assertEquals(emptyBorderButton.getBorder(), EmptyBorderButton.NO_BUTTON_BORDER);

		// no changes when disabled
		emptyBorderButton.setEnabled(false);
		setButtonPressed(true);
		assertEquals(emptyBorderButton.getBorder(), EmptyBorderButton.NO_BUTTON_BORDER);
	}

	@Test
	public void testModernButtonStatesAndStockRestore() {
		runSwing(() -> {
			Object oldChrome = UIManager.get("Docking.modernChrome");
			Object oldHover = UIManager.get("Button.toolbar.hoverBackground");
			Object oldPressed = UIManager.get("Button.toolbar.pressedBackground");
			try {
				UIManager.put("Docking.modernChrome", false);
				Dimension stockSize = emptyBorderButton.getPreferredSize();
				UIManager.put("Docking.modernChrome", true);
				UIManager.put("Button.toolbar.hoverBackground", Color.BLUE);
				UIManager.put("Button.toolbar.pressedBackground", Color.GREEN);
				assertFalse(emptyBorderButton.isOpaque());
				assertTrue(emptyBorderButton.getPreferredSize().height >= 28);
				emptyBorderButton.setSize(emptyBorderButton.getPreferredSize());
				buttonModel.setRollover(true);
				assertEquals(Color.BLUE.getRGB(), paintButtonCenter());
				buttonModel.setArmed(true);
				buttonModel.setPressed(true);
				assertEquals(Color.GREEN.getRGB(), paintButtonCenter());
				buttonModel.setArmed(false);
				buttonModel.setPressed(false);
				buttonModel.setRollover(false);
				emptyBorderButton.setSelected(true);
				assertEquals(Color.GREEN.getRGB(), paintButtonCenter());
				emptyBorderButton.setEnabled(false);
				assertEquals(0, paintButtonCenter()); // no stale hover or pressed fill
				UIManager.put("Docking.modernChrome", false);
				emptyBorderButton.updateUI();
				assertTrue(emptyBorderButton.isOpaque());
				assertEquals(stockSize, emptyBorderButton.getPreferredSize());
			}
			finally {
				UIManager.put("Docking.modernChrome", oldChrome);
				UIManager.put("Button.toolbar.hoverBackground", oldHover);
				UIManager.put("Button.toolbar.pressedBackground", oldPressed);
			}
		});
	}

	private int paintButtonCenter() {
		int width = emptyBorderButton.getWidth();
		int height = emptyBorderButton.getHeight();
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try {
			emptyBorderButton.paint(g);
		}
		finally {
			g.dispose();
		}
		return image.getRGB(width / 2, height / 2);
	}

	private void setButtonArmed(final boolean armed) {
		runSwing(() -> buttonModel.setArmed(armed));
	}

	private void setButtonPressed(final boolean pressed) {
		runSwing(() -> buttonModel.setPressed(pressed));
	}

	private void setRollover(final boolean isRollover) {
		runSwing(() -> buttonModel.setRollover(isRollover));
	}

//	public void testButtonBorderOnModalDialog() throws InterruptedException {
//		assertEquals(emptyBorderButton.getBorder(), EmptyBorderButton.NO_BUTTON_BORDER);
//
//		final CountDownLatch done = new CountDownLatch(1);
//		emptyBorderButton.addActionListener(new ActionListener() {
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				showModalDialog();
//				done.countDown();
//			}
//		});
//
//		runSwing(new Runnable() {
//			@Override
//			public void run() {
//				emptyBorderButton.doClick();
//			}
//		});
//
//		done.await(DEFAULT_WINDOW_TIMEOUT, TimeUnit.MILLISECONDS);
//		waitForSwing();
//		Thread.sleep(1000);
//
//		assertEquals(emptyBorderButton.getBorder(), EmptyBorderButton.NO_BUTTON_BORDER);
//
//		closeDialogs();
//	}
//
//	private void showModalDialog() {
//		executeOnSwingWithoutBlocking(new Runnable() {
//			@Override
//			public void run() {
//				JOptionPane.showConfirmDialog(null, "Test");
//			}
//		});
//	}

	@Test
	public void testButtonEnablementFromAction() {
		DockingAction action = new DockingAction("name", "owner") {
			@Override
			public void actionPerformed(ActionContext context) {
				// do nothing
			}

			@Override
			public boolean isEnabledForContext(ActionContext context) {
				return true;
			}
		};
		ActionContextProvider contextProvider =
			e -> new DefaultActionContext(null, e.getSource(), e.getComponent());
		action.setToolBarData(new ToolBarData(ResourceManager.getDefaultIcon()));
		action.setEnabled(false);

		DialogToolbarButton button = new DialogToolbarButton(action, contextProvider);
		assertTrue(!button.isEnabled());

		action.setEnabled(true);
		assertTrue(button.isEnabled());

		button = new DialogToolbarButton(action, contextProvider);
		assertTrue(button.isEnabled());

		action.setEnabled(false);
		assertTrue(!button.isEnabled());
	}

	// testBorderDragging() // is this possible?

//	private void closeDialogs() {
//		closeAllWindows();
//	}
}
