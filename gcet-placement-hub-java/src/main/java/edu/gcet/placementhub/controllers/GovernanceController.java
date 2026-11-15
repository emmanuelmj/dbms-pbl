package edu.gcet.placementhub.controllers;

import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.GovernanceModels.*;
import edu.gcet.placementhub.services.GovernanceService;
import edu.gcet.placementhub.services.GovernanceService.AuditLogBundle;
import edu.gcet.placementhub.utils.OperationResult;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Async SwingWorker controller mediating Phase 5 Governance, Approvals,
 * Reports, Settings, Audit Logs, and Database Diagnostics UI events.
 */
public class GovernanceController {

    private final GovernanceService service = new GovernanceService();

    public void loadApprovalBundleAsync(User actor, Consumer<ApprovalBundle> onComplete) {
        new SwingWorker<ApprovalBundle, Void>() {
            @Override
            protected ApprovalBundle doInBackground() {
                return service.loadApprovalBundle(actor);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(new ApprovalBundle(List.of(), List.of(), List.of(), List.of(), List.of(), ex.getMessage()));
                }
            }
        }.execute();
    }

    public void decideStudentsAsync(
            User actor,
            List<String> studentIds,
            boolean approve,
            String reason,
            Consumer<OperationResult<Integer>> onComplete
    ) {
        new SwingWorker<OperationResult<Integer>, Void>() {
            @Override
            protected OperationResult<Integer> doInBackground() {
                return service.decideStudents(actor, studentIds, approve, reason);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(OperationResult.fail(ex.getMessage()));
                }
            }
        }.execute();
    }

    public void decideFacultyAsync(
            User actor,
            Faculty target,
            boolean approve,
            String reason,
            Consumer<OperationResult<Void>> onComplete
    ) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.decideFaculty(actor, target, approve, reason);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(OperationResult.fail(ex.getMessage()));
                }
            }
        }.execute();
    }

    public void decideCompanyAsync(
            User actor,
            Company target,
            boolean approve,
            String reason,
            Consumer<OperationResult<Void>> onComplete
    ) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.decideCompany(actor, target, approve, reason);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(OperationResult.fail(ex.getMessage()));
                }
            }
        }.execute();
    }

    public void decideAdminRequestAsync(
            User actor,
            String requestId,
            boolean approve,
            String notes,
            Consumer<OperationResult<Void>> onComplete
    ) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.decideAdminRequest(actor, requestId, approve, notes);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(OperationResult.fail(ex.getMessage()));
                }
            }
        }.execute();
    }

    public void loadReportBundleAsync(User actor, ReportFilter filter, Consumer<ReportBundle> onComplete) {
        new SwingWorker<ReportBundle, Void>() {
            @Override
            protected ReportBundle doInBackground() {
                return service.generateReportBundle(actor, filter);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(new ReportBundle(
                            filter, List.of(), List.of(), List.of(), List.of(),
                            List.of(), List.of(), List.of(), List.of(), List.of(),
                            0, 0, 0, 0.0, 0.0, 0.0, "", ex.getMessage()
                    ));
                }
            }
        }.execute();
    }

    public void updateProfileAsync(
            User actor,
            String fullName,
            String email,
            String phone,
            String department,
            String designation,
            Consumer<OperationResult<User>> onComplete
    ) {
        new SwingWorker<OperationResult<User>, Void>() {
            @Override
            protected OperationResult<User> doInBackground() {
                return service.updateMyProfile(actor, fullName, email, phone, department, designation);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(OperationResult.fail(ex.getMessage()));
                }
            }
        }.execute();
    }

    public void changePasswordAsync(
            User actor,
            String currentPassword,
            String newPassword,
            Consumer<OperationResult<Void>> onComplete
    ) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.changeMyPassword(actor, currentPassword, newPassword);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(OperationResult.fail(ex.getMessage()));
                }
            }
        }.execute();
    }

    public void loadUsersForAdminAsync(User actor, Consumer<OperationResult<List<User>>> onComplete) {
        new SwingWorker<OperationResult<List<User>>, Void>() {
            @Override
            protected OperationResult<List<User>> doInBackground() {
                return service.loadUsersForAdmin(actor);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(OperationResult.fail(ex.getMessage()));
                }
            }
        }.execute();
    }

    public void createUserByAdminAsync(
            User actor,
            String username,
            String email,
            String fullName,
            UserRole targetRole,
            String department,
            String designation,
            String initialPassword,
            Consumer<OperationResult<String>> onComplete
    ) {
        new SwingWorker<OperationResult<String>, Void>() {
            @Override
            protected OperationResult<String> doInBackground() {
                return service.createUserByAdmin(actor, username, email, fullName, targetRole, department, designation, initialPassword);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(OperationResult.fail(ex.getMessage()));
                }
            }
        }.execute();
    }

    public void deleteUserByAdminAsync(User actor, String targetUserId, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.deleteUserByAdmin(actor, targetUserId);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(OperationResult.fail(ex.getMessage()));
                }
            }
        }.execute();
    }

    public void loadAuditLogsAsync(
            User actor,
            String search,
            String actionFilter,
            String roleFilter,
            String fromDate,
            String toDate,
            Consumer<AuditLogBundle> onComplete
    ) {
        new SwingWorker<AuditLogBundle, Void>() {
            @Override
            protected AuditLogBundle doInBackground() {
                return service.loadAuditLogs(actor, search, actionFilter, roleFilter, fromDate, toDate);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(new AuditLogBundle(List.of(), List.of(), false, ex.getMessage()));
                }
            }
        }.execute();
    }

    public void loadExtendedDbDiagnosticsAsync(Consumer<ExtendedDbStatus> onComplete) {
        new SwingWorker<ExtendedDbStatus, Void>() {
            @Override
            protected ExtendedDbStatus doInBackground() {
                return service.loadExtendedDbDiagnostics();
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    DbStatus fallback = new DbStatus();
                    fallback.setConnected(false);
                    fallback.setLastError(ex.getMessage());
                    onComplete.accept(ExtendedDbStatus.unavailable(fallback, ex.getMessage()));
                }
            }
        }.execute();
    }
}
