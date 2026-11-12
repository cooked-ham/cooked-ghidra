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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import docking.DockingContextListener;
import docking.action.DockingActionIf;
import docking.actions.KeyBindingUtils;
import ghidra.app.plugin.core.symboltree.SymbolTreeService;

import ghidra.app.services.GoToService;
import ghidra.app.services.NavigationHistoryService;
import ghidra.app.services.ProgramManager;
import ghidra.framework.model.DomainObjectListener;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.program.model.symbol.Namespace;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;
import ghidra.program.util.ProgramLocation;

/**
 * Provider-local, event-driven location context. Enabled at startup with
 * {@code -Dghidra.ui.locationContext=true}; no saved tool or shortcut changes.
 * The supplier describes rendered content, which may differ from a pending navigation request.
 */
public class LocationContextPanel extends JPanel {

	/** A view's own content and an explicit, text-based linkage explanation. */
	public record Context(Program program, ProgramLocation location, String state, String detail) {
	}

	private final PluginTool tool;
	private final Navigatable navigatable;
	private final Supplier<Context> contextSupplier;
	private final JButton previous = button("←", "Previous location in history");
	private final JButton next = button("→", "Next location in history");
	private final JButton programButton = button("No program", "Switch open program");
	private final JButton namespace = button("Global", "Browse namespace symbols");
	private final JButton functionButton = button("No location", "Go to function entry");
	private final JButton addressButton = button("—", "Go to displayed address");
	private final JLabel state = new JLabel("Unavailable") {
		@Override public Dimension getPreferredSize() {
			Dimension size = super.getPreferredSize();
			return new Dimension(Math.min(140, size.width), size.height);
		}
	};
	private final JButton overflow = button("…", "More location context");
	private DockingActionIf previousAction;
	private DockingActionIf nextAction;
	private final DockingContextListener contextListener = _ -> requestRefresh();
	private final DomainObjectListener programListener = _ -> requestRefresh();
	private Program observedProgram;
	private Namespace targetNamespace;
	private boolean attached;
	private final AtomicBoolean refreshQueued = new AtomicBoolean();
	private Program targetProgram;
	private Address targetAddress;
	private Address functionEntry;

	/** Wraps content only when the development spike is enabled. */
	public static JComponent wrap(JComponent content, PluginTool tool, Navigatable navigatable,
			Supplier<Context> contextSupplier) {
		if (!Boolean.getBoolean("ghidra.ui.locationContext")) {
			return content;
		}
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(new LocationContextPanel(tool, navigatable, contextSupplier), BorderLayout.NORTH);
		wrapper.add(content, BorderLayout.CENTER);
		return wrapper;
	}

	public LocationContextPanel(PluginTool tool, Navigatable navigatable,
			Supplier<Context> contextSupplier) {
		super(new BorderLayout(6, 0));
		this.tool = tool;
		this.navigatable = navigatable;
		this.contextSupplier = contextSupplier;
		setName("Location context");
		setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 6));
		JPanel history = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
		history.add(previous);
		history.add(next);
		add(history, BorderLayout.WEST);

		JPanel crumbs = new JPanel(null) {
			@Override public Dimension getPreferredSize() {
				return new Dimension(300, addressButton.getPreferredSize().height);
			}
			@Override public void doLayout() {
				JButton[] segments = { programButton, namespace, functionButton, addressButton };
				int needed = 0;
				for (JButton segment : segments) needed += segment.getPreferredSize().width + 2;
				boolean collapsed = needed > getWidth();
				overflow.setVisible(collapsed);
				int x = collapsed ? Math.min(getWidth(), overflow.getPreferredSize().width) : 0;
				overflow.setBounds(0, 0, x, getHeight());
				for (int i = 0; i < segments.length; i++) {
					JButton segment = segments[i];
					segment.setVisible(!collapsed || i == segments.length - 1);
					if (!segment.isVisible()) continue;
					int width = Math.max(0, Math.min(segment.getPreferredSize().width, getWidth() - x));
					segment.setBounds(x, 0, width, getHeight());
					x += width + 2;
				}
			}
		};
		crumbs.setMinimumSize(new Dimension(0, 0));
		crumbs.add(overflow);
		crumbs.add(programButton);
		crumbs.add(namespace);
		crumbs.add(functionButton);
		crumbs.add(addressButton);
		add(crumbs, BorderLayout.CENTER);
		overflow.addActionListener(_ -> {
			JPopupMenu menu = new JPopupMenu();
			for (JButton segment : new JButton[] { programButton, namespace, functionButton, addressButton }) {
				JMenuItem item = new JMenuItem(segment.getText());
				item.putClientProperty("html.disable", true);
				item.setEnabled(segment.isEnabled());
				item.setToolTipText(segment.getToolTipText());
				item.addActionListener(_ -> segment.doClick());
				menu.add(item);
			}
			menu.show(overflow, 0, overflow.getHeight());
		});
		installHistoryMenu(previous, false);
		installHistoryMenu(next, true);

		state.setName("Location linkage state");
		add(state, BorderLayout.EAST);

		previous.addActionListener(_ -> navigateHistory(false));
		next.addActionListener(_ -> navigateHistory(true));
		programButton.addActionListener(_ -> showPrograms());
		namespace.addActionListener(_ -> showNamespace());
		functionButton.addActionListener(_ -> goTo(functionEntry));
		addressButton.addActionListener(_ -> goTo(targetAddress));
		addHierarchyListener(_ -> updateSubscriptions());
	}

	private void updateSubscriptions() {
		boolean showing = isShowing() && !navigatable.isDisposed();
		if (showing == attached) return;
		attached = showing;
		if (attached) {
			tool.getWindowManager().addContextListener(contextListener);
			requestRefresh();
		}
		else {
			tool.getWindowManager().removeContextListener(contextListener);
			observe(null);
		}
	}

	private void observe(Program program) {
		if (observedProgram == program) return;
		if (observedProgram != null) observedProgram.removeListener(programListener);
		observedProgram = program;
		if (observedProgram != null) observedProgram.addListener(programListener);
	}

	/** Coalesces provider and model notifications into one EDT update; never polls. */
	public void requestRefresh() {
		// Model events may arrive off the EDT. Coalesce before posting so a burst
		// occupies one queue entry, and inspect Swing/provider state only on the EDT.
		if (!refreshQueued.compareAndSet(false, true)) return;
		SwingUtilities.invokeLater(() -> {
			refreshQueued.set(false);
			if (isShowing() && !navigatable.isDisposed()) refreshContext();
		});
	}

	/** Provider-local updates also reach independent and unfocused panes. */
	public static void changed(JComponent wrapper) {
		if (wrapper == null) return;
		for (Component c : wrapper.getComponents()) {
			if (c instanceof LocationContextPanel panel) panel.requestRefresh();
		}
	}

	@Override
	public void addNotify() {
		super.addNotify();
		refreshContext();
		updateSubscriptions();
	}

	@Override
	public void removeNotify() {
		attached = false;
		tool.getWindowManager().removeContextListener(contextListener);
		observe(null);
		super.removeNotify();
	}

	/** Refresh labels and history availability without adding history entries. Runs on the EDT. */
	public void refreshContext() {
		if (navigatable.isDisposed()) {
			observe(null);
			return;
		}
		Context context = contextSupplier.get();
		targetProgram = context.program();
		if (targetProgram != null && targetProgram.isClosed()) {
			targetProgram = null;
		}
		if (attached) observe(targetProgram);
		ProgramLocation location = context.location();
		// Never combine one program's name with a stale location from a different program.
		targetAddress = targetProgram != null && location != null &&
			location.getProgram() == targetProgram ? location.getAddress() : null;
		Function function = targetAddress == null ? null :
			targetProgram.getFunctionManager().getFunctionContaining(targetAddress);
		functionEntry = function == null ? null : function.getEntryPoint();
		programButton.setText(targetProgram == null ? "No program" : targetProgram.getName());
		programButton.setToolTipText(targetProgram == null ? "No program" :
			"Program: " + targetProgram.getDomainFile().getPathname() + " (switch open program)");
		targetNamespace = targetAddress == null ? null : function == null ?
			targetProgram.getSymbolTable().getNamespace(targetAddress) : function.getParentNamespace();
		namespace.setText(targetNamespace == null ? "—" : targetNamespace.getName(true));
		namespace.setEnabled(targetNamespace != null);
		namespace.setToolTipText(namespace.getText());
		functionButton.setText(function != null ? function.getName() :
			targetAddress == null ? "No location" :
			targetProgram.getListing().getDefinedDataContaining(targetAddress) != null ?
				"Data / no function" :
			targetProgram.getListing().getInstructionContaining(targetAddress) != null ?
				"Code / no function" : "Undefined / no function");
		functionButton.setToolTipText(function == null ? functionButton.getText() :
			"Go to " + function.getName(true) + " at " + functionEntry.toString(true));
		String addressText = targetAddress == null ? "—" : targetAddress.toString(true);
		if (functionEntry != null && functionEntry.hasSameAddressSpace(targetAddress)) {
			long offset = targetAddress.subtract(functionEntry);
			addressText += offset < 0 ? " (-0x" + Long.toHexString(-offset) + ")" :
				" (+0x" + Long.toHexString(offset) + ")";
		}
		addressButton.setText(addressText);
		addressButton.setToolTipText("Displayed address: " + addressText);
		state.setText(context.state());
		state.getAccessibleContext().setAccessibleName(context.state());
		state.setToolTipText(context.detail());
		state.getAccessibleContext().setAccessibleDescription(context.detail());
		GoToService goTo = tool.getService(GoToService.class);
		programButton.setEnabled(tool.getService(ProgramManager.class) != null);
		functionButton.setEnabled(goTo != null && functionEntry != null);
		addressButton.setEnabled(goTo != null && targetAddress != null);
		updateHistoryTooltips();
		NavigationHistoryService history = tool.getService(NavigationHistoryService.class);
		Navigatable historyTarget = historyTarget();
		previous.setEnabled(history != null && historyTarget != null && history.hasPrevious(historyTarget));
		next.setEnabled(history != null && historyTarget != null && history.hasNext(historyTarget));
		for (JButton segment : new JButton[] { programButton, namespace, functionButton, addressButton }) {
			segment.getAccessibleContext().setAccessibleDescription(segment.getToolTipText());
		}
	}

	private void showMenu(JPopupMenu menu, JButton anchor) {
		JButton visibleAnchor = anchor.isShowing() ? anchor : overflow;
		menu.show(visibleAnchor, 0, visibleAnchor.getHeight());
	}

	private void installHistoryMenu(JButton button, boolean forward) {
		JPopupMenu menu = new JPopupMenu();
		button.setComponentPopupMenu(menu);
		menu.addPopupMenuListener(new PopupMenuListener() {
			@Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
				menu.removeAll();
				NavigationHistoryService history = tool.getService(NavigationHistoryService.class);
				Navigatable target = historyTarget();
				if (history == null || target == null) return;
				var locations = forward ? history.getNextLocations(target) : history.getPreviousLocations(target);
				for (LocationMemento location : locations) {
					Program program = location.getProgram();
					if (program == null || program.isClosed() || !location.isValid()) continue;
					JMenuItem item = new JMenuItem(program.getName() + " — " + location.getLocationDescription());
					item.putClientProperty("html.disable", true);
					item.setToolTipText(program.getDomainFile().getPathname());
					item.addActionListener(_ -> {
						if (program.isClosed() || target.isDisposed()) return;
						if (forward) history.next(target, location);
						else history.previous(target, location);
						navigatable.requestFocus();
						requestRefresh();
					});
					menu.add(item);
				}
				if (menu.getComponentCount() == 0) {
					JMenuItem empty = new JMenuItem("No history");
					empty.setEnabled(false);
					menu.add(empty);
				}
			}
			@Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { }
			@Override public void popupMenuCanceled(PopupMenuEvent event) { }
		});
		button.getInputMap().put(KeyStroke.getKeyStroke("alt DOWN"), "locationHistory");
		button.getActionMap().put("locationHistory", new AbstractAction() {
			@Override public void actionPerformed(java.awt.event.ActionEvent event) {
				menu.show(button, 0, button.getHeight());
			}
		});
	}

	private void updateHistoryTooltips() {
		if (previousAction == null || nextAction == null) {
			for (DockingActionIf action : tool.getAllActions()) {
				if (!action.getOwner().equals("NextPrevAddressPlugin")) continue;
				if (action.getName().equals("Previous Location in History")) previousAction = action;
				if (action.getName().equals("Next Location in History")) nextAction = action;
			}
		}
		setHistoryTooltip(previous, previousAction);
		setHistoryTooltip(next, nextAction);
	}

	private void setHistoryTooltip(JButton button, DockingActionIf action) {
		String binding = action == null || action.getKeyBinding() == null ? "" :
			" (" + KeyBindingUtils.parseKeyStroke(action.getKeyBinding()) + ")";
		String description = button.getName() + binding + "; right-click or Alt+Down for history";
		button.setToolTipText(description);
		button.getAccessibleContext().setAccessibleDescription(description);
	}

	private void showPrograms() {
		ProgramManager manager = tool.getService(ProgramManager.class);
		if (manager == null) return;
		JPopupMenu menu = new JPopupMenu();
		for (Program program : manager.getAllOpenPrograms()) {
			JMenuItem item = new JMenuItem(program.getName());
			item.putClientProperty("html.disable", true);
			item.setToolTipText(program.getDomainFile().getPathname());
			if (!navigatable.isConnected()) {
				item.setEnabled(false);
				item.setToolTipText("Independent views retain their program; switch programs in the connected Listing");
			}
			item.addActionListener(_ -> {
				if (program.isClosed()) return;
				if (navigatable.isConnected()) manager.setCurrentProgram(program);
				navigatable.requestFocus();
				requestRefresh();
			});
			menu.add(item);
		}
		showMenu(menu, programButton);
	}

	private void showNamespace() {
		Program program = targetProgram;
		if (program == null || program.isClosed() || targetNamespace == null) return;
		showNamespace(program, targetNamespace);
	}

	private void showNamespace(Program program, Namespace scope) {
		showNamespace(program, scope, 0);
	}

	private void showNamespace(Program program, Namespace scope, int offset) {
		if (program.isClosed()) return;
		JPopupMenu menu = new JPopupMenu();
		SymbolTreeService tree = tool.getService(SymbolTreeService.class);
		if (tree != null && !scope.isGlobal() && program == navigatable.getProgram()) {
			JMenuItem browse = new JMenuItem("Reveal " + scope.getName(true) + " in Symbol Tree");
			browse.addActionListener(_ -> { if (!program.isClosed()) tree.selectSymbol(scope.getSymbol()); });
			menu.add(browse);
		}
		if (!scope.isGlobal()) {
			JMenuItem parent = new JMenuItem("Parent: " + scope.getParentNamespace().getName(true));
			parent.addActionListener(_ -> showNamespace(program, scope.getParentNamespace()));
			menu.add(parent);
		}
		SymbolIterator symbols = program.getSymbolTable().getSymbols(scope);
		for (int i = 0; i < offset && symbols.hasNext(); i++) symbols.next();
		int count = 0;
		int scanned = 0;
		while (symbols.hasNext() && scanned++ < 100) {
			Symbol symbol = symbols.next();
			if (symbol.getObject() instanceof Namespace child && !(child instanceof Function)) {
				JMenuItem item = new JMenuItem(symbol.getName() + " / …");
				item.addActionListener(_ -> showNamespace(program, child));
				menu.add(item);
				count++;
				continue;
			}
			Address address = symbol.getAddress();
			if (!address.isMemoryAddress()) continue;
			JMenuItem item = new JMenuItem(symbol.getName() + " — " + address.toString(true));
			item.addActionListener(_ -> {
				GoToService service = tool.getService(GoToService.class);
				if (service != null && !program.isClosed()) {
					service.goTo(navigatable, new ProgramLocation(program, address), program);
					navigatable.requestFocus();
				}
			});
			menu.add(item);
			count++;
		}
		if (offset > 0) {
			JMenuItem previousPage = new JMenuItem("Previous 100 symbols");
			previousPage.addActionListener(_ -> showNamespace(program, scope, Math.max(0, offset - 100)));
			menu.add(previousPage);
		}
		if (symbols.hasNext()) {
			JMenuItem nextPage = new JMenuItem("Next 100 symbols");
			nextPage.addActionListener(_ -> showNamespace(program, scope, offset + 100));
			menu.add(nextPage);
		}
		if (count == 0) menu.add(new JLabel("No addressable symbols"));
		showMenu(menu, namespace);
	}

	private void navigateHistory(boolean forward) {
		NavigationHistoryService history = tool.getService(NavigationHistoryService.class);
		Navigatable historyTarget = historyTarget();
		if (history != null && historyTarget != null) {
			if (forward) {
				history.next(historyTarget);
			}
			else {
				history.previous(historyTarget);
			}
			navigatable.requestFocus();
			refreshContext();
		}
	}

	private Navigatable historyTarget() {
		// addNewLocation normalizes connected providers to the shared default navigatable,
		// but history queries and previous/next do not. Clones retain their own history.
		if (!navigatable.isConnected()) {
			return navigatable;
		}
		GoToService goTo = tool.getService(GoToService.class);
		return goTo == null ? null : goTo.getDefaultNavigatable();
	}

	private void goTo(Address address) {
		GoToService goTo = tool.getService(GoToService.class);
		if (goTo != null && targetProgram != null && !targetProgram.isClosed() && address != null) {
			goTo.goTo(navigatable, new ProgramLocation(targetProgram, address), targetProgram);
			navigatable.requestFocus();
		}
	}

	private static JButton button(String text, String description) {
		JButton button = new JButton(text);
		button.setName(description);
		button.putClientProperty("html.disable", true);
		button.putClientProperty("JButton.buttonType", "toolBarButton");
		button.setToolTipText(description);
		button.getAccessibleContext().setAccessibleName(description);
		return button;
	}
}
