package edu.gcet.placementhub.config;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central institutional visual design tokens and FlatLaf Light/Dark manager
 * matching the GCET Placement Hub color system.
 */
public final class ThemeManager {

    // Core Institutional Palette (exact match to src/index.css & Tailwind classes)
    public static final Color NAVY_MIDNIGHT = new Color(0x10, 0x1A, 0x35);      // #101A35
    public static final Color NAVY_DEEP = new Color(0x17, 0x2B, 0x50);          // #172B50
    public static final Color NAVY_HEADER_DARK = new Color(0x0F, 0x1D, 0x3D);   // #0F1D3D
    public static final Color NAVY_OVERLAY_DARK = new Color(0x07, 0x0E, 0x20);  // #070E20
    public static final Color BLUE_ELECTRIC = new Color(0x26, 0x84, 0xFF);      // #2684FF
    public static final Color BLUE_HOVER = new Color(0x1F, 0x73, 0xE6);         // #1F73E6
    public static final Color CYAN_ACCENT = new Color(0x32, 0xC5, 0xF4);        // #32C5F4
    public static final Color CYAN_PRIMARY = new Color(0x06, 0xB6, 0xD4);       // #06B6D4
    public static final Color CYAN_DARK = new Color(0x08, 0x91, 0xB2);          // #0891B2

    public static final Color EMERALD_PRIMARY = new Color(0x05, 0x96, 0x69);    // #059669
    public static final Color EMERALD_SOFT = new Color(0xEC, 0xFD, 0xF5);
    public static final Color PURPLE_PRIMARY = new Color(0x93, 0x33, 0xEA);     // #9333EA
    public static final Color PURPLE_SOFT = new Color(0xFA, 0xF5, 0xFF);
    public static final Color AMBER_PRIMARY = new Color(0xD9, 0x77, 0x06);
    public static final Color AMBER_SOFT = new Color(0xFE, 0xF3, 0xC7);
    public static final Color ROSE_PRIMARY = new Color(0xE1, 0x1D, 0x48);
    public static final Color ROSE_SOFT = new Color(0xFF, 0xF1, 0xF2);

    public static final Color SOFT_WHITE = new Color(0xF7, 0xF9, 0xFC);
    public static final Color LIGHT_BG = new Color(0xF3, 0xF6, 0xFB);
    public static final Color SLATE_50 = new Color(0xF8, 0xFA, 0xFC);
    public static final Color SLATE_100 = new Color(0xF1, 0xF5, 0xF9);
    public static final Color SLATE_200 = new Color(0xE2, 0xE8, 0xF0);
    public static final Color SLATE_300 = new Color(0xCB, 0xD5, 0xE1);
    public static final Color SLATE_400 = new Color(0x94, 0xA3, 0xB8);
    public static final Color SLATE_500 = new Color(0x64, 0x74, 0x8B);
    public static final Color SLATE_600 = new Color(0x47, 0x55, 0x69);
    public static final Color SLATE_700 = new Color(0x33, 0x41, 0x55);
    public static final Color SLATE_800 = new Color(0x1E, 0x29, 0x3B);
    public static final Color SLATE_900 = new Color(0x0F, 0x17, 0x2A);

    public static final Font FONT_FAMILY_BASE = resolvePreferredFont();

    private static boolean darkMode = false;
    private static final List<Runnable> themeListeners = new CopyOnWriteArrayList<>();

    private ThemeManager() {
    }

    private static Font resolvePreferredFont() {
        String[] candidates = {"Plus Jakarta Sans", "Segoe UI", "Inter", "Dialog"};
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        String[] available = ge.getAvailableFontFamilyNames();
        for (String candidate : candidates) {
            for (String sys : available) {
                if (sys.equalsIgnoreCase(candidate)) {
                    return new Font(sys, Font.PLAIN, 13);
                }
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    }

    public static Font font(int style, float size) {
        return FONT_FAMILY_BASE.deriveFont(style, size);
    }

    public static Font monoFont(int style, int size) {
        return new Font(Font.MONOSPACED, style, size);
    }

    public static Font monoFont(int style, float size) {
        return new Font(Font.MONOSPACED, style, Math.round(size));
    }

    public static void initFlatLaf(boolean startDark) {
        darkMode = startDark;
        applyLaf();
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static void toggleTheme() {
        darkMode = !darkMode;
        applyLaf();
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
        for (Runnable listener : themeListeners) {
            listener.run();
        }
    }

    public static void addThemeListener(Runnable listener) {
        themeListeners.add(listener);
    }

    private static void applyLaf() {
        try {
            UIManager.put("Component.arc", 12);
            UIManager.put("Button.arc", 12);
            UIManager.put("TextComponent.arc", 12);
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.width", 8);
            UIManager.put("defaultFont", font(Font.PLAIN, 13));

            if (darkMode) {
                FlatDarkLaf.setup();
                UIManager.put("Panel.background", new Color(0x0B, 0x13, 0x29));
            } else {
                FlatLightLaf.setup();
                UIManager.put("Panel.background", Color.WHITE);
            }
            FlatLaf.updateUI();
        } catch (Exception ignored) {
        }
    }

    public static Color surfaceBg() {
        return darkMode ? new Color(0x0B, 0x13, 0x29) : SOFT_WHITE;
    }

    public static Color cardBg() {
        return darkMode ? NAVY_MIDNIGHT : Color.WHITE;
    }

    public static Color cardSubtleBg() {
        return darkMode ? new Color(0x15, 0x23, 0x47) : SLATE_50;
    }

    public static Color cardBorder() {
        return darkMode ? NAVY_DEEP : SLATE_200;
    }

    public static Color primaryText() {
        return darkMode ? Color.WHITE : NAVY_HEADER_DARK;
    }

    public static Color secondaryText() {
        return darkMode ? SLATE_300 : SLATE_600;
    }

    public static Color mutedText() {
        return darkMode ? SLATE_400 : SLATE_500;
    }

    public static Color inputBg() {
        return darkMode ? SLATE_900 : SLATE_50;
    }

    public static Color inputBorder() {
        return darkMode ? SLATE_700 : SLATE_300;
    }
}
