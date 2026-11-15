package edu.gcet.placementhub;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.ui.MainFrame;

import javax.swing.*;
import java.util.Locale;

/**
 * Entry point for GCET PLACEMENT HUB (Java 17 Swing + FlatLaf + MongoDB Atlas / local MongoDB).
 *
 * macOS and Windows specific desktop properties are applied before any AWT class is loaded so the
 * app renders crisply on Retina / HiDPI screens and integrates with the macOS menu bar.
 */
public class Main {

    public static void main(String[] args) {
        applyDesktopProperties();
        SwingUtilities.invokeLater(() -> {
            ThemeManager.initFlatLaf(false);
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }

    private static void applyDesktopProperties() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            // Use the native macOS menu bar instead of an in-window bar (prevents layout clipping)
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "GCET Placement Hub");
            System.setProperty("apple.awt.application.appearance", "system");
            // Retina rendering: FlatLaf scales automatically; Metal pipeline avoids blurry text on Apple Silicon
            System.setProperty("sun.java2d.metal", "true");
        } else if (os.contains("win")) {
            // Per-monitor DPI awareness so FlatLaf scales correctly on 125%/150% Windows displays
            System.setProperty("sun.java2d.uiScale.enabled", "true");
            System.setProperty("sun.java2d.dpiaware", "true");
        }
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
    }
}
