package edu.gcet.placementhub.model;

/**
 * Academic or capstone engineering project attached to a student profile.
 */
public class StudentProject {
    private String id;
    private String studentId;
    private String userId;
    private String title;
    private String description;
    private String technologies;
    private String projectUrl;
    private String duration;

    public StudentProject() {}

    public StudentProject(String id, String title, String description, String technologies, String projectUrl, String duration) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.technologies = technologies;
        this.projectUrl = projectUrl;
        this.duration = duration;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTechnologies() { return technologies; }
    public void setTechnologies(String technologies) { this.technologies = technologies; }

    public String getProjectUrl() { return projectUrl; }
    public void setProjectUrl(String projectUrl) { this.projectUrl = projectUrl; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
}
