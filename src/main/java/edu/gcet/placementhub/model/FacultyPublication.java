package edu.gcet.placementhub.model;

/**
 * Academic research publication entry for faculty profiles.
 */
public class FacultyPublication {
    private String id;
    private String facultyId;
    private String userId;
    private String title;
    private String journalOrConference;
    private String publicationYear;
    private String authors;
    private String doiOrUrl;

    public FacultyPublication() {}

    public FacultyPublication(String id, String title, String journalOrConference, String publicationYear, String authors, String doiOrUrl) {
        this.id = id;
        this.title = title;
        this.journalOrConference = journalOrConference;
        this.publicationYear = publicationYear;
        this.authors = authors;
        this.doiOrUrl = doiOrUrl;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFacultyId() { return facultyId; }
    public void setFacultyId(String facultyId) { this.facultyId = facultyId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getJournalOrConference() { return journalOrConference; }
    public void setJournalOrConference(String journalOrConference) { this.journalOrConference = journalOrConference; }

    public String getPublicationYear() { return publicationYear; }
    public void setPublicationYear(String publicationYear) { this.publicationYear = publicationYear; }

    public String getAuthors() { return authors; }
    public void setAuthors(String authors) { this.authors = authors; }

    public String getDoiOrUrl() { return doiOrUrl; }
    public void setDoiOrUrl(String doiOrUrl) { this.doiOrUrl = doiOrUrl; }
}
