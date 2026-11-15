package edu.gcet.placementhub.models;

/**
 * Official GCTC Academic Circular / Archive announcement entry from campusConnectService.ts.
 */
public record GctcAnnouncement(
        String id,
        String title,
        String date,
        String category,
        String summary,
        String link,
        boolean important,
        String attachmentName,
        String postedBy
) {
}
