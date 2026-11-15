package edu.gcet.placementhub;

import com.formdev.flatlaf.FlatLightLaf;
import edu.gcet.placementhub.db.DatabaseConnection;
import edu.gcet.placementhub.ui.MainWindow;
import edu.gcet.placementhub.ui.dialogs.LoginDialog;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GCET PLACEMENT HUB
 * Smart Placement Cell & Skill Matrix Portal
 * Geethanjali College of Engineering and Technology
 * Cheeryal, Keesara, Telangana, India.
 *
 * "Your Skills. Your Opportunities. Your Future."
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        // High-DPI screen adjustments
        System.setProperty("sun.java2d.uiScale.enabled", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                // Initialize FlatLaf modern desktop look and feel
                FlatLightLaf.setup();
                Theme.applyGlobalTheme();

                // Test connection to MySQL 8 database
                LOGGER.info("Pinging MySQL 8 database gcet_placement_hub...");
                boolean connected = DatabaseConnection.testConnection();
                if (connected) {
                    edu.gcet.placementhub.db.DatabaseInitializer.initializeSchema();
                } else {
                    LOGGER.warning("Could not establish direct connection to MySQL server. Starting in offline mode.");
                }

                // Show Login Screen first
                LoginDialog loginDialog = new LoginDialog(null);
                loginDialog.setVisible(true);

                if (loginDialog.isAuthenticated()) {
                    // Launch main application window with authenticated user session
                    MainWindow mainWindow = new MainWindow(loginDialog.getAuthenticatedUser());
                    mainWindow.setVisible(true);
                } else {
                    LOGGER.info("Authentication cancelled. Exiting application.");
                    System.exit(0);
                }

            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Fatal error launching GCET Placement Hub", ex);
                JOptionPane.showMessageDialog(
                    null,
                    "An error occurred during startup:\n" + ex.getMessage(),
                    "GCET Placement Hub - Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }
}
