package edu.gcet.placementhub.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Student project entry mapped from MySQL `student_projects` table.
 */
public class StudentProject {
    private String id;
    private String studentId;
    private String title;
    private String description;
    private List<String> technologies = new ArrayList<>();
    private String link;
    private String duration;
    private String createdAt;

    public StudentProject() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getTechnologies() { return technologies; }
    public void setTechnologies(List<String> technologies) {
        this.technologies = technologies != null ? technologies : new ArrayList<>();
    }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
