// Test-only Eclipse application. Runs in an isolated Eclipse installation and workspace.
package uireview;

import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.app.*;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.application.*;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.osgi.framework.Bundle;

public class EclipseReviewApp implements IApplication {
    private Path output;
    private Display display;
    private final List<String> rows = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();
    private int imageNumber;

    @Override
    public Object start(IApplicationContext context) throws Exception {
        output = Paths.get(System.getProperty("uireview.output"));
        Files.createDirectories(output);
        rows.add("kind,implementation,title,state,screenshot");
        var preferences = InstanceScope.INSTANCE.getNode("ghidra.ghidradev");
        preferences.putBoolean("ghidradev.requestedOpenPortConsent", true);
        preferences.putBoolean("ghidradev.scriptEditorEnabled", false);
        preferences.putBoolean("ghidradev.symbolLookupEnabled", false);
        preferences.flush();
        display = PlatformUI.createDisplay();
        try {
            PlatformUI.createAndRunWorkbench(display, new WorkbenchAdvisor() {
                @Override public String getInitialWindowPerspectiveId() { return "org.eclipse.jdt.ui.JavaPerspective"; }
                @Override public org.eclipse.core.runtime.IAdaptable getDefaultPageInput() { return ResourcesPlugin.getWorkspace().getRoot(); }
                @Override public WorkbenchWindowAdvisor createWorkbenchWindowAdvisor(IWorkbenchWindowConfigurer configurer) {
                    return new WorkbenchWindowAdvisor(configurer) {
                        @Override public void preWindowOpen() {
                            configurer.setInitialSize(new Point(1100, 760));
                            configurer.setTitle("GhidraDev isolated UI review");
                        }
                    };
                }
                @Override public void postStartup() { display.asyncExec(() -> reviewAndClose()); }
            });
        }
        finally { if (!display.isDisposed()) display.dispose(); }
        return failures.isEmpty() ? IApplication.EXIT_OK : Integer.valueOf(1);
    }

    private void reviewAndClose() {
        try {
            Bundle bundle = Platform.getBundle("ghidra.ghidradev");
            if (bundle == null) throw new AssertionError("GhidraDev bundle missing");
            bundle.start();
            IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("UiReviewJavaProject");
            if (!project.exists()) project.create(null);
            project.open(null);
            var description = project.getDescription();
            description.setNatureIds(new String[] { JavaCore.NATURE_ID });
            project.setDescription(description, null);
            var installation = project.getFolder("Ghidra");
            if (!installation.exists()) installation.createLink(new org.eclipse.core.runtime.Path(System.getProperty("uireview.ghidra")), IResource.NONE, null);
            if (!project.getFile("Module.manifest").exists()) project.getFile("Module.manifest")
                .create(new java.io.ByteArrayInputStream(new byte[0]), true, null);
            var selection = new StructuredSelection(JavaCore.create(project));
            var sourceFolder = project.getFolder("src");
            if (!sourceFolder.exists()) sourceFolder.create(true, true, null);
            var javaProject = JavaCore.create(project);
            var vmType = Arrays.stream(org.eclipse.jdt.launching.JavaRuntime.getVMInstallTypes())
                .filter(type -> type.getId().endsWith("StandardVMType")).findFirst().orElseThrow();
            var vm = vmType.createVMInstall("ui-review-jdk");
            vm.setName("UI Review JDK");
            vm.setInstallLocation(new java.io.File(System.getProperty("java.home")));
            org.eclipse.jdt.launching.JavaRuntime.setDefaultVMInstall(vm, null);
            javaProject.setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[] {
                JavaCore.newSourceEntry(sourceFolder.getFullPath()),
                org.eclipse.jdt.launching.JavaRuntime.getDefaultJREContainerEntry() }, null);
            for (String type : List.of("CreateGhidraScriptProjectWizard", "CreateGhidraModuleProjectWizard",
                    "CreateGhidraScriptWizard", "ImportGhidraModuleSourceWizard", "ExportGhidraModuleWizard", "LinkGhidraWizard")) {
                try {
                    Class<?> wizardType = bundle.loadClass("ghidradev.ghidraprojectcreator.wizards." + type);
                    IWizard wizard = type.equals("LinkGhidraWizard") ?
                        (IWizard) wizardType.getConstructor(org.eclipse.jface.viewers.ISelection.class).newInstance(selection) :
                        (IWizard) wizardType.getConstructor().newInstance();
                    if (wizard instanceof IWorkbenchWizard workbenchWizard) workbenchWizard.init(PlatformUI.getWorkbench(), selection);
                    WizardDialog dialog = new WizardDialog(shell(), wizard);
                    dialog.setBlockOnOpen(false);
                    dialog.create(); dialog.open();
                    dialog.getShell().setBounds(80, 80, 1100, 760);
                    for (IWizardPage page : wizard.getPages()) {
                        dialog.showPage(page);
                        settle();
                        String state = "complete=" + page.isPageComplete() + "; message=" + page.getMessage() + "; error=" + page.getErrorMessage();
                        record("wizard page", page.getClass().getName(), type + " / " + page.getTitle(), state, dialog.getShell());
                    }
                    Shell wizardShell = dialog.getShell();
                    cancel(wizardShell);
                    if (!wizardShell.isDisposed()) throw new AssertionError("Wizard did not cancel: " + type);
                }
                catch (Throwable failure) { failed(type, failure); closeChildren(); }
            }
            try {
                var chooser = (org.eclipse.jface.dialogs.Dialog) bundle.loadClass("ghidradev.ghidraprojectcreator.utils.PackageFragmentRootSelectionDialog")
                    .getConstructor(Shell.class, String.class, String.class, String.class)
                    .newInstance(shell(), "Choose Script Source Folder", "Select a Java source folder", "Select a source folder to continue");
                chooser.setBlockOnOpen(false); chooser.open(); settle();
                record("source folder chooser", chooser.getClass().getName(), "Choose Script Source Folder", "no selection; source-folder validation", chooser.getShell());
                cancel(chooser.getShell());
            }
            catch (Throwable failure) { failed("source folder chooser", failure); closeChildren(); }
            try {
                var type = DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurationType("GhidraGuiLaunchConfigurationType");
                var launch = type.newInstance(null, "UI Review Ghidra Launch");
                launch.setAttribute(org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project.getName());
                var config = launch.doSave();
                display.timerExec(1500, () -> {
                    try {
                        Shell launchShell = Arrays.stream(display.getShells()).filter(s -> !s.isDisposed() && s.isVisible() &&
                            s.getText().contains("Run Configurations")).findFirst().orElseThrow();
                        launchShell.setBounds(80, 80, 1200, 800);
                        for (Control control : controls(launchShell)) {
                            if (control instanceof org.eclipse.swt.custom.CTabFolder tabs) {
                                for (int i = 0; i < tabs.getItemCount(); i++) {
                                    tabs.setSelection(i);
                                    Event event = new Event(); event.item = tabs.getItem(i);
                                    tabs.notifyListeners(SWT.Selection, event); settle();
                                    record("launch configuration tab", "ghidradev.ghidraprojectcreator.launchers.GhidraLaunchTabGroup",
                                        tabs.getItem(i).getText(), "configuration only; no process launched", launchShell);
                                }
                            }
                        }
                        for (Control control : controls(launchShell)) if (control instanceof Button button && button.getText().replace("&", "").equals("Close")) {
                            button.notifyListeners(SWT.Selection, new Event()); return;
                        }
                        launchShell.close();
                    }
                    catch (Throwable failure) { failed("launch configuration", failure); closeChildren(); }
                });
                DebugUITools.openLaunchConfigurationDialogOnGroup(shell(), new StructuredSelection(config), IDebugUIConstants.ID_RUN_LAUNCH_GROUP);
            }
            catch (Throwable failure) { failed("launch configuration", failure); closeChildren(); }
            for (String pageId : List.of("ghidradev.GhidraRootPreferencePage",
                    "ghidradev.ghidraprojectcreator.preferences.GhidraProjectCreatorPreferencePage",
                    "ghidradev.ghidrascripteditor.preferences.GhidraScriptEditorPreferencePage",
                    "ghidradev.ghidrasymbollookup.preferences.GhidraSymbolLookupPreferencePage")) {
                try {
                    PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(shell(), pageId, new String[] {pageId}, null);
                    if (dialog == null) throw new AssertionError("Preference page not registered: " + pageId);
                    dialog.setBlockOnOpen(false); dialog.open();
                    dialog.getShell().setBounds(80, 80, 1100, 760); settle();
                    var page = dialog.getPreferenceManager().getElements(org.eclipse.jface.preference.PreferenceManager.PRE_ORDER).stream()
                        .filter(node -> node.getId().equals(pageId)).findFirst().orElseThrow().getPage();
                    record("preferences", pageId, page.getTitle(), "valid=" + page.isValid(), dialog.getShell());
                    if (pageId.contains("ScriptEditor") || pageId.contains("SymbolLookup")) {
                        List<Text> fields = new ArrayList<>(); texts(page.getControl(), fields);
                        if (fields.isEmpty()) throw new AssertionError("Port field absent");
                        Text port = fields.getLast();
                        port.setFocus();
                        port.setText("not-a-port");
                        port.notifyListeners(SWT.FocusOut, new Event());
                        settle();
                        if (page.isValid()) throw new AssertionError("Invalid port accepted: " + pageId);
                        String error = ((org.eclipse.jface.preference.PreferencePage) page).getErrorMessage();
                        if (error == null || !error.contains("Port must be an integer"))
                            throw new AssertionError("Expected a port error, got: " + error);
                        record("preferences error", pageId, page.getTitle(), error, dialog.getShell());
                    }
                    cancel(dialog.getShell());
                }
                catch (Throwable failure) { failed(pageId, failure); closeChildren(); }
            }
            var preferenceStore = (org.eclipse.jface.preference.IPreferenceStore) bundle.loadClass("ghidradev.Activator")
                .getMethod("getPreferenceStore").invoke(bundle.loadClass("ghidradev.Activator").getMethod("getDefault").invoke(null));
            if (preferenceStore.getBoolean("ghidradev.scriptEditorEnabled") || preferenceStore.getBoolean("ghidradev.symbolLookupEnabled"))
                throw new AssertionError("Cancelled preferences changed listener enablement");
            Files.write(output.resolve("eclipse-review.csv"), rows);
            ResourcesPlugin.getWorkspace().save(true, null);
            Files.writeString(output.resolve(failures.isEmpty() ? "review.done" : "review.failures"),
                failures.isEmpty() ? "Source-built GhidraDev wizard pages and preferences captured; cancellation verified; no listener enabled.\n" : String.join("\n", failures));
        }
        catch (Throwable failure) {
            failed("application", failure);
            try { Files.writeString(output.resolve("review.failures"), String.join("\n", failures)); }
            catch (Exception ignored) { }
        }
        finally { PlatformUI.getWorkbench().close(); }
    }

    private Shell shell() { return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(); }
    private void settle() {
        long until = System.currentTimeMillis() + 450;
        while (System.currentTimeMillis() < until) {
            if (!display.readAndDispatch()) try { Thread.sleep(15); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }
    private void record(String kind, String implementation, String title, String state, Shell shell) throws Exception {
        shell.layout(true, true); shell.redraw(); shell.update(); settle();
        String filename = String.format("%02d-%s.png", ++imageNumber, implementation.substring(implementation.lastIndexOf('.') + 1));
        Rectangle client = shell.getClientArea();
        Image image = new Image(display, client.width, client.height);
        GC graphics = new GC(shell);
        try {
            graphics.copyArea(image, 0, 0);
            ImageLoader loader = new ImageLoader(); loader.data = new ImageData[] {image.getImageData()};
            loader.save(output.resolve(filename).toString(), SWT.IMAGE_PNG);
        }
        finally { graphics.dispose(); image.dispose(); }
        rows.add(csv(kind, implementation, title, state, filename));
    }
    private static void cancel(Shell shell) {
        for (Control control : controls(shell)) if (control instanceof Button button && button.getText().replace("&", "").equals("Cancel")) {
            button.notifyListeners(SWT.Selection, new Event()); return;
        }
        throw new AssertionError("Cancel button missing");
    }
    private void closeChildren() {
        Shell main = shell();
        for (Shell child : display.getShells()) if (child != main && !child.isDisposed()) child.close();
    }
    private static List<Control> controls(Control control) {
        List<Control> result = new ArrayList<>(); result.add(control);
        if (control instanceof Composite parent) for (Control child : parent.getChildren()) result.addAll(controls(child));
        return result;
    }
    private static void texts(Control control, List<Text> fields) {
        for (Control child : controls(control)) if (child instanceof Text text) fields.add(text);
    }
    private void failed(String context, Throwable failure) {
        java.io.StringWriter text = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(text));
        failures.add(context + ": " + text); System.err.println(context + ": " + text);
    }
    private static String csv(String... values) {
        return Arrays.stream(values).map(value -> "\"" + value.replace("\"", "\"\"") + "\"").collect(java.util.stream.Collectors.joining(","));
    }
    @Override public void stop() { if (display != null && !display.isDisposed()) display.asyncExec(() -> PlatformUI.getWorkbench().close()); }
}
