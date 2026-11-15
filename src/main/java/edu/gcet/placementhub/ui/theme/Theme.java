package edu.gcet.placementhub.ui.theme;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.*;
import java.awt.*;

/**
 * GCET PLACEMENT HUB - Institutional Design System & Theme Engine
 * Geethanjali College of Engineering and Technology (Cheeryal, Keesara, Telangana)
 *
 * Visual Palette:
 * - Midnight Navy:  #101A35
 * - Deep Blue:      #172B50
 * - Electric Blue:  #2684FF
 * - Cyan Accent:    #32C5F4
 * - Soft White:     #F7F9FC
 * - Light BG:       #F3F6FB
 * - Dark Text:      #172033
 * - Subtle Border:  #E3EAF3
 */
public class Theme {
    public static final Color MIDNIGHT_NAVY  = new Color(0x10, 0x1A, 0x35);
    public static final Color DEEP_BLUE      = new Color(0x17, 0x2B, 0x50);
    public static final Color ELECTRIC_BLUE  = new Color(0x26, 0x84, 0xFF);
    public static final Color CYAN_ACCENT    = new Color(0x32, 0xC5, 0xF4);
    public static final Color SOFT_WHITE     = new Color(0xF7, 0xF9, 0xFC);
    public static final Color LIGHT_BG       = new Color(0xF3, 0xF6, 0xFB);
    public static final Color DARK_TEXT      = new Color(0x17, 0x20, 0x33);
    public static final Color SUBTLE_BORDER  = new Color(0xE3, 0xEA, 0xF3);
    public static final Color SUCCESS_GREEN  = new Color(0x10, 0xB9, 0x81);
    public static final Color WARNING_AMBER  = new Color(0xF5, 0x9E, 0x0B);
    public static final Color DANGER_ROSE    = new Color(0xEF, 0x44, 0x44);

    public static final Font FONT_HEADER_LARGE = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font FONT_HEADER_MED   = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_TITLE        = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_BODY         = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD         = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL        = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_MONO         = new Font("Consolas", Font.PLAIN, 12);

    public static void applyGlobalTheme() {
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.thumbArc", 10);
        UIManager.put("ScrollBar.width", 10);

        UIManager.put("Table.rowHeight", 36);
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
        UIManager.put("Table.gridColor", SUBTLE_BORDER);
        UIManager.put("TableHeader.height", 40);
        UIManager.put("TableHeader.font", FONT_BOLD);

        UIManager.put("defaultFont", FONT_BODY);
    }

    public static JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(FONT_BOLD);
        button.setBackground(ELECTRIC_BLUE);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        return button;
    }

    public static JButton createSecondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(FONT_BODY);
        button.setBackground(Color.WHITE);
        button.setForeground(DARK_TEXT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SUBTLE_BORDER, 1),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        return button;
    }

    public static JButton createDangerButton(String text) {
        JButton button = new JButton(text);
        button.setFont(FONT_BOLD);
        button.setBackground(DANGER_ROSE);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        return button;
    }
}
