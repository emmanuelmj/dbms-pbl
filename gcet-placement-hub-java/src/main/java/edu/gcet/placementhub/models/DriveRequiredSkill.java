package edu.gcet.placementhub.models;

/**
 * Required skill specification mapped from MySQL `drive_required_skills` table.
 */
public class DriveRequiredSkill {
    private String skillId;
    private int minProficiency;
    private boolean mandatory;

    public DriveRequiredSkill() {
    }

    public DriveRequiredSkill(String skillId, int minProficiency, boolean mandatory) {
        this.skillId = skillId;
        this.minProficiency = minProficiency;
        this.mandatory = mandatory;
    }

    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }

    public int getMinProficiency() { return minProficiency; }
    public void setMinProficiency(int minProficiency) { this.minProficiency = minProficiency; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
}
