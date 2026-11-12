// Standalone fixture: exercises the real pre-application JDK chooser without touching user settings.
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import ghidra.launch.*;
import ghidra.launch.JavaFinder.JavaFilter;

public class BootstrapReview {
    public static void main(String[] args) throws Exception {
        Path output = Paths.get(args[1]).toAbsolutePath();
        Files.createDirectories(output);
        String profile = output.resolve("profile").toString();
        if (ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(arg -> arg.startsWith("-Dapplication.settingsdir="))
                .noneMatch(arg -> Paths.get(arg.substring(arg.indexOf('=') + 1)).toAbsolutePath().normalize()
                    .equals(Paths.get(profile).normalize())))
            throw new AssertionError("A disposable JVM settings directory is required");
        Path installation = output.resolve("installation");
        Files.createDirectories(installation.resolve("Ghidra"));
        Files.createDirectories(installation.resolve("support"));
        Files.copy(Paths.get(args[0], "Ghidra/application.properties"), installation.resolve("Ghidra/application.properties"), StandardCopyOption.REPLACE_EXISTING);
        String launch = Files.readString(Paths.get(args[0], "support/launch.properties"));
        Files.writeString(installation.resolve("support/launch.properties"), launch +
            "\nVMARGS=-Dapplication.settingsdir=" + profile.replace('\\', '/') + "\n");
        AppConfig config = new AppConfig(installation.toFile());
        var saveField = AppConfig.class.getDeclaredField("javaHomeSaveFile");
        saveField.setAccessible(true);
        Path savePath = ((File) saveField.get(config)).toPath().toAbsolutePath().normalize();
        if (!savePath.startsWith(Paths.get(profile).normalize()))
            throw new AssertionError("Refusing to invoke chooser: save destination outside fixture: " + savePath);
        var method = LaunchSupport.class.getDeclaredMethod("askJavaHome", AppConfig.class,
            JavaFinder.class, JavaFilter.class);
        method.setAccessible(true);
        InputStream priorIn = System.in;
        PrintStream priorOut = System.out;
        ByteArrayOutputStream transcript = new ByteArrayOutputStream();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            System.setIn(new ByteArrayInputStream(("\n" + System.getProperty("java.home") + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new PrintStream(transcript, true, java.nio.charset.StandardCharsets.UTF_8));
            Future<Object> result = worker.submit(() -> method.invoke(null, config, JavaFinder.create(), JavaFilter.JDK_ONLY));
            boolean captured = false;
            Window[] chooserWindow = {null};
            JFileChooser[] activeChooser = {null};
            for (int attempt = 0; attempt < 300 && !captured; attempt++) {
                boolean[] ready = {false};
                SwingUtilities.invokeAndWait(() -> {
                    for (Window window : Window.getWindows()) {
                        if (!window.isShowing()) continue;
                        JFileChooser chooser = findChooser(window);
                        if (chooser == null) continue;
                        chooser.setCurrentDirectory(output.toFile());
                        window.setBounds(70, 70, 800, 540);
                        window.validate();
                        chooserWindow[0] = window;
                        activeChooser[0] = chooser;
                        ready[0] = true;
                    }
                });
                captured = ready[0];
                if (!captured) Thread.sleep(100);
            }
            if (!captured) throw new AssertionError("Bootstrap chooser did not appear");
            Thread.sleep(1000); // JFileChooser loads its directory listing asynchronously.
            SwingUtilities.invokeAndWait(() -> {
                Window window = chooserWindow[0];
                BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics();
                try { window.paint(graphics); ImageIO.write(image, "png", output.resolve("bootstrap-jdk-chooser.png").toFile()); }
                catch (Exception e) { throw new RuntimeException(e); }
                finally { graphics.dispose(); }
                activeChooser[0].cancelSelection();
            });
            if (!Integer.valueOf(0).equals(result.get(30, TimeUnit.SECONDS)))
                throw new AssertionError("JDK chooser/CLI recovery failed");
            String text = transcript.toString(java.nio.charset.StandardCharsets.UTF_8);
            if (!text.contains("Directory not specified") || !text.contains("Saved changes to " + profile))
                throw new AssertionError("Cancellation/recovery did not use isolated settings: " + text);
            Files.writeString(output.resolve("bootstrap-checks.txt"), "Actual LaunchSupport.askJavaHome directory chooser shown and cancelled.\nCLI recovered with a valid JDK; only disposable settings were saved.\n" + text);
        }
        finally {
            System.setIn(priorIn); System.setOut(priorOut); worker.shutdownNow();
            for (Window window : Window.getWindows()) SwingUtilities.invokeAndWait(window::dispose);
        }
        System.out.println("Bootstrap chooser review passed: " + output);
    }
    private static JFileChooser findChooser(Component component) {
        if (component instanceof JFileChooser chooser) return chooser;
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            JFileChooser result = findChooser(child); if (result != null) return result;
        }
        return null;
    }
}
