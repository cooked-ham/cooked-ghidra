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
package ghidra.graph.viewer;

import docking.DockingChrome;

import javax.swing.*;
import java.awt.*;

/**
 * Compact, keyboard-accessible graph navigation without changing provider action bindings.
 */
class GraphNavigationBar extends JPanel {
    private final JButton zoom;

    GraphNavigationBar(Runnable out, Runnable reset, Runnable in, Runnable fit) {
        super(new FlowLayout(FlowLayout.TRAILING, 4, 4));
        setName("Graph navigation");
        setBorder(DockingChrome.statusBarBorder(4));
        add(button("−", "Zoom out", out));
        zoom = button("100%", "Reset graph zoom to 100%", reset);
        zoom.setPreferredSize(new Dimension(76, zoom.getPreferredSize().height));
        add(zoom);
        add(button("+", "Zoom in", in));
        add(button("Fit graph", "Fit the entire graph in this view", fit));
        setGraphState(false, 1);
    }

    private static JButton button(String text, String description, Runnable action) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(2, 8, 2, 8));
        button.setToolTipText(description);
        button.getAccessibleContext().setAccessibleName(description);
        button.addActionListener(_ -> action.run());
        return button;
    }

    void setGraphState(boolean available, double scale) {
        for (Component control : getComponents()) {
            control.setEnabled(available);
        }
        zoom.setText(available ? Math.round(scale * 100) + "%" : "—");
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (getParent() == null || getParent().getWidth() == 0) {
            return size;
        }
        Insets insets = getInsets();
        int available = Math.max(1, getParent().getWidth() - insets.left - insets.right - 8);
        int used = 0;
        int rows = 1;
        int height = 0;
        for (Component control : getComponents()) {
            Dimension d = control.getPreferredSize();
            if (used > 0 && used + d.width > available) {
                rows++;
                used = 0;
            }
            used += d.width + 4;
            height = Math.max(height, d.height);
        }
        size.height = insets.top + insets.bottom + rows * (height + 4) + 4;
        return size;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setVisible(DockingChrome.isModern());
    }
}
