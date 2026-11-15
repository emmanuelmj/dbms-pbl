package edu.gcet.placementhub.model;

/**
 * Internship or industry work experience record on a student profile.
 */
public class StudentInternship {
    private String id;
    private String studentId;
    private String userId;
    private String companyName;
    private String role;
    private String duration;
    private String location;
    private String description;

    public StudentInternship() {}

    public StudentInternship(String id, String companyName, String role, String duration, String location, String description) {
        this.id = id;
        this.companyName = companyName;
        this.role = role;
        this.duration = duration;
        this.location = location;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
