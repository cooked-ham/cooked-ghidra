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
package ghidra.app.nav;

import java.awt.*;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.ToIntBiFunction;

import javax.swing.*;

import docking.DialogComponentProvider;
import docking.widgets.searchlist.*;
import generic.theme.Gui;
import ghidra.app.events.*;
import ghidra.app.services.ProgramManager;
import ghidra.framework.model.DomainObjectListener;
import ghidra.framework.model.ProjectLocator;
import ghidra.framework.plugintool.PluginTool;
import ghidra.framework.plugintool.util.PluginEventListener;
import ghidra.program.model.listing.Program;
import ghidra.util.HelpLocation;

/** Searchable, live view of this tool's visible programs. Switching uses normal program history. */
public class ProgramSwitcherDialog extends DialogComponentProvider {

	private final PluginTool tool;
	private final ProgramManager manager;
	private final DefaultSearchListModel<Program> model = new DefaultSearchListModel<>();
	private final SearchList<Program> search;
	private final Set<Program> observed = new LinkedHashSet<>();
	private final AtomicBoolean refreshQueued = new AtomicBoolean();
	private final DomainObjectListener programListener = _ -> requestRefresh();
	private final PluginEventListener eventListener = event -> {
		if (event instanceof ProgramOpenedPluginEvent || event instanceof ProgramClosedPluginEvent ||
			event instanceof ProgramActivatedPluginEvent ||
			event instanceof ProgramVisibilityChangePluginEvent) {
			requestRefresh();
		}
	};
	private boolean disposed;
	private boolean listening;

	/** Opens a new switcher for the tool, including when it currently has no programs. */
	public static void show(PluginTool tool) {
		tool.showDialog(new ProgramSwitcherDialog(tool));
	}

	public ProgramSwitcherDialog(PluginTool tool) {
		super("Switch Program", true);
		this.tool = tool;
		manager = tool.getService(ProgramManager.class);
		search = new SearchList<>(model, (program, _) -> activate(program)) {
			@Override
			public void setInitialSelection() {
				super.setInitialSelection();
				if (getModel().getSize() > 0) {
					setSelectedItem(getModel().getElementAt(0).value());
				}
			}

			@Override
			protected BiPredicate<Program, String> createFilter(String text) {
				List<String> words = Arrays.asList(text.toLowerCase(Locale.ROOT).split("\\s+"));
				return (program, _) -> {
					String candidate = description(program).toLowerCase(Locale.ROOT);
					return words.stream().allMatch(candidate::contains);
				};
			}

			@Override
			protected ToIntBiFunction<Program, String> createRanking(String text) {
				String query = text.toLowerCase(Locale.ROOT);
				return (program, _) -> {
					String name = program.getName().toLowerCase(Locale.ROOT);
					return name.equals(query) ? 0 : name.startsWith(query) ? 1 :
						name.contains(query) ? 2 : 3;
				};
			}
		};
		search.setShowCategories(false);
		search.setDisplayNameFunction((program, _) -> description(program));
		search.setItemRenderer(new ProgramRenderer());
		search.setSelectionCallback(program -> setOkEnabled(program != null));
		search.getTextField().setName("Program search");
		search.getTextField().putClientProperty("JTextField.placeholderText", "Search programs or paths…");
		search.getTextField().getAccessibleContext().setAccessibleName("Search open programs");
		search.getTextField().getAccessibleContext().setAccessibleDescription(
			"Type to filter, use arrow keys to select, Enter to switch, Escape to cancel");
		search.getTextField().setToolTipText("Search program names, project names and paths");
		search.getAccessibleContext().setAccessibleName("Open programs");
		search.setPreferredSize(new Dimension(640, 340));
		addWorkPanel(search);
		addOKButton();
		setOkButtonText("Switch");
		addCancelButton();
		setOkEnabled(false);
		setFocusComponent(search.getTextField());
		setHelpLocation(new HelpLocation("ProgramManagerPlugin", "Go_To_Program"));
		refreshPrograms();
	}

	@Override
	protected void dialogShown() {
		listening = true;
		tool.addListenerForAllPluginEvents(eventListener);
		refreshPrograms();
	}

	@Override
	public void dispose() {
		if (disposed) {
			return;
		}
		disposed = true;
		if (listening) {
			tool.removeListenerForAllPluginEvents(eventListener);
		}
		observed.forEach(program -> program.removeListener(programListener));
		observed.clear();
		super.dispose();
		search.dispose();
	}

	private void requestRefresh() {
		if (refreshQueued.compareAndSet(false, true)) {
			SwingUtilities.invokeLater(() -> {
				refreshQueued.set(false);
				if (!disposed) {
					refreshPrograms();
				}
			});
		}
	}

	private void refreshPrograms() {
		Program selected = search.getSelectedItem();
		List<Program> programs = Arrays.stream(manager.getAllOpenPrograms())
			.filter(program -> !program.isClosed() && manager.isVisible(program)).toList();
		for (Program old : Set.copyOf(observed)) {
			if (!programs.contains(old)) {
				old.removeListener(programListener);
				observed.remove(old);
			}
		}
		if (listening) {
			for (Program program : programs) {
				if (observed.add(program)) {
					program.addListener(programListener);
				}
			}
		}
		model.clearData();
		model.add("Open programs", programs);
		model.fireDataChanged();
		search.setSelectedItem(selected != null ? selected : manager.getCurrentProgram());
		if (search.getSelectedItem() == null && model.getSize() > 0) {
			search.setSelectedItem(model.getElementAt(0).value());
		}
		setOkEnabled(search.getSelectedItem() != null);
	}

	@Override
	protected void okCallback() {
		activate(search.getSelectedItem());
	}

	private void activate(Program program) {
		// The file can close while the dialog is open or an activation is queued.
		if (program == null || program.isClosed() || !manager.isVisible(program) ||
			!Arrays.asList(manager.getAllOpenPrograms()).contains(program)) {
			refreshPrograms();
			return;
		}
		manager.setCurrentProgram(program);
		close();
	}

	private String description(Program program) {
		String state = (program == manager.getCurrentProgram() ? " — Current" : "") +
			(program.isChanged() ? " — Modified" : "");
		return program.getName() + " — " + path(program) + state;
	}

	private String path(Program program) {
		ProjectLocator project = program.getDomainFile().getProjectLocator();
		return (project == null ? "" : project.getName() + ": ") +
			program.getDomainFile().getPathname();
	}

	private class ProgramRenderer extends JPanel
			implements ListCellRenderer<SearchListEntry<Program>> {
		private final JLabel name = new JLabel();
		private final JLabel path = new JLabel();

		ProgramRenderer() {
			super(new BorderLayout(0, 2));
			name.putClientProperty("html.disable", true);
			path.putClientProperty("html.disable", true);
			Gui.registerFont(name, "font.standard.bold");
			add(name, BorderLayout.NORTH);
			add(path, BorderLayout.SOUTH);
			setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends SearchListEntry<Program>> list,
				SearchListEntry<Program> value, int index, boolean selected, boolean focused) {
			Program program = value.value();
			name.setText(program.getName() +
				(program == manager.getCurrentProgram() ? " — Current" : "") +
				(program.isChanged() ? " — Modified" : ""));
			path.setText(path(program));
			Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
			name.setForeground(foreground);
			path.setForeground(foreground);
			setBackground(selected ? list.getSelectionBackground() : list.getBackground());
			setToolTipText(description(program));
			getAccessibleContext().setAccessibleName(description(program));
			return this;
		}
	}
}
