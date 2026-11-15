package edu.gcet.placementhub.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * GCET PLACEMENT HUB - Application Configuration
 * Geethanjali College of Engineering and Technology (Cheeryal, Keesara, Telangana)
 */
public class AppConfig {
    public static final String APP_NAME = "GCET PLACEMENT HUB";
    public static final String APP_TITLE = "Smart Placement Cell & Skill Matrix Portal";
    public static final String COLLEGE_NAME = "Geethanjali College of Engineering and Technology";
    public static final String COLLEGE_TAGLINE = "Your Skills. Your Opportunities. Your Future.";
    public static final String CAMPUS_LOCATION = "Cheeryal (V), Keesara (M), Telangana, India - PIN 501301";
    public static final String AUTONOMOUS_AFFILIATION = "UGC Autonomous · Affiliated to JNTUH · NAAC A+ · NBA Accredited";
    public static final String GCTC_PORTAL_URL = "https://www.gctcportal.in/";
    public static final String GCET_WEBSITE_URL = "https://gcet.edu.in/";

    // Official GCET Engineering Branches
    public static final List<String> BRANCHES = Collections.unmodifiableList(Arrays.asList(
            "CSE",
            "CSE - AIML",
            "CSE - DS",
            "CSE - Cyber Security",
            "IT",
            "ECE",
            "EEE",
            "MECH",
            "CIVIL"
    ));

    // Graduation Years
    public static final List<Integer> GRADUATION_YEARS = Collections.unmodifiableList(Arrays.asList(
            2024, 2025, 2026, 2027, 2028
    ));

    // Database Connection Parameters
    public static String getDbHost() {
        return System.getenv().getOrDefault("DB_HOST", "localhost");
    }

    public static int getDbPort() {
        try {
            return Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "3306"));
        } catch (NumberFormatException e) {
            return 3306;
        }
    }

    public static String getDbName() {
        return System.getenv().getOrDefault("DB_NAME", "gcet_placement_hub");
    }

    public static String getDbUser() {
        return System.getenv().getOrDefault("DB_USER", "root");
    }

    public static String getDbPassword() {
        return System.getenv().getOrDefault("DB_PASSWORD", "root");
    }
}
