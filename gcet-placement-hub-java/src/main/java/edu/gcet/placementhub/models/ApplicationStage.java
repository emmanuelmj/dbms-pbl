package edu.gcet.placementhub.models;

/**
 * Canonical 10-stage recruitment pipeline enum + labels matching src/types/index.ts.
 */
public enum ApplicationStage {
    APPLICATION_SUBMITTED("Application Submitted"),
    APPLICATION_REVIEWED("Application Reviewed"),
    SHORTLISTED("Shortlisted"),
    ONLINE_ASSESSMENT("Online Assessment"),
    TECHNICAL_INTERVIEW("Technical Interview"),
    HR_INTERVIEW("HR Interview"),
    SELECTED("Selected"),
    OFFERED("Offered"),
    JOINED("Joined"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");

    private final String label;

    ApplicationStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isOfferOrSelected() {
        return this == SELECTED || this == OFFERED || this == JOINED;
    }

    public boolean isShortlistedOrBeyond() {
        return this == SHORTLISTED
                || this == ONLINE_ASSESSMENT
                || this == TECHNICAL_INTERVIEW
                || this == HR_INTERVIEW
                || this == SELECTED
                || this == OFFERED
                || this == JOINED;
    }

    public static ApplicationStage fromString(String value) {
        if (value == null || value.isBlank()) {
            return APPLICATION_SUBMITTED;
        }
        try {
            return ApplicationStage.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return APPLICATION_SUBMITTED;
        }
    }
}
