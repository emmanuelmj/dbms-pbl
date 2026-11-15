package edu.gcet.placementhub.model;

/**
 * Custom social or professional web link belonging to any user profile.
 */
public class CustomLink {
    private String id;
    private String userId;
    private String title;
    private String url;
    private int displayOrder;

    public CustomLink() {}

    public CustomLink(String id, String title, String url) {
        this.id = id;
        this.title = title;
        this.url = url;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
