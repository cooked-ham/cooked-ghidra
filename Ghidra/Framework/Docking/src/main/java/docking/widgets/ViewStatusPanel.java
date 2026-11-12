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
package docking.widgets;

import docking.DockingChrome;
import generic.theme.GThemeDefaults.Colors.Messages;

import javax.swing.*;
import java.awt.*;

/**
 * Displays noninteractive, accessible empty/loading guidance above a view's content.
 */
public class ViewStatusPanel extends JPanel {
    private final JComponent content;
    private final JPanel message = new JPanel(new GridLayout(0, 1, 0, 6));
    private final JLabel title = new JLabel("", SwingConstants.CENTER);
    private final JLabel detail = new JLabel("", SwingConstants.CENTER);
    private final boolean replaceContent;
    private boolean hasStatus;

    public ViewStatusPanel(JComponent content) {
        this(content, false);
    }

    /**
     * Replacement mode hides the stock content while modern guidance is displayed.
     */
    public ViewStatusPanel(JComponent content, boolean replaceContent) {
        super(null);
        this.content = content;
        this.replaceContent = replaceContent;
        message.setFocusable(replaceContent);
        message.setOpaque(false);
        title.putClientProperty("html.disable", true);
        detail.putClientProperty("html.disable", true);
        detail.setForeground(Messages.HINT);
        message.add(title);
        message.add(detail);
        add(message);
        add(content);
        message.setVisible(false);
    }

    /**
     * A null heading clears the guidance; content and its controls remain installed.
     */
    public void setStatus(String heading, String explanation) {
        hasStatus = heading != null;
        title.setText(heading);
        detail.setText(explanation);
        title.setToolTipText(heading);
        detail.setToolTipText(explanation);
        message.getAccessibleContext().setAccessibleName(heading);
        message.getAccessibleContext().setAccessibleDescription(explanation);
        message.setVisible(hasStatus && DockingChrome.isModern());
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return content == null ? super.getPreferredSize() : content.getPreferredSize();
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    @Override
    public void doLayout() {
        content.setBounds(0, 0, getWidth(), getHeight());
        boolean showStatus = hasStatus && DockingChrome.isModern();
        message.setVisible(showStatus);
        content.setVisible(!showStatus || !replaceContent);
        int width = Math.max(0, getWidth() - 32);
        int height = Math.clamp(getHeight() - 64, 0, message.getPreferredSize().height);
        message.setBounds(Math.min(16, getWidth()), Math.max(32, (getHeight() - height) / 2),
                width, height);
        message.doLayout();
    }
}
