package edu.gcet.placementhub.service;

import edu.gcet.placementhub.dao.UserDao;
import edu.gcet.placementhub.model.User;

import java.util.List;
import java.util.regex.Pattern;

/**
 * GCET PLACEMENT HUB - User Management Service
 * Enforces Role-Based Access Control (RBAC), validation, and security workflows.
 */
public class UserService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private final UserDao userDao;

    public UserService() {
        this.userDao = new UserDao();
    }

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public List<User> getUsers(User currentUser, String search, String roleFilter, String statusFilter) {
        if (currentUser.getRole() != User.Role.ADMIN && currentUser.getRole() != User.Role.PLACEMENT_OFFICER) {
            throw new SecurityException("Permission Denied: Only Administrators and Placement Officers can view user directory.");
        }
        return userDao.findAll(search, roleFilter, statusFilter);
    }

    public User getUserById(User currentUser, String userId) {
        if (currentUser.getRole() != User.Role.ADMIN && !currentUser.getId().equals(userId)) {
            throw new SecurityException("Permission Denied: You are not authorized to view another user's account details.");
        }
        return userDao.findById(userId);
    }

    public void addUser(User currentUser, User newUser, String password) throws StudentService.ValidationException {
        // RBAC: Only ADMIN can create users
        if (!currentUser.canManageUsers()) {
            throw new SecurityException("Permission Denied: Only System Administrators can register user accounts.");
        }

        validateUserFields(newUser, password, null);

        boolean success = userDao.insert(newUser, password);
        if (!success) {
            throw new RuntimeException("Database error occurred while creating user.");
        }
    }

    public void updateUser(User currentUser, User user) throws StudentService.ValidationException {
        // RBAC
        if (!currentUser.canManageUsers() && !currentUser.getId().equals(user.getId())) {
            throw new SecurityException("Permission Denied: You cannot modify this user account.");
        }

        // Non-admin users cannot change their own role or active status
        if (!currentUser.canManageUsers()) {
            User existing = userDao.findById(user.getId());
            if (existing != null) {
                user.setRole(existing.getRole());
                user.setActive(existing.isActive());
            }
        }

        validateUserFields(user, null, user.getId());

        boolean success = userDao.update(user);
        if (!success) {
            throw new RuntimeException("Database error occurred while updating user.");
        }
    }

    public void resetPassword(User currentUser, String targetUserId, String newPassword) throws StudentService.ValidationException {
        if (!currentUser.canManageUsers() && !currentUser.getId().equals(targetUserId)) {
            throw new SecurityException("Permission Denied: Only Administrators can reset other users' passwords.");
        }

        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new StudentService.ValidationException("Password must be at least 6 characters long.");
        }

        boolean success = userDao.updatePassword(targetUserId, newPassword.trim());
        if (!success) {
            throw new RuntimeException("Database error occurred while resetting password.");
        }
    }

    public void toggleUserStatus(User currentUser, String targetUserId, boolean active) {
        if (!currentUser.canManageUsers()) {
            throw new SecurityException("Permission Denied: Only System Administrators can activate or deactivate accounts.");
        }

        if (currentUser.getId().equals(targetUserId) && !active) {
            throw new IllegalArgumentException("Safety Warning: You cannot deactivate your own administrative account.");
        }

        boolean success = userDao.toggleStatus(targetUserId, active);
        if (!success) {
            throw new RuntimeException("Database error occurred while toggling account status.");
        }
    }

    public void deleteUser(User currentUser, String targetUserId) {
        if (!currentUser.canManageUsers()) {
            throw new SecurityException("Permission Denied: Only System Administrators can delete user accounts.");
        }

        if (currentUser.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("Safety Warning: You cannot delete your own administrative account.");
        }

        boolean success = userDao.delete(targetUserId);
        if (!success) {
            throw new RuntimeException("Database error occurred while deleting user account.");
        }
    }

    private void validateUserFields(User u, String password, String excludeId) throws StudentService.ValidationException {
        if (u == null) {
            throw new StudentService.ValidationException("User payload cannot be empty.");
        }

        // Username
        if (u.getUsername() == null || u.getUsername().trim().length() < 3) {
            throw new StudentService.ValidationException("Username must be at least 3 characters long.");
        }
        String un = u.getUsername().trim().toLowerCase();
        if (un.contains(" ")) {
            throw new StudentService.ValidationException("Username cannot contain whitespace.");
        }
        if (userDao.isUsernameTaken(un, excludeId)) {
            throw new StudentService.ValidationException("Username '" + un + "' is already in use.");
        }
        u.setUsername(un);

        // Full Name
        if (u.getFullName() == null || u.getFullName().trim().length() < 2) {
            throw new StudentService.ValidationException("Full Name is required (minimum 2 characters).");
        }
        u.setFullName(u.getFullName().trim());

        // Email
        if (u.getEmail() == null || u.getEmail().trim().isEmpty()) {
            throw new StudentService.ValidationException("Email address is required.");
        }
        String em = u.getEmail().trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(em).matches()) {
            throw new StudentService.ValidationException("Invalid institutional email format.");
        }
        if (userDao.isEmailTaken(em, excludeId)) {
            throw new StudentService.ValidationException("Email '" + em + "' is already registered to another user.");
        }
        u.setEmail(em);

        // Role
        if (u.getRole() == null) {
            throw new StudentService.ValidationException("A valid institutional role must be assigned.");
        }

        // Password for new users
        if (excludeId == null) {
            if (password == null || password.trim().length() < 6) {
                throw new StudentService.ValidationException("Initial password must be at least 6 characters long.");
            }
        }
    }
}
