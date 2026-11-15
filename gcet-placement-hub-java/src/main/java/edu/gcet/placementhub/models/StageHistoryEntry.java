package edu.gcet.placementhub.models;

/**
 * Recruitment stage history entry mapped from `application_stage_history`.
 */
public class StageHistoryEntry {
    private ApplicationStage stage;
    private String timestamp;
    private String updatedBy;
    private String notes;

    public ApplicationStage getStage() { return stage; }
    public void setStage(ApplicationStage stage) { this.stage = stage; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
