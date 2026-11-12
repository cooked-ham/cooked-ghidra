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

import ghidra.app.services.GoToService;
import ghidra.app.services.NavigationHistoryService;
import ghidra.framework.plugintool.PluginTool;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.program.util.ProgramLocation;

/**
 * Provider-local location-context spike. Enabled at startup with
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
	private final JButton programButton = button("No program", "Program: first address");
	private final JLabel namespace = new JLabel("Global");
	private final JButton functionButton = button("No location", "Go to function entry");
	private final JButton addressButton = button("—", "Go to displayed address");
	private final JLabel state = new JLabel("Unavailable");
	private final Timer updater;
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
		programButton.addActionListener(_ -> goTo(targetProgram == null ? null : targetProgram.getMinAddress()));
		functionButton.addActionListener(_ -> goTo(functionEntry));
		addressButton.addActionListener(_ -> goTo(targetAddress));
		// Only showing panels poll. No listeners retain closed programs or detached providers.
		// Refresh is observational: only the click handlers call the existing navigation services.
		updater = new Timer(200, _ -> {
			if (isShowing()) {
				refreshContext();
			}
		});
	}

	@Override
	public void addNotify() {
		super.addNotify();
		refreshContext();
		updater.start();
	}

	@Override
	public void removeNotify() {
		updater.stop();
		super.removeNotify();
	}

	/** Refresh labels and history availability without adding history entries. Runs on the EDT. */
	public void refreshContext() {
		if (navigatable.isDisposed()) {
			updater.stop();
			return;
		}
		Context context = contextSupplier.get();
		targetProgram = context.program();
		if (targetProgram != null && targetProgram.isClosed()) {
			targetProgram = null;
		}
		ProgramLocation location = context.location();
		// Never combine one program's name with a stale location from a different program.
		targetAddress = targetProgram != null && location != null &&
			location.getProgram() == targetProgram ? location.getAddress() : null;
		Function function = targetAddress == null ? null :
			targetProgram.getFunctionManager().getFunctionContaining(targetAddress);
		functionEntry = function == null ? null : function.getEntryPoint();
		programButton.setText(targetProgram == null ? "No program" : targetProgram.getName());
		programButton.setToolTipText(targetProgram == null ? "No program" :
			"Program: " + targetProgram.getDomainFile().getPathname() + " (go to first address)");
		namespace.setText(targetAddress == null ? "—" : function == null ?
			targetProgram.getSymbolTable().getNamespace(targetAddress).getName(true) :
			function.getParentNamespace().getName(true));
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
		programButton.setEnabled(goTo != null && targetProgram != null && targetProgram.getMinAddress() != null);
		functionButton.setEnabled(goTo != null && functionEntry != null);
		addressButton.setEnabled(goTo != null && targetAddress != null);
		NavigationHistoryService history = tool.getService(NavigationHistoryService.class);
		Navigatable historyTarget = historyTarget();
		previous.setEnabled(history != null && historyTarget != null && history.hasPrevious(historyTarget));
		next.setEnabled(history != null && historyTarget != null && history.hasNext(historyTarget));
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
