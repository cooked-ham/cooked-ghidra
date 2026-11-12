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
package docking.actions.dialog;

import docking.ActionContext;
import docking.DefaultActionContext;
import docking.DockingChrome;
import docking.action.DockingAction;
import docking.action.DockingActionIf;
import docking.action.ToolBarData;
import generic.test.AbstractGuiTest;
import org.junit.After;
import org.junit.Test;
import resources.Icons;

import javax.swing.*;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class RecentActionsTest extends AbstractGuiTest {

    @After
    public void cleanUp() {
        ActionsModel.clearRecentActions();
        runSwing(() -> UIManager.put(DockingChrome.UI_KEY, null));
    }

    private DockingAction action(String name) {
        DockingAction a = new DockingAction(name, "Owner") {
            @Override
            public void actionPerformed(ActionContext context) {
                // nothing
            }
        };
        a.setToolBarData(new ToolBarData(Icons.ADD_ICON, "g"));
        return a;
    }

    private List<String> recentNames(ActionsModel model) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            var entry = model.getElementAt(i);
            if (ActionGroup.RECENT.getDisplayName().equals(entry.category())) {
                names.add(entry.value().getName());
            }
        }
        return names;
    }

    private ActionsModel model(DockingAction... actions) {
        Set<DockingActionIf> local = new HashSet<>(Arrays.asList(actions));
        return new ActionsModel(local, new HashSet<>(), new DefaultActionContext());
    }

    @Test
    public void testRecentGroupIsMostRecentFirstAndModernOnly() {
        DockingAction a = action("Alpha");
        DockingAction b = action("Beta");
        DockingAction c = action("Gamma");
        ActionsModel.recordRecentAction(a);
        ActionsModel.recordRecentAction(b);
        ActionsModel.recordRecentAction(a);

        runSwing(() -> UIManager.put(DockingChrome.UI_KEY, false));
        assertEquals(List.of(), runSwing(() -> recentNames(model(a, b, c))));

        runSwing(() -> UIManager.put(DockingChrome.UI_KEY, true));
        assertEquals(List.of("Alpha", "Beta"), runSwing(() -> recentNames(model(a, b, c))));
    }

    @Test
    public void testRecentListIsBoundedAndSkipsUnavailableActions() {
        runSwing(() -> UIManager.put(DockingChrome.UI_KEY, true));
        List<DockingAction> all = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            DockingAction a = action("Action" + i);
            all.add(a);
            ActionsModel.recordRecentAction(a);
        }
        List<String> names = runSwing(() -> recentNames(model(all.toArray(new DockingAction[0]))));
        assertEquals(List.of("Action7", "Action6", "Action5", "Action4", "Action3"), names);

        List<String> missing = runSwing(() -> recentNames(model(all.get(7))));
        assertEquals(List.of("Action7"), missing);
    }
}
