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
import java.util.function.Supplier;

import javax.swing.*;
import docking.DockingContextListener;
import ghidra.app.services.ProgramManager;
import ghidra.framework.model.DomainObjectListener;
import ghidra.program.model.symbol.Namespace;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolIterator;
import ghidra.app.plugin.core.symboltree.SymbolTreeService;

import ghidra.app.services.GoToService;
import ghidra.app.services.NavigationHistoryService;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
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
	private final JLabel state = new JLabel("Unavailable");
	private final DockingContextListener contextListener = _ -> requestRefresh();
	private final DomainObjectListener programListener = _ -> requestRefresh();
	private Program observedProgram;
	private Namespace targetNamespace;
	private boolean attached;
	private boolean refreshQueued;
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

		JPanel crumbs = new JPanel(new FlowLayout(FlowLayout.LEADING, 2, 0));
		crumbs.add(programButton);
		crumbs.add(new JLabel("/"));
		crumbs.add(namespace);
		crumbs.add(new JLabel("/"));
		crumbs.add(functionButton);
		crumbs.add(new JLabel("/"));
		crumbs.add(addressButton);
		namespace.putClientProperty("html.disable", true);
		JScrollPane scroller = new JScrollPane(crumbs, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroller.setBorder(BorderFactory.createEmptyBorder());
		scroller.setMinimumSize(new Dimension(0, 0));
		// Reserve scrollbar height so long names do not obscure the breadcrumb buttons.
		scroller.setPreferredSize(new Dimension(300,
			crumbs.getPreferredSize().height + scroller.getHorizontalScrollBar().getPreferredSize().height));
		add(scroller, BorderLayout.CENTER);
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
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::requestRefresh);
			return;
		}
		if (refreshQueued || !isShowing() || navigatable.isDisposed()) return;
		refreshQueued = true;
		SwingUtilities.invokeLater(() -> {
			refreshQueued = false;
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
		state.setToolTipText(context.detail());
		state.getAccessibleContext().setAccessibleDescription(context.detail());
		GoToService goTo = tool.getService(GoToService.class);
		programButton.setEnabled(tool.getService(ProgramManager.class) != null);
		functionButton.setEnabled(goTo != null && functionEntry != null);
		addressButton.setEnabled(goTo != null && targetAddress != null);
		NavigationHistoryService history = tool.getService(NavigationHistoryService.class);
		Navigatable historyTarget = historyTarget();
		previous.setEnabled(history != null && historyTarget != null && history.hasPrevious(historyTarget));
		next.setEnabled(history != null && historyTarget != null && history.hasNext(historyTarget));
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
				requestRefresh();
			});
			menu.add(item);
		}
		menu.show(programButton, 0, programButton.getHeight());
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
		menu.show(namespace, 0, namespace.getHeight());
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
