package edu.gcet.placementhub.controllers;

import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.PlacementModels.PlacementBundle;
import edu.gcet.placementhub.services.PlacementManagementService;
import edu.gcet.placementhub.services.StudentManagementService.OperationResult;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Async SwingWorker controller mediating Phase 4 UI events and JDBC PlacementManagementService.
 */
public class PlacementController {

    private final PlacementManagementService service = new PlacementManagementService();

    public void loadPlacementBundleAsync(User actor, Consumer<PlacementBundle> onComplete) {
        new SwingWorker<PlacementBundle, Void>() {
            @Override
            protected PlacementBundle doInBackground() {
                return service.loadPlacementBundle(actor);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(new PlacementBundle(List.of(), List.of(), List.of(), List.of(), List.of(), ex.getMessage()));
                }
            }
        }.execute();
    }

    public void createCompanyAsync(User actor, Company draft, Consumer<OperationResult<String>> onComplete) {
        new SwingWorker<OperationResult<String>, Void>() {
            @Override
            protected OperationResult<String> doInBackground() {
                return service.createCompany(actor, draft);
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

    public void updateCompanyAsync(User actor, Company updated, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.updateCompany(actor, updated);
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

    public void updateCompanyApprovalAsync(
            User actor,
            String companyId,
            String approvalStatus,
            String reason,
            Consumer<OperationResult<Void>> onComplete
    ) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.updateCompanyApprovalStatus(actor, companyId, approvalStatus, reason);
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

    public void deleteCompanyAsync(User actor, String companyId, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.deleteCompany(actor, companyId);
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

    public void createDriveAsync(User actor, PlacementDrive draft, Consumer<OperationResult<String>> onComplete) {
        new SwingWorker<OperationResult<String>, Void>() {
            @Override
            protected OperationResult<String> doInBackground() {
                return service.createDrive(actor, draft);
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

    public void updateDriveAsync(User actor, PlacementDrive updated, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.updateDrive(actor, updated);
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

    public void deleteDriveAsync(User actor, String driveId, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.deleteDrive(actor, driveId);
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

    public void submitApplicationAsync(User actor, String driveId, String studentId, Consumer<OperationResult<String>> onComplete) {
        new SwingWorker<OperationResult<String>, Void>() {
            @Override
            protected OperationResult<String> doInBackground() {
                return service.submitApplication(actor, driveId, studentId);
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

    public void advanceStageAsync(
            User actor,
            String appId,
            ApplicationStage nextStage,
            Double offerLpa,
            String rejectionReason,
            String notes,
            Consumer<OperationResult<Void>> onComplete
    ) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.advanceApplicationStage(actor, appId, nextStage, offerLpa, rejectionReason, notes);
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

    public void withdrawApplicationAsync(User actor, String appId, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.withdrawApplication(actor, appId);
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
}
