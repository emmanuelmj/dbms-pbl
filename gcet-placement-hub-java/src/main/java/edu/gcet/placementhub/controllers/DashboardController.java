package edu.gcet.placementhub.controllers;

import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.services.DashboardService;
import edu.gcet.placementhub.services.DashboardService.DashboardData;

import javax.swing.*;
import java.util.function.Consumer;

/**
 * Asynchronous SwingWorker controller for loading and refreshing Dashboard data from MySQL JDBC.
 */
public class DashboardController {

    private final DashboardService dashboardService = new DashboardService();

    public void loadDashboardAsync(User user, Student knownStudent, Consumer<DashboardData> callback) {
        new SwingWorker<DashboardData, Void>() {
            @Override
            protected DashboardData doInBackground() {
                return dashboardService.loadDashboardData(user, knownStudent);
            }

            @Override
            protected void done() {
                try {
                    callback.accept(get());
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }
}
