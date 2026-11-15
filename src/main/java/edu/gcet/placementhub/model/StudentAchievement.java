package edu.gcet.placementhub.model;

/**
 * Student achievement, award, workshop, or hackathon participation entry.
 */
public class StudentAchievement {
    private String id;
    private String studentId;
    private String userId;
    private String title;
    private String category = "AWARD"; // ACHIEVEMENT, AWARD, WORKSHOP, HACKATHON
    private String organizer;
    private String eventDate;
    private String description;
    private String credentialUrl;

    public StudentAchievement() {}

    public StudentAchievement(String id, String title, String category, String organizer, String eventDate, String description, String credentialUrl) {
        this.id = id;
        this.title = title;
        this.category = category != null && !category.isEmpty() ? category : "AWARD";
        this.organizer = organizer;
        this.eventDate = eventDate;
        this.description = description;
        this.credentialUrl = credentialUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getOrganizer() { return organizer; }
    public void setOrganizer(String organizer) { this.organizer = organizer; }

    public String getEventDate() { return eventDate; }
    public void setEventDate(String eventDate) { this.eventDate = eventDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCredentialUrl() { return credentialUrl; }
    public void setCredentialUrl(String credentialUrl) { this.credentialUrl = credentialUrl; }
}
