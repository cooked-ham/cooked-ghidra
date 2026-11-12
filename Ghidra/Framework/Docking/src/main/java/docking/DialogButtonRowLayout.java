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

import java.awt.*;
import java.util.Arrays;

/** Equal-width dialog actions, trailing aligned and wrapped to fit narrow modern dialogs. */
class DialogButtonRowLayout implements LayoutManager {
	private static final int GAP = 8;

	@Override
	public void addLayoutComponent(String name, Component component) {
	}

	@Override
	public void removeLayoutComponent(Component component) {
	}

	private Component[] buttons(Container parent) {
		return Arrays.stream(parent.getComponents()).filter(Component::isVisible)
			.toArray(Component[]::new);
	}

	private Dimension buttonSize(Component[] buttons) {
		Dimension size = new Dimension();
		for (Component button : buttons) {
			Dimension preferred = button.getPreferredSize();
			size.width = Math.max(size.width, preferred.width);
			size.height = Math.max(size.height, preferred.height);
		}
		return size;
	}

	@Override
	public Dimension preferredLayoutSize(Container parent) {
		Component[] buttons = buttons(parent);
		Dimension size = buttonSize(buttons);
		Insets insets = parent.getInsets();
		int columns = Math.max(1, buttons.length);
		Container outer = parent.getParent();
		int available = outer == null ? 0 : outer.getWidth() -
			outer.getInsets().left - outer.getInsets().right - insets.left - insets.right;
		if (DockingChrome.isModern() && available > 0) {
			columns = Math.clamp((available + GAP) / (size.width + GAP), 1, columns);
		}
		int rows = (buttons.length + columns - 1) / columns;
		return new Dimension(columns * size.width + (columns - 1) * GAP + insets.left + insets.right,
			rows * size.height + Math.max(0, rows - 1) * GAP + insets.top + insets.bottom);
	}

	@Override
	public Dimension minimumLayoutSize(Container parent) {
		Dimension size = buttonSize(buttons(parent));
		Insets insets = parent.getInsets();
		return new Dimension(size.width + insets.left + insets.right,
			size.height + insets.top + insets.bottom);
	}

	@Override
	public void layoutContainer(Container parent) {
		Component[] buttons = buttons(parent);
		Dimension size = buttonSize(buttons);
		Insets insets = parent.getInsets();
		int available = Math.max(0, parent.getWidth() - insets.left - insets.right);
		int width = Math.min(size.width, available);
		int columns = DockingChrome.isModern() ? Math.max(1, (available + GAP) / (width + GAP)) :
			Math.max(1, buttons.length);
		boolean leftToRight = parent.getComponentOrientation().isLeftToRight();
		for (int first = 0; first < buttons.length; first += columns) {
			int count = Math.min(columns, buttons.length - first);
			int rowWidth = count * width + (count - 1) * GAP;
			int offset = DockingChrome.isModern() ? available - rowWidth : (available - rowWidth) / 2;
			for (int i = 0; i < count; i++) {
				int x = offset + i * (width + GAP);
				if (!leftToRight) x = available - x - width;
				buttons[first + i].setBounds(insets.left + x,
					insets.top + (first / columns) * (size.height + GAP), width, size.height);
			}
		}
	}
}
