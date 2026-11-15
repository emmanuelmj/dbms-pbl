package edu.gcet.placementhub;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.ui.MainFrame;

import javax.swing.*;

/**
 * Entry point for GCET PLACEMENT HUB (Java 17 Swing + FlatLaf + JDBC + MySQL).
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ThemeManager.initFlatLaf(false);
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
