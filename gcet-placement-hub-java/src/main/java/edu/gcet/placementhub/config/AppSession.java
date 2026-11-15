package edu.gcet.placementhub.config;

import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.models.User;

/**
 * Holds the currently authenticated user session in memory for the desktop application.
 */
public final class AppSession {

    private static AppSession instance;

    private User currentUser;
    private Student currentStudent;

    private AppSession() {
    }

    public static synchronized AppSession getInstance() {
        if (instance == null) {
            instance = new AppSession();
        }
        return instance;
    }

    public synchronized User getCurrentUser() {
        return currentUser;
    }

    public synchronized Student getCurrentStudent() {
        return currentStudent;
    }

    public synchronized void startSession(User user, Student student) {
        this.currentUser = user;
        this.currentStudent = student;
    }

    public synchronized void clear() {
        this.currentUser = null;
        this.currentStudent = null;
    }

    public synchronized boolean isAuthenticated() {
        return this.currentUser != null;
    }
}
