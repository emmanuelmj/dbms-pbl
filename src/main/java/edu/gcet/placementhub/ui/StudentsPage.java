package edu.gcet.placementhub.ui;

import edu.gcet.placementhub.model.User;

/**
 * GCET PLACEMENT HUB - Student Management Page Alias
 * Provides backward-compatible import for edu.gcet.placementhub.ui.StudentsPage.
 */
public class StudentsPage extends edu.gcet.placementhub.ui.pages.StudentsPage {
    public StudentsPage(User currentUser) {
        super(currentUser);
    }
}
