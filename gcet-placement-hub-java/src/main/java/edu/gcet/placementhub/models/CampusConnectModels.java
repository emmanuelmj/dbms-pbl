package edu.gcet.placementhub.models;

import java.util.List;

/**
 * Institutional Campus Connect data models matching src/types/campus.ts and campusConnectService.ts.
 */
public final class CampusConnectModels {

    private CampusConnectModels() {
    }

    public record PortalSyncStatus(
            boolean isOnline,
            String lastSyncTimestamp,
            String sourceUrl,
            String officialCollegeUrl,
            String sourceLabel,
            String statusMessage
    ) {}

    public record ExamResultItem(
            String id,
            String title,
            String examMonthYear,
            String releaseDate,
            List<String> regulations,
            List<String> branches,
            String portalUrl,
            String type
    ) {}

    public record CollegeClub(
            String id,
            String name,
            String shortName,
            String category,
            String description,
            List<String> activities,
            String facultyCoordinator,
            String studentLead,
            String portalUrl,
            String badge,
            String colorHex
    ) {}

    public record CollegeFestival(
            String id,
            String name,
            String tagline,
            String type,
            String description,
            List<String> highlights,
            String scheduleMonth,
            List<String> departments,
            String portalUrl
    ) {}

    public record StudentResource(
            String id,
            String title,
            String shortDescription,
            String category,
            String url,
            String actionText,
            boolean directPortalLink
    ) {}

    public record BranchIntake(
            String branchCode,
            String branchName,
            String degree,
            int sanctionedIntake,
            int establishedYear
    ) {}
}
