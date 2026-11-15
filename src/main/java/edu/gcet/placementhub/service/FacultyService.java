package edu.gcet.placementhub.service;

import edu.gcet.placementhub.dao.FacultyDao;
import edu.gcet.placementhub.model.Faculty;
import edu.gcet.placementhub.model.User;

import java.util.List;
import java.util.regex.Pattern;

/**
 * GCET PLACEMENT HUB - Faculty Management Service
 * Business logic, departmental coordination, and role checks.
 */
public class FacultyService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private final FacultyDao facultyDao;

    public FacultyService() {
        this.facultyDao = new FacultyDao();
    }

    public FacultyService(FacultyDao facultyDao) {
        this.facultyDao = facultyDao;
    }

    public List<Faculty> getFaculty(User currentUser, String search, String deptFilter, String statusFilter) {
        return facultyDao.findAll(search, deptFilter, statusFilter);
    }

    public Faculty getFacultyById(User currentUser, String id) {
        return facultyDao.findById(id);
    }

    public void addFaculty(User currentUser, Faculty faculty) throws StudentService.ValidationException {
        if (!currentUser.canManageFaculty()) {
            throw new SecurityException("Permission Denied: Only Administrators and Placement Officers can register faculty.");
        }

        validateFacultyFields(faculty, null);

        boolean success = facultyDao.insert(faculty);
        if (!success) {
            throw new RuntimeException("Database error occurred while adding faculty member.");
        }
    }

    public void updateFaculty(User currentUser, Faculty faculty) throws StudentService.ValidationException {
        if (!currentUser.canManageFaculty()) {
            throw new SecurityException("Permission Denied: Only Administrators and Placement Officers can update faculty records.");
        }

        validateFacultyFields(faculty, faculty.getId());

        boolean success = facultyDao.update(faculty);
        if (!success) {
            throw new RuntimeException("Database error occurred while updating faculty member.");
        }
    }

    public void toggleFacultyStatus(User currentUser, String id, String status) {
        if (!currentUser.canManageFaculty()) {
            throw new SecurityException("Permission Denied: Only Administrators and Placement Officers can change faculty status.");
        }

        boolean success = facultyDao.toggleStatus(id, status);
        if (!success) {
            throw new RuntimeException("Database error occurred while toggling faculty status.");
        }
    }

    public void deleteFaculty(User currentUser, String id) {
        if (!currentUser.canManageUsers()) { // Only Admin can delete faculty completely
            throw new SecurityException("Permission Denied: Only System Administrators can permanently delete faculty records.");
        }

        boolean success = facultyDao.delete(id);
        if (!success) {
            throw new RuntimeException("Database error occurred while deleting faculty record.");
        }
    }

    private void validateFacultyFields(Faculty f, String excludeId) throws StudentService.ValidationException {
        if (f == null) {
            throw new StudentService.ValidationException("Faculty payload cannot be empty.");
        }

        if (f.getFacultyId() == null || f.getFacultyId().trim().length() < 3) {
            throw new StudentService.ValidationException("Faculty ID is required (e.g. GCET-FAC-CSE-01).");
        }
        String facId = f.getFacultyId().trim().toUpperCase();
        if (facultyDao.isFacultyIdTaken(facId, excludeId)) {
            throw new StudentService.ValidationException("Faculty ID '" + facId + "' is already registered.");
        }
        f.setFacultyId(facId);

        if (f.getFullName() == null || f.getFullName().trim().length() < 2) {
            throw new StudentService.ValidationException("Full Name is required (minimum 2 characters).");
        }
        f.setFullName(f.getFullName().trim());

        if (f.getDepartment() == null || f.getDepartment().trim().isEmpty()) {
            throw new StudentService.ValidationException("Academic department is required.");
        }

        if (f.getDesignation() == null || f.getDesignation().trim().isEmpty()) {
            throw new StudentService.ValidationException("Academic designation is required.");
        }

        if (f.getEmail() == null || f.getEmail().trim().isEmpty()) {
            throw new StudentService.ValidationException("Institutional email is required.");
        }
        String em = f.getEmail().trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(em).matches()) {
            throw new StudentService.ValidationException("Invalid institutional email address format.");
        }
        if (facultyDao.isEmailTaken(em, excludeId)) {
            throw new StudentService.ValidationException("Email '" + em + "' is already registered to another faculty member.");
        }
        f.setEmail(em);
    }
}
