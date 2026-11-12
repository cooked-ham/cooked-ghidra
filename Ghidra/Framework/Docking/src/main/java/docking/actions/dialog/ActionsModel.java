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

import static docking.actions.dialog.ActionGroup.*;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import docking.*;
import docking.action.DockingActionIf;
import docking.action.MenuData;
import docking.widgets.searchlist.DefaultSearchListModel;

/** 
 * Model for the SearchList used by the {@link ActionChooserDialog}.  This model is constructed
 * with two sets of actions; local and global. The local actions are actions that are specific to
 * the currently focused {@link ComponentProvider} or {@link DialogComponentProvider}. Global 
 * actions are actions that are added at the tool level and are not specific to a ComponentProvider
 * or DialogComponentProvider.
 * <P>
 * The model supports the concept of a {@link ActionDisplayLevel}. The display level determines
 * which combination of local and global actions to display and takes into account if they are
 * valid for the current context, are enabled for the current context and, for popups, the value of
 * the "addToPopup" value. Each higher display level is less restrictive and adds more actions in
 * the displayed list. See the {@link ActionDisplayLevel} for a description of which actions are
 * displayed for each level
 * 
 */
public class ActionsModel extends DefaultSearchListModel<DockingActionIf> {
	private final Set<DockingActionIf> localActions;
	private final Set<DockingActionIf> globalActions;
	private ActionDisplayLevel displayLevel = ActionDisplayLevel.LOCAL;
	private ActionContext context;
	private final Comparator<DockingActionIf> nameComparator = new ActionNameComparator();
	private final Comparator<DockingActionIf> menuPathComparator = new ActionMenuPathComparator();
	private final Comparator<DockingActionIf> popupPathComparator = new ActionPopupPathComparator();

	private static final int MAX_RECENT = 5;
	private static final LinkedList<String> recentActionKeys = new LinkedList<>();

	ActionsModel(Set<DockingActionIf> localActions, Set<DockingActionIf> globalActions,
			ActionContext context) {
		this.context = context;
		this.localActions = localActions;
		this.globalActions = globalActions;
		populateActions();
	}

	/**
	 * Sets the display level for the actions dialog. Each higher level includes more actions
	 * in the displayed list of actions.
	 * @param level the {@link ActionDisplayLevel}
	 */
	public void setDisplayLevel(ActionDisplayLevel level) {
		this.displayLevel = level;
		populateActions();
		fireDataChanged();
	}

	/**
	 * Returns the current {@link ActionDisplayLevel} of the dialog.
	 * @return the current display level of the dialog
	 */
	public ActionDisplayLevel getActionDisplayLevel() {
		return displayLevel;
	}

	@Override
	public void dispose() {
		localActions.clear();
		globalActions.clear();
		context = null;
	}

	public boolean isDisposed() {
		return context == null;
	}

	private static String actionKey(DockingActionIf a) {
		return a.getOwner() + "::" + a.getName();
	}

	/**
	 * Remembers that the given action was chosen so that it can be offered first the next time
	 * the chooser is shown (modern chrome only).
	 * @param action the action that was chosen
	 */
	public static void recordRecentAction(DockingActionIf action) {
		synchronized (recentActionKeys) {
			String key = actionKey(action);
			recentActionKeys.remove(key);
			recentActionKeys.addFirst(key);
			while (recentActionKeys.size() > MAX_RECENT) {
				recentActionKeys.removeLast();
			}
		}
	}

	/** Clears the remembered recent actions. */
	public static void clearRecentActions() {
		synchronized (recentActionKeys) {
			recentActionKeys.clear();
		}
	}

	private void addRecentActions() {
		if (!DockingChrome.isModern()) {
			return;
		}
		List<String> keys;
		synchronized (recentActionKeys) {
			keys = new ArrayList<>(recentActionKeys);
		}
		List<DockingActionIf> candidates = new ArrayList<>(localActions);
		candidates.addAll(globalActions);
		List<DockingActionIf> recent = new ArrayList<>();
		for (String key : keys) {
			for (DockingActionIf a : candidates) {
				if (actionKey(a).equals(key) && a.isValidContext(context) &&
					a.isEnabledForContext(context)) {
					recent.add(a);
					break;
				}
			}
		}
		if (!recent.isEmpty()) {
			add(RECENT.getDisplayName(), recent);
		}
	}

	private void populateActions() {
		clearData();
		addRecentActions();
		switch (displayLevel) {
			case LOCAL:
				addLocalActions(LOCAL_TOOLBAR, this::isValidToolbar);
				addLocalActions(LOCAL_MENU, this::isValidMenu);
				addLocalActions(POPUP, this::isValidPopup);
				addGlobalActions(POPUP, this::isValidPopup);
				addLocalActions(KEYBINDING_ONLY, this::isValidKeybindingOnly);
				addGlobalActions(KEYBINDING_ONLY, this::isValidKeybindingOnly);
				break;

			case GLOBAL:
				addLocalActions(LOCAL_TOOLBAR, this::isValidToolbar);
				addGlobalActions(GLOBAL_TOOLBAR, this::isValidToolbar);
				addLocalActions(LOCAL_MENU, this::isValidMenu);
				addGlobalActions(GLOBAL_MENU, this::isValidMenu);
				addLocalActions(POPUP, this::isValidPopup);
				addGlobalActions(POPUP, this::isValidPopup);
				addLocalActions(KEYBINDING_ONLY, this::isValidKeybindingOnly);
				addGlobalActions(KEYBINDING_ONLY, this::isValidKeybindingOnly);
				break;

			case ALL:
				addLocalActions(LOCAL_TOOLBAR, this::isToolbar);
				addGlobalActions(GLOBAL_TOOLBAR, this::isToolbar);
				addLocalActions(LOCAL_MENU, this::isMenu);
				addGlobalActions(GLOBAL_MENU, this::isMenu);
				addLocalActions(POPUP, this::isPopup);
				addGlobalActions(POPUP, this::isPopup);
				addLocalActions(KEYBINDING_ONLY, this::isKeybindingOnly);
				addGlobalActions(KEYBINDING_ONLY, this::isKeybindingOnly);
				break;
		}
	}

	ActionContext getContext() {
		return context;
	}

	private boolean isToolbar(DockingActionIf a) {
		return a.getToolBarData() != null;
	}

	private boolean isValidToolbar(DockingActionIf a) {
		return isToolbar(a) && a.isValidContext(context);
	}

	private boolean isMenu(DockingActionIf a) {
		return a.getMenuBarData() != null;
	}

	private boolean isValidMenu(DockingActionIf a) {
		return isMenu(a) && a.isValidContext(context);
	}

	private boolean isPopup(DockingActionIf a) {
		return a.getPopupMenuData() != null;
	}

	private boolean isValidPopup(DockingActionIf a) {
		return isPopup(a) && a.isValidContext(context) && a.isAddToPopup(context);
	}

	private boolean isKeybindingOnly(DockingActionIf a) {
		return a.getToolBarData() == null && a.getMenuBarData() == null &&
			a.getPopupMenuData() == null;
	}

	private boolean isValidKeybindingOnly(DockingActionIf a) {
		return isKeybindingOnly(a) && a.isValidContext(context) && a.isEnabledForContext(context);
	}

	private void addLocalActions(ActionGroup actionGroup, Predicate<DockingActionIf> filter) {
		List<DockingActionIf> actions = localActions.stream()
				.filter(filter)
				.sorted(getSorter(actionGroup))
				.collect(Collectors.toCollection(ArrayList::new));
		add(actionGroup.getDisplayName(), actions);
	}

	private void addGlobalActions(ActionGroup actionGroup, Predicate<DockingActionIf> filter) {
		List<DockingActionIf> actions = globalActions.stream()
				.filter(filter)
				.sorted(getSorter(actionGroup))
				.collect(Collectors.toCollection(ArrayList::new));
		add(actionGroup.getDisplayName(), actions);
	}

	/**
	 * Returns the appropriate action sorter for the given ActionGroup category. Actions with
	 * a menu path (menu and popup) use the menu path to sort the actions. All others use
	 * the action's name for sorting.
	 * @param actionGroup the type
	 * @return the comparator to use for sorting actions within their category
	 */
	private Comparator<? super DockingActionIf> getSorter(ActionGroup actionGroup) {
		return switch (actionGroup) {
			case GLOBAL_MENU, LOCAL_MENU -> menuPathComparator;
			case POPUP -> popupPathComparator;
			default -> nameComparator;
		};
	}

	private static class ActionNameComparator implements Comparator<DockingActionIf> {
		@Override
		public int compare(DockingActionIf a1, DockingActionIf a2) {
			return a1.getName().compareTo(a2.getName());
		}
	}

	private static class ActionMenuPathComparator implements Comparator<DockingActionIf> {
		@Override
		public int compare(DockingActionIf a1, DockingActionIf a2) {
			MenuData menuData1 = a1.getMenuBarData();
			MenuData menuData2 = a2.getMenuBarData();
			String path1 = menuData1 != null ? menuData1.getMenuPathAsString() : "";
			String path2 = menuData2 != null ? menuData2.getMenuPathAsString() : "";

			return path1.compareTo(path2);
		}
	}

	private static class ActionPopupPathComparator implements Comparator<DockingActionIf> {
		@Override
		public int compare(DockingActionIf a1, DockingActionIf a2) {
			MenuData menuData1 = a1.getPopupMenuData();
			MenuData menuData2 = a2.getPopupMenuData();
			String path1 = menuData1 != null ? menuData1.getMenuPathAsString() : "";
			String path2 = menuData2 != null ? menuData2.getMenuPathAsString() : "";

			return path1.compareTo(path2);
		}
	}

}
