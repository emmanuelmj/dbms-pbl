package edu.gcet.placementhub.model;

import java.sql.Timestamp;

/**
 * GCET PLACEMENT HUB - Skill Model
 */
public class Skill {
    private String id;
    private String name;
    private String category;
    private String description;
    private String status; // ACTIVE, INACTIVE
    private Timestamp createdAt;

    public Skill() {
        this.status = "ACTIVE";
    }

    public Skill(String id, String name, String category, String description) {
        this(id, name, category, description, "ACTIVE");
    }

    public Skill(String id, String name, String category, String description, String status) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.status = (status != null && !status.trim().isEmpty()) ? status.toUpperCase() : "ACTIVE";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
