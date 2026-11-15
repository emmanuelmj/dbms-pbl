package edu.gcet.placementhub.controllers;

import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.services.StudentManagementService;
import edu.gcet.placementhub.services.StudentManagementService.DirectoryBundle;
import edu.gcet.placementhub.services.StudentManagementService.OperationResult;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Async SwingWorker controller mediating UI events and JDBC StudentManagementService operations.
 */
public class StudentController {

    private final StudentManagementService service = new StudentManagementService();

    public void loadDirectoryBundleAsync(User currentUser, Consumer<DirectoryBundle> onComplete) {
        new SwingWorker<DirectoryBundle, Void>() {
            @Override
            protected DirectoryBundle doInBackground() {
                return service.loadDirectoryBundle(currentUser);
            }

            @Override
            protected void done() {
                try {
                    onComplete.accept(get());
                } catch (Exception ex) {
                    onComplete.accept(new DirectoryBundle(List.of(), List.of(), List.of(), ex.getMessage()));
                }
            }
        }.execute();
    }

    public void loadFacultyDirectoryAsync(User actor, Consumer<OperationResult<List<Faculty>>> onComplete) {
        new SwingWorker<OperationResult<List<Faculty>>, Void>() {
            @Override
            protected OperationResult<List<Faculty>> doInBackground() {
                return service.loadFacultyDirectory(actor);
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

    public void createStudentAsync(User actor, Student draft, String plainPassword, Consumer<OperationResult<String>> onComplete) {
        new SwingWorker<OperationResult<String>, Void>() {
            @Override
            protected OperationResult<String> doInBackground() {
                return service.createStudent(actor, draft, plainPassword);
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

    public void updateStudentAsync(User actor, Student updated, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.updateStudent(actor, updated);
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

    public void deleteStudentAsync(User actor, String studentId, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.deleteStudent(actor, studentId);
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

    public void upsertStudentSkillAsync(
            User actor,
            String studentId,
            String skillId,
            int proficiency,
            boolean markVerified,
            Consumer<OperationResult<Void>> onComplete
    ) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.upsertStudentSkill(actor, studentId, skillId, proficiency, markVerified);
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

    public void verifyStudentSkillAsync(User actor, String studentId, String skillId, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.verifyStudentSkill(actor, studentId, skillId);
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

    public void addProjectAsync(User actor, String studentId, StudentProject project, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.addProject(actor, studentId, project);
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

    public void deleteProjectAsync(User actor, String studentId, String projectId, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.deleteProject(actor, studentId, projectId);
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

    public void addCertificationAsync(User actor, String studentId, StudentCertification cert, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.addCertification(actor, studentId, cert);
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

    public void deleteCertificationAsync(User actor, String studentId, String certId, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.deleteCertification(actor, studentId, certId);
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

    public void addInternshipAsync(User actor, String studentId, StudentInternship internship, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.addInternship(actor, studentId, internship);
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

    public void deleteInternshipAsync(User actor, String studentId, String internshipId, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.deleteInternship(actor, studentId, internshipId);
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

    public void createSkillAsync(User actor, Skill skill, Consumer<OperationResult<String>> onComplete) {
        new SwingWorker<OperationResult<String>, Void>() {
            @Override
            protected OperationResult<String> doInBackground() {
                return service.createSkill(actor, skill);
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

    public void updateSkillAsync(User actor, Skill skill, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.updateSkill(actor, skill);
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

    public void deleteSkillAsync(User actor, Skill skill, Consumer<OperationResult<Void>> onComplete) {
        new SwingWorker<OperationResult<Void>, Void>() {
            @Override
            protected OperationResult<Void> doInBackground() {
                return service.deleteSkill(actor, skill);
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
