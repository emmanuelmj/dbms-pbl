package edu.gcet.placementhub.utils;

import java.util.*;

/**
 * Exact Java port of server/db.ts normalizeDeptCode & FACULTY_ASSIGNMENTS.
 */
public final class DeptNormalizer {

    public static final List<String> GCET_BRANCHES = List.of(
            "CSE",
            "CSE - AIML",
            "CSE - DS",
            "CSE - Cyber Security",
            "IT",
            "ECE",
            "EEE",
            "MECH",
            "CIVIL"
    );

    private static final Map<String, List<String>> FACULTY_ASSIGNMENTS = Map.of(
            "faculty_lavanya", List.of("CSE", "CSE-AIML", "CSE-DS"),
            "usr-fac-1", List.of("CSE", "CSE-AIML", "CSE-DS"),
            "faculty_aparna", List.of("CSE-CS", "IT"),
            "usr-fac-2", List.of("CSE-CS", "IT"),
            "faculty_rajashakar", List.of("EEE", "ECE", "MECH", "CIVIL"),
            "usr-fac-3", List.of("EEE", "ECE", "MECH", "CIVIL")
    );

    private DeptNormalizer() {
    }

    public static String normalizeDeptCode(String dept) {
        if (dept == null || dept.isBlank()) return "";
        String s = dept.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (s.contains("AIML")) return "CSE-AIML";
        if (s.contains("DS") || s.contains("DATASCIENCE")) return "CSE-DS";
        if (s.contains("CYBER") || s.contains("SECURITY") || s.contains("CSE-CS") || s.contains("CSECS")) return "CSE-CS";
        if (s.equals("CSE")) return "CSE";
        if (s.equals("IT")) return "IT";
        if (s.equals("EEE")) return "EEE";
        if (s.equals("ECE")) return "ECE";
        if (s.equals("MECH") || s.equals("MECHANICAL")) return "MECH";
        if (s.equals("CIVIL")) return "CIVIL";
        return dept.trim();
    }

    public static List<String> getFacultyAuthorizedDepts(String facultyUsernameOrId) {
        if (facultyUsernameOrId == null || facultyUsernameOrId.isBlank()) return List.of();
        String key = facultyUsernameOrId.trim().toLowerCase(Locale.ROOT);
        if (FACULTY_ASSIGNMENTS.containsKey(key)) {
            return FACULTY_ASSIGNMENTS.get(key);
        }
        if (key.contains("lavanya")) return FACULTY_ASSIGNMENTS.get("faculty_lavanya");
        if (key.contains("aparna")) return FACULTY_ASSIGNMENTS.get("faculty_aparna");
        if (key.contains("rajashakar") || key.contains("raja")) return FACULTY_ASSIGNMENTS.get("faculty_rajashakar");
        return List.of();
    }

    public static boolean isFacultyAuthorizedForStudent(List<String> authorizedDepts, String facultyUsernameOrId, String studentBranch) {
        List<String> effective = (authorizedDepts != null && !authorizedDepts.isEmpty())
                ? authorizedDepts
                : getFacultyAuthorizedDepts(facultyUsernameOrId);
        if (effective.isEmpty()) return false;
        String normBranch = normalizeDeptCode(studentBranch);
        for (String d : effective) {
            if (normalizeDeptCode(d).equals(normBranch)) {
                return true;
            }
        }
        return false;
    }

    public static String assignedCoordinatorLabel(String branch) {
        String norm = normalizeDeptCode(branch);
        if (List.of("CSE", "CSE-AIML", "CSE-DS").contains(norm)) {
            return "B. Laxmi Lavanya (CSE / AIML / DS Coordinator)";
        } else if (List.of("CSE-CS", "IT").contains(norm)) {
            return "P. Aparna (Cyber Security & IT Coordinator)";
        } else if (List.of("EEE", "ECE", "MECH", "CIVIL").contains(norm)) {
            return "S. Raja Shakar Reddy (Core Engineering Coordinator)";
        }
        return "Department Faculty Coordinator";
    }
}
