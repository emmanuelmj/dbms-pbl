package edu.gcet.placementhub.controllers;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.models.PortalCategory;
import edu.gcet.placementhub.services.AuthService;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Asynchronous SwingWorker controller connecting auth/registration screens with MySQL JDBC services.
 */
public class AuthController {

    private final AuthService authService = new AuthService();

    public void loginAsync(
            String username,
            String password,
            PortalCategory portalCategory,
            Consumer<AuthService.AuthResult> onSuccess,
            Consumer<String> onError
    ) {
        new SwingWorker<AuthService.AuthResult, Void>() {
            private String errorMessage;

            @Override
            protected AuthService.AuthResult doInBackground() {
                try {
                    return authService.login(username, password, portalCategory);
                } catch (AuthService.AuthException ex) {
                    this.errorMessage = ex.getMessage();
                    return null;
                } catch (Exception ex) {
                    this.errorMessage = "Authentication failed: " + ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    AuthService.AuthResult res = get();
                    if (res != null && errorMessage == null) {
                        AppSession.getInstance().startSession(res.getUser(), res.getStudentRecord());
                        onSuccess.accept(res);
                    } else {
                        onError.accept(errorMessage != null ? errorMessage : "Incorrect username or password. Please try again.");
                    }
                } catch (Exception ex) {
                    onError.accept(errorMessage != null ? errorMessage : ex.getMessage());
                }
            }
        }.execute();
    }

    public void registerStudentAsync(
            String fullName,
            String rollNumber,
            String email,
            String phone,
            String branch,
            int academicYear,
            int graduationYear,
            double cgpa,
            String password,
            String confirmPassword,
            List<String> selectedSkills,
            Consumer<AuthService.RegistrationReceipt> onSuccess,
            Consumer<String> onError
    ) {
        new SwingWorker<AuthService.RegistrationReceipt, Void>() {
            private String errorMessage;

            @Override
            protected AuthService.RegistrationReceipt doInBackground() {
                try {
                    return authService.registerStudent(
                            fullName, rollNumber, email, phone, branch,
                            academicYear, graduationYear, cgpa, password, confirmPassword, selectedSkills
                    );
                } catch (AuthService.AuthException ex) {
                    this.errorMessage = ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    AuthService.RegistrationReceipt res = get();
                    if (res != null && errorMessage == null) {
                        onSuccess.accept(res);
                    } else {
                        onError.accept(errorMessage != null ? errorMessage : "Student registration failed.");
                    }
                } catch (Exception ex) {
                    onError.accept(ex.getMessage());
                }
            }
        }.execute();
    }

    public void registerFacultyAsync(
            String fullName,
            String employeeId,
            String email,
            String department,
            String designation,
            String phone,
            String password,
            String confirmPassword,
            Consumer<AuthService.RegistrationReceipt> onSuccess,
            Consumer<String> onError
    ) {
        new SwingWorker<AuthService.RegistrationReceipt, Void>() {
            private String errorMessage;

            @Override
            protected AuthService.RegistrationReceipt doInBackground() {
                try {
                    return authService.registerFaculty(
                            fullName, employeeId, email, department, designation, phone, password, confirmPassword
                    );
                } catch (AuthService.AuthException ex) {
                    this.errorMessage = ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    AuthService.RegistrationReceipt res = get();
                    if (res != null && errorMessage == null) {
                        onSuccess.accept(res);
                    } else {
                        onError.accept(errorMessage != null ? errorMessage : "Faculty registration failed.");
                    }
                } catch (Exception ex) {
                    onError.accept(ex.getMessage());
                }
            }
        }.execute();
    }

    public void registerCompanyAsync(
            String companyName,
            String industry,
            String website,
            String location,
            String hrName,
            String hrEmail,
            String hrPhone,
            String tier,
            String password,
            String confirmPassword,
            Consumer<AuthService.RegistrationReceipt> onSuccess,
            Consumer<String> onError
    ) {
        new SwingWorker<AuthService.RegistrationReceipt, Void>() {
            private String errorMessage;

            @Override
            protected AuthService.RegistrationReceipt doInBackground() {
                try {
                    return authService.registerCompany(
                            companyName, industry, website, location, hrName, hrEmail, hrPhone, tier, password, confirmPassword
                    );
                } catch (AuthService.AuthException ex) {
                    this.errorMessage = ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    AuthService.RegistrationReceipt res = get();
                    if (res != null && errorMessage == null) {
                        onSuccess.accept(res);
                    } else {
                        onError.accept(errorMessage != null ? errorMessage : "Company registration failed.");
                    }
                } catch (Exception ex) {
                    onError.accept(ex.getMessage());
                }
            }
        }.execute();
    }

    public void checkDatabaseHealthAsync(Consumer<DbStatus> onDone) {
        new SwingWorker<DbStatus, Void>() {
            @Override
            protected DbStatus doInBackground() {
                return DatabaseConnection.getInstance().pingAndInspect();
            }

            @Override
            protected void done() {
                try {
                    onDone.accept(get());
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    public void recoverPasswordAsync(
            String identifier,
            String registeredEmail,
            String mysqlDbPassword,
            String newPassword,
            String confirmPassword,
            Consumer<String> onSuccess,
            Consumer<String> onError
    ) {
        new SwingWorker<String, Void>() {
            private String errorMessage;

            @Override
            protected String doInBackground() {
                try {
                    return authService.recoverAndSetPassword(identifier, registeredEmail, mysqlDbPassword, newPassword, confirmPassword);
                } catch (AuthService.AuthException ex) {
                    this.errorMessage = ex.getMessage();
                    return null;
                } catch (Exception ex) {
                    this.errorMessage = "Password recovery error: " + ex.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    String msg = get();
                    if (msg != null && errorMessage == null) {
                        onSuccess.accept(msg);
                    } else {
                        onError.accept(errorMessage != null ? errorMessage : "Could not update password.");
                    }
                } catch (Exception ex) {
                    onError.accept(errorMessage != null ? errorMessage : ex.getMessage());
                }
            }
        }.execute();
    }

    public void logout() {
        edu.gcet.placementhub.config.AppSession.getInstance().clear();
    }
}
