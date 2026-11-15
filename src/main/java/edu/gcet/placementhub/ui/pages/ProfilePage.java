package edu.gcet.placementhub.ui.pages;

import edu.gcet.placementhub.dao.ProfileDao;
import edu.gcet.placementhub.model.*;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * GCET PLACEMENT HUB - Unified Profile Management & Edit Profile Module (Java 17 Swing)
 * Supports STUDENT, FACULTY, COMPANY (Recruiter), and ADMIN/OFFICER/LEADERSHIP roles
 * with transactional MySQL 8 persistence, photo upload, URL validation, and completion tracking.
 */
public class ProfilePage extends JPanel {
    private static final int MAX_PHOTO_BYTES = 2 * 1024 * 1024; // 2 MB

    private final User currentUser;
    private final ProfileDao profileDao;
    private final Runnable onUserProfileUpdated;

    private UserProfileData loadedProfile;
    private UserProfileData workingProfile;
    private boolean isEditing = false;

    // Overview UI controls
    private JLabel lblPhotoPreview;
    private JLabel lblHeaderName;
    private JLabel lblHeaderSub;
    private JLabel lblHeaderBio;
    private JLabel lblCompletionText;
    private JProgressBar progressCompletion;
    private JButton btnUploadPhoto;
    private JButton btnRemovePhoto;
    private JButton btnEditProfile;
    private JButton btnSaveChanges;
    private JButton btnCancelEdit;
    private JButton btnRefresh;
    private JLabel lblStatusBanner;

    // Tabs container
    private JTabbedPane tabbedPane;

    // Common / Personal fields
    private JTextField txtFullName;
    private JTextField txtUsername;
    private JTextField txtEmail;
    private JTextField txtPhone;
    private JTextField txtDesignation;
    private JTextField txtDepartment;
    private JTextField txtLocation;
    private JTextField txtDob;
    private JComboBox<String> cmbGender;
    private JTextField txtAddress;
    private JTextField txtResumeUrl;
    private JTextArea txtAboutMe;

    // Student Academic fields
    private JTextField txtRollNumber;
    private JTextField txtStudentBranch;
    private JTextField txtApprovalStatus;
    private JTextField txtPlacementStatus;
    private JTextField txtSection;
    private JSpinner spnAcademicYear;
    private JSpinner spnCurrentSem;
    private JSpinner spnGradYear;
    private JTextField txtCgpa;
    private JSpinner spnBacklogs;
    private JTextField txtTenthPct;
    private JTextField txtInterPct;

    // Student Skills & Career fields
    private JTextField txtTechSkills;
    private JTextField txtProgLangs;
    private JTextField txtFrameworks;
    private JTextField txtSoftSkills;
    private JTextField txtCareerInterests;
    private JTextField txtPreferredRoles;
    private DefaultTableModel verifiedSkillsModel;

    // Faculty fields
    private JTextField txtFacultyId;
    private JTextField txtFacultyDept;
    private JTextField txtFacultyVerifyStatus;
    private JTextArea txtQualifications;
    private JTextField txtExpertise;
    private JTextField txtSubjectsHandled;
    private JTextArea txtResearchInterests;

    // Company / Recruiter fields
    private JLabel lblCompanyLogoPreview;
    private JButton btnUploadLogo;
    private JButton btnRemoveLogo;
    private JTextField txtCompanyName;
    private JTextField txtRecruiterName;
    private JTextField txtRecruiterDesignation;
    private JTextField txtCompanyIndustry;
    private JTextField txtCompanyWebsite;
    private JTextField txtCompanyStatusTier;
    private JTextArea txtCompanyDescription;

    // Social link fields
    private JTextField txtLinkedin;
    private JTextField txtGithub;
    private JTextField txtPortfolio;
    private JTextField txtLeetcode;
    private JTextField txtHackerrank;
    private JTextField txtCodechef;
    private JTextField txtGfg;
    private JTextField txtKaggle;
    private JTextField txtGoogleDev;
    private JPanel clickableSocialLinksPanel;

    // Dynamic table models
    private DefaultTableModel customLinksModel;
    private DefaultTableModel projectsModel;
    private DefaultTableModel internshipsModel;
    private DefaultTableModel certificationsModel;
    private DefaultTableModel achievementsModel;
    private DefaultTableModel publicationsModel;

    private final List<JButton> listActionButtons = new ArrayList<>();

    public ProfilePage(User currentUser, Runnable onUserProfileUpdated) {
        this.currentUser = currentUser;
        this.profileDao = new ProfileDao();
        this.onUserProfileUpdated = onUserProfileUpdated;
        initLayout();
        loadProfileAsync();
    }

    private void initLayout() {
        setLayout(new BorderLayout(14, 14));
        setBackground(Theme.LIGHT_BG);
        setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel topStack = new JPanel(new BorderLayout(10, 10));
        topStack.setOpaque(false);
        topStack.add(createOverviewHeaderCard(), BorderLayout.CENTER);

        lblStatusBanner = new JLabel(" ");
        lblStatusBanner.setFont(Theme.FONT_BOLD);
        lblStatusBanner.setBorder(new EmptyBorder(4, 10, 4, 10));
        lblStatusBanner.setVisible(false);
        lblStatusBanner.setOpaque(true);
        topStack.add(lblStatusBanner, BorderLayout.SOUTH);

        add(topStack, BorderLayout.NORTH);

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(Theme.FONT_BOLD);
        tabbedPane.setBackground(Color.WHITE);
        buildRoleTabs();
        add(tabbedPane, BorderLayout.CENTER);

        setEditMode(false);
    }

    private JPanel createOverviewHeaderCard() {
        JPanel card = new JPanel(new BorderLayout(18, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1),
                new EmptyBorder(16, 20, 16, 20)
        ));

        // Left: Photo + Upload/Remove controls
        JPanel photoPanel = new JPanel(new BorderLayout(0, 6));
        photoPanel.setOpaque(false);

        lblPhotoPreview = new JLabel("", JLabel.CENTER);
        lblPhotoPreview.setPreferredSize(new Dimension(92, 92));
        lblPhotoPreview.setOpaque(true);
        lblPhotoPreview.setBackground(Theme.MIDNIGHT_NAVY);
        lblPhotoPreview.setForeground(Theme.CYAN_ACCENT);
        lblPhotoPreview.setFont(new Font("Segoe UI", Font.BOLD, 28));
        lblPhotoPreview.setBorder(BorderFactory.createLineBorder(Theme.ELECTRIC_BLUE, 2));
        photoPanel.add(lblPhotoPreview, BorderLayout.CENTER);

        JPanel photoBtnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        photoBtnRow.setOpaque(false);
        btnUploadPhoto = Theme.createSecondaryButton("Upload Photo");
        btnUploadPhoto.setFont(Theme.FONT_SMALL);
        btnUploadPhoto.setMargin(new Insets(3, 6, 3, 6));
        btnUploadPhoto.addActionListener(e -> handleImageUpload("avatar"));

        btnRemovePhoto = Theme.createSecondaryButton("Clear");
        btnRemovePhoto.setFont(Theme.FONT_SMALL);
        btnRemovePhoto.setMargin(new Insets(3, 6, 3, 6));
        btnRemovePhoto.addActionListener(e -> handleImageRemove("avatar"));

        photoBtnRow.add(btnUploadPhoto);
        photoBtnRow.add(btnRemovePhoto);
        photoPanel.add(photoBtnRow, BorderLayout.SOUTH);
        card.add(photoPanel, BorderLayout.WEST);

        // Center: Identity + Bio + Completion Bar
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        lblHeaderName = new JLabel(currentUser != null ? currentUser.getFullName() : "User Profile");
        lblHeaderName.setFont(Theme.FONT_HEADER_LARGE);
        lblHeaderName.setForeground(Theme.MIDNIGHT_NAVY);

        lblHeaderSub = new JLabel(" ");
        lblHeaderSub.setFont(Theme.FONT_BODY);
        lblHeaderSub.setForeground(Theme.DEEP_BLUE);

        lblHeaderBio = new JLabel(" ");
        lblHeaderBio.setFont(Theme.FONT_SMALL);
        lblHeaderBio.setForeground(Color.DARK_GRAY);

        JPanel compRow = new JPanel(new BorderLayout(10, 4));
        compRow.setOpaque(false);
        compRow.setMaximumSize(new Dimension(560, 42));
        compRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblCompletionText = new JLabel("Profile Completion: 0%");
        lblCompletionText.setFont(Theme.FONT_BOLD);
        lblCompletionText.setForeground(Theme.MIDNIGHT_NAVY);

        progressCompletion = new JProgressBar(0, 100);
        progressCompletion.setPreferredSize(new Dimension(260, 14));
        progressCompletion.setStringPainted(true);
        progressCompletion.setForeground(Theme.ELECTRIC_BLUE);

        compRow.add(lblCompletionText, BorderLayout.NORTH);
        compRow.add(progressCompletion, BorderLayout.CENTER);

        lblHeaderName.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblHeaderSub.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblHeaderBio.setAlignmentX(Component.LEFT_ALIGNMENT);

        center.add(lblHeaderName);
        center.add(Box.createVerticalStrut(3));
        center.add(lblHeaderSub);
        center.add(Box.createVerticalStrut(4));
        center.add(lblHeaderBio);
        center.add(Box.createVerticalStrut(8));
        center.add(compRow);

        card.add(center, BorderLayout.CENTER);

        // Right: Edit / Save / Cancel / Refresh buttons
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        actions.setOpaque(false);

        btnRefresh = Theme.createSecondaryButton("↻ Refresh");
        btnRefresh.addActionListener(e -> loadProfileAsync());

        btnEditProfile = Theme.createPrimaryButton("✏️ Edit Profile");
        btnEditProfile.addActionListener(e -> setEditMode(true));

        btnSaveChanges = Theme.createPrimaryButton("💾 Save Changes");
        btnSaveChanges.setBackground(Theme.SUCCESS_GREEN);
        btnSaveChanges.addActionListener(e -> saveProfileAsync());

        btnCancelEdit = Theme.createSecondaryButton("✖ Cancel");
        btnCancelEdit.addActionListener(e -> cancelEdit());

        actions.add(btnRefresh);
        actions.add(btnEditProfile);
        actions.add(btnSaveChanges);
        actions.add(btnCancelEdit);
        card.add(actions, BorderLayout.EAST);

        return card;
    }

    private void buildRoleTabs() {
        tabbedPane.removeAll();
        listActionButtons.clear();

        User.Role role = currentUser != null && currentUser.getRole() != null ? currentUser.getRole() : User.Role.STUDENT;

        if (role == User.Role.STUDENT) {
            tabbedPane.addTab("Personal Info", createPersonalTab(true));
            tabbedPane.addTab("Academic Info", createStudentAcademicTab());
            tabbedPane.addTab("Skills & Career", createStudentSkillsTab());
            tabbedPane.addTab("Projects", createProjectsTab());
            tabbedPane.addTab("Experience", createInternshipsTab());
            tabbedPane.addTab("Certifications & Awards", createCertsAndAchievementsTab());
            tabbedPane.addTab("Social Links", createSocialLinksTab());
        } else if (role == User.Role.FACULTY) {
            tabbedPane.addTab("Personal & Bio", createPersonalTab(false));
            tabbedPane.addTab("Expertise & Teaching", createFacultyExpertiseTab());
            tabbedPane.addTab("Publications", createPublicationsTab());
            tabbedPane.addTab("Certifications", createFacultyCertificationsTab());
            tabbedPane.addTab("Social Links", createSocialLinksTab());
        } else if (role == User.Role.COMPANY) {
            tabbedPane.addTab("Recruiter & Contact", createPersonalTab(false));
            tabbedPane.addTab("Company Details", createCompanyDetailsTab());
            tabbedPane.addTab("Social & Web Links", createSocialLinksTab());
        } else {
            tabbedPane.addTab("Personal & Role", createPersonalTab(false));
            tabbedPane.addTab("Social Links", createSocialLinksTab());
        }
    }

    // =========================================================================
    // TAB BUILDERS
    // =========================================================================

    private JScrollPane createPersonalTab(boolean isStudent) {
        JPanel panel = createFormContainer();
        GridBagConstraints gbc = createGbc();

        txtFullName = new JTextField();
        txtUsername = createReadOnlyField();
        txtEmail = new JTextField();
        txtPhone = new JTextField();
        txtDesignation = new JTextField();
        txtDepartment = createReadOnlyField();
        txtLocation = new JTextField();
        txtDob = new JTextField();
        cmbGender = new JComboBox<>(new String[]{"", "Male", "Female", "Non-Binary", "Prefer not to say"});
        txtAddress = new JTextField();
        txtResumeUrl = new JTextField();
        txtAboutMe = new JTextArea(4, 30);
        txtAboutMe.setLineWrap(true);
        txtAboutMe.setWrapStyleWord(true);

        int r = 0;
        addFieldRow(panel, gbc, r++, "Full Name *", txtFullName, "Username (Read-Only)", txtUsername);
        addFieldRow(panel, gbc, r++, "Email Address *", txtEmail, "Phone Number", txtPhone);

        if (isStudent) {
            addFieldRow(panel, gbc, r++, "Department (Verified)", txtDepartment, "Location (City, State)", txtLocation);
            addFieldRow(panel, gbc, r++, "Date of Birth (YYYY-MM-DD)", txtDob, "Gender (Optional)", cmbGender);
            addSingleFieldRow(panel, gbc, r++, "Residential Address", txtAddress);
            addSingleFieldRow(panel, gbc, r++, "Resume / CV URL (https://...)", txtResumeUrl);
        } else {
            addFieldRow(panel, gbc, r++, "Designation / Title", txtDesignation, "Department (Institutional)", txtDepartment);
            addSingleFieldRow(panel, gbc, r++, "Location (City / Campus)", txtLocation);
        }

        addTextAreaRow(panel, gbc, r++, "About Me / Professional Introduction", txtAboutMe);
        addFiller(panel, gbc, r);

        return wrapScroll(panel);
    }

    private JScrollPane createStudentAcademicTab() {
        JPanel panel = createFormContainer();
        GridBagConstraints gbc = createGbc();

        txtRollNumber = createReadOnlyField();
        txtStudentBranch = createReadOnlyField();
        txtApprovalStatus = createReadOnlyField();
        txtPlacementStatus = createReadOnlyField();
        txtSection = new JTextField();
        spnAcademicYear = new JSpinner(new SpinnerNumberModel(4, 1, 4, 1));
        spnCurrentSem = new JSpinner(new SpinnerNumberModel(7, 1, 8, 1));
        spnGradYear = new JSpinner(new SpinnerNumberModel(2026, 2020, 2035, 1));
        txtCgpa = new JTextField();
        spnBacklogs = new JSpinner(new SpinnerNumberModel(0, 0, 20, 1));
        txtTenthPct = new JTextField();
        txtInterPct = new JTextField();

        JLabel note = new JLabel("Note: Roll Number, Department/Branch, Approval Status, and Placement Status are verified institutional fields and cannot be freely modified.");
        note.setFont(Theme.FONT_SMALL);
        note.setForeground(Theme.DEEP_BLUE);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4;
        panel.add(note, gbc);
        gbc.gridwidth = 1;

        int r = 1;
        addFieldRow(panel, gbc, r++, "Roll Number (Read-Only)", txtRollNumber, "Department (Verified)", txtStudentBranch);
        addFieldRow(panel, gbc, r++, "Approval Status (Protected)", txtApprovalStatus, "Placement Status (Protected)", txtPlacementStatus);
        addFieldRow(panel, gbc, r++, "Section", txtSection, "Academic Year (1-4)", spnAcademicYear);
        addFieldRow(panel, gbc, r++, "Current Semester (1-8)", spnCurrentSem, "Graduation Year", spnGradYear);
        addFieldRow(panel, gbc, r++, "CGPA (0.00 - 10.00)", txtCgpa, "Active Backlogs", spnBacklogs);
        addFieldRow(panel, gbc, r++, "10th SSC Percentage (%)", txtTenthPct, "Inter / Diploma (%)", txtInterPct);
        addFiller(panel, gbc, r);

        return wrapScroll(panel);
    }

    private JScrollPane createStudentSkillsTab() {
        JPanel panel = createFormContainer();
        GridBagConstraints gbc = createGbc();

        txtTechSkills = new JTextField();
        txtProgLangs = new JTextField();
        txtFrameworks = new JTextField();
        txtSoftSkills = new JTextField();
        txtCareerInterests = new JTextField();
        txtPreferredRoles = new JTextField();

        int r = 0;
        addSingleFieldRow(panel, gbc, r++, "Programming Languages (comma-separated)", txtProgLangs);
        addSingleFieldRow(panel, gbc, r++, "Frameworks & Technologies (comma-separated)", txtFrameworks);
        addSingleFieldRow(panel, gbc, r++, "Technical Skills & Core Domains (comma-separated)", txtTechSkills);
        addSingleFieldRow(panel, gbc, r++, "Soft Skills (comma-separated)", txtSoftSkills);
        addSingleFieldRow(panel, gbc, r++, "Career Interests (comma-separated)", txtCareerInterests);
        addSingleFieldRow(panel, gbc, r++, "Preferred Job Roles (comma-separated)", txtPreferredRoles);

        JLabel lblMatrix = new JLabel("Faculty-Verified Technical Skill Matrix (Protected Institutional Assessment)");
        lblMatrix.setFont(Theme.FONT_BOLD);
        lblMatrix.setForeground(Theme.MIDNIGHT_NAVY);
        gbc.gridx = 0; gbc.gridy = r++; gbc.gridwidth = 4;
        panel.add(lblMatrix, gbc);

        verifiedSkillsModel = new DefaultTableModel(new String[]{"Skill", "Category", "Proficiency (1-5)", "Verification"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable tblVerified = new JTable(verifiedSkillsModel);
        JScrollPane sp = new JScrollPane(tblVerified);
        sp.setPreferredSize(new Dimension(600, 150));
        gbc.gridx = 0; gbc.gridy = r++; gbc.gridwidth = 4; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        panel.add(sp, gbc);

        return wrapScroll(panel);
    }

    private JPanel createProjectsTab() {
        projectsModel = new DefaultTableModel(new String[]{"Title", "Technologies", "Duration", "Project Link", "Description"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(projectsModel);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(Color.WHITE);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);
        JButton btnAdd = Theme.createPrimaryButton("+ Add Project");
        JButton btnEdit = Theme.createSecondaryButton("Edit Selected");
        JButton btnDel = Theme.createDangerButton("Remove");
        JButton btnOpen = Theme.createSecondaryButton("Open Project Link");

        listActionButtons.add(btnAdd);
        listActionButtons.add(btnEdit);
        listActionButtons.add(btnDel);

        btnAdd.addActionListener(e -> openProjectDialog(-1));
        btnEdit.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) openProjectDialog(row);
        });
        btnDel.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && workingProfile != null) {
                workingProfile.getProjects().remove(row);
                refreshProjectsTable();
            }
        });
        btnOpen.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && workingProfile != null && row < workingProfile.getProjects().size()) {
                openUrlInBrowser(workingProfile.getProjects().get(row).getProjectUrl());
            }
        });

        toolbar.add(btnAdd);
        toolbar.add(btnEdit);
        toolbar.add(btnDel);
        toolbar.add(btnOpen);
        root.add(toolbar, BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        return root;
    }

    private JPanel createInternshipsTab() {
        internshipsModel = new DefaultTableModel(new String[]{"Company", "Role", "Duration", "Location", "Description"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(internshipsModel);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(Color.WHITE);
        root.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);
        JButton btnAdd = Theme.createPrimaryButton("+ Add Internship / Work Experience");
        JButton btnEdit = Theme.createSecondaryButton("Edit Selected");
        JButton btnDel = Theme.createDangerButton("Remove");

        listActionButtons.add(btnAdd);
        listActionButtons.add(btnEdit);
        listActionButtons.add(btnDel);

        btnAdd.addActionListener(e -> openInternshipDialog(-1));
        btnEdit.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) openInternshipDialog(row);
        });
        btnDel.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && workingProfile != null) {
                workingProfile.getInternships().remove(row);
                refreshInternshipsTable();
            }
        });

        toolbar.add(btnAdd);
        toolbar.add(btnEdit);
        toolbar.add(btnDel);
        root.add(toolbar, BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);
        return root;
    }

    private JPanel createCertsAndAchievementsTab() {
        JPanel split = new JPanel(new GridLayout(2, 1, 12, 12));
        split.setBackground(Theme.LIGHT_BG);
        split.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top: Certifications
        certificationsModel = new DefaultTableModel(new String[]{"Certificate Name", "Issuing Organization", "Issue Date", "Credential ID", "Credential URL"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tblCerts = new JTable(certificationsModel);
        JPanel certPanel = createSectionTableCard("Certifications & Credentials", tblCerts,
                () -> openCertificationDialog(-1),
                () -> { int r = tblCerts.getSelectedRow(); if (r >= 0) openCertificationDialog(r); },
                () -> { int r = tblCerts.getSelectedRow(); if (r >= 0 && workingProfile != null) { workingProfile.getCertifications().remove(r); refreshCertificationsTable(); } },
                () -> { int r = tblCerts.getSelectedRow(); if (r >= 0 && workingProfile != null) openUrlInBrowser(workingProfile.getCertifications().get(r).getCredentialUrl()); }
        );

        // Bottom: Achievements, Awards, Workshops & Hackathons
        achievementsModel = new DefaultTableModel(new String[]{"Title", "Category", "Organizer", "Date", "Description", "URL"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tblAch = new JTable(achievementsModel);
        JPanel achPanel = createSectionTableCard("Achievements, Awards, Workshops & Hackathons", tblAch,
                () -> openAchievementDialog(-1),
                () -> { int r = tblAch.getSelectedRow(); if (r >= 0) openAchievementDialog(r); },
                () -> { int r = tblAch.getSelectedRow(); if (r >= 0 && workingProfile != null) { workingProfile.getAchievements().remove(r); refreshAchievementsTable(); } },
                () -> { int r = tblAch.getSelectedRow(); if (r >= 0 && workingProfile != null) openUrlInBrowser(workingProfile.getAchievements().get(r).getCredentialUrl()); }
        );

        split.add(certPanel);
        split.add(achPanel);
        return split;
    }

    private JScrollPane createFacultyExpertiseTab() {
        JPanel panel = createFormContainer();
        GridBagConstraints gbc = createGbc();

        txtFacultyId = createReadOnlyField();
        txtFacultyDept = createReadOnlyField();
        txtFacultyVerifyStatus = createReadOnlyField();
        txtQualifications = new JTextArea(3, 30);
        txtQualifications.setLineWrap(true);
        txtQualifications.setWrapStyleWord(true);
        txtExpertise = new JTextField();
        txtSubjectsHandled = new JTextField();
        txtResearchInterests = new JTextArea(3, 30);
        txtResearchInterests.setLineWrap(true);
        txtResearchInterests.setWrapStyleWord(true);

        int r = 0;
        addFieldRow(panel, gbc, r++, "Faculty / Employee ID (Verified)", txtFacultyId, "Department (Verified)", txtFacultyDept);
        addSingleFieldRow(panel, gbc, r++, "Institutional Verification Status (Protected)", txtFacultyVerifyStatus);
        addTextAreaRow(panel, gbc, r++, "Academic Qualifications (e.g. Ph.D CSE, M.Tech)", txtQualifications);
        addSingleFieldRow(panel, gbc, r++, "Areas of Expertise (comma-separated)", txtExpertise);
        addSingleFieldRow(panel, gbc, r++, "Subjects Handled (comma-separated)", txtSubjectsHandled);
        addTextAreaRow(panel, gbc, r++, "Research Interests", txtResearchInterests);
        addFiller(panel, gbc, r);

        return wrapScroll(panel);
    }

    private JPanel createPublicationsTab() {
        publicationsModel = new DefaultTableModel(new String[]{"Publication Title", "Journal / Conference", "Year", "Authors", "DOI / URL"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tbl = new JTable(publicationsModel);
        return createSectionTableCard("Research Publications & Papers", tbl,
                () -> openPublicationDialog(-1),
                () -> { int r = tbl.getSelectedRow(); if (r >= 0) openPublicationDialog(r); },
                () -> { int r = tbl.getSelectedRow(); if (r >= 0 && workingProfile != null) { workingProfile.getPublications().remove(r); refreshPublicationsTable(); } },
                () -> { int r = tbl.getSelectedRow(); if (r >= 0 && workingProfile != null) openUrlInBrowser(workingProfile.getPublications().get(r).getDoiOrUrl()); }
        );
    }

    private JPanel createFacultyCertificationsTab() {
        certificationsModel = new DefaultTableModel(new String[]{"Certificate Name", "Issuing Organization", "Issue Date", "Credential ID", "Credential URL"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tblCerts = new JTable(certificationsModel);
        return createSectionTableCard("Faculty Certifications & FDPs", tblCerts,
                () -> openCertificationDialog(-1),
                () -> { int r = tblCerts.getSelectedRow(); if (r >= 0) openCertificationDialog(r); },
                () -> { int r = tblCerts.getSelectedRow(); if (r >= 0 && workingProfile != null) { workingProfile.getCertifications().remove(r); refreshCertificationsTable(); } },
                () -> { int r = tblCerts.getSelectedRow(); if (r >= 0 && workingProfile != null) openUrlInBrowser(workingProfile.getCertifications().get(r).getCredentialUrl()); }
        );
    }

    private JScrollPane createCompanyDetailsTab() {
        JPanel panel = createFormContainer();
        GridBagConstraints gbc = createGbc();

        lblCompanyLogoPreview = new JLabel("LOGO", JLabel.CENTER);
        lblCompanyLogoPreview.setPreferredSize(new Dimension(84, 84));
        lblCompanyLogoPreview.setOpaque(true);
        lblCompanyLogoPreview.setBackground(Theme.LIGHT_BG);
        lblCompanyLogoPreview.setBorder(BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1));

        btnUploadLogo = Theme.createSecondaryButton("Upload Company Logo");
        btnUploadLogo.addActionListener(e -> handleImageUpload("company_logo"));
        btnRemoveLogo = Theme.createSecondaryButton("Clear Logo");
        btnRemoveLogo.addActionListener(e -> handleImageRemove("company_logo"));

        JPanel logoStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        logoStrip.setOpaque(false);
        logoStrip.add(lblCompanyLogoPreview);
        logoStrip.add(btnUploadLogo);
        logoStrip.add(btnRemoveLogo);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4;
        panel.add(logoStrip, gbc);
        gbc.gridwidth = 1;

        txtCompanyName = new JTextField();
        txtCompanyIndustry = new JTextField();
        txtRecruiterName = new JTextField();
        txtRecruiterDesignation = new JTextField();
        txtCompanyWebsite = new JTextField();
        txtCompanyStatusTier = createReadOnlyField();
        txtCompanyDescription = new JTextArea(4, 30);
        txtCompanyDescription.setLineWrap(true);
        txtCompanyDescription.setWrapStyleWord(true);

        int r = 1;
        addFieldRow(panel, gbc, r++, "Company Name *", txtCompanyName, "Industry *", txtCompanyIndustry);
        addFieldRow(panel, gbc, r++, "Recruiter Name", txtRecruiterName, "Recruiter Designation", txtRecruiterDesignation);
        addFieldRow(panel, gbc, r++, "Company Website (https://...)", txtCompanyWebsite, "Verification & Tier (Protected)", txtCompanyStatusTier);
        addTextAreaRow(panel, gbc, r++, "Company Description", txtCompanyDescription);
        addFiller(panel, gbc, r);

        return wrapScroll(panel);
    }

    private JScrollPane createSocialLinksTab() {
        JPanel wrapper = new JPanel(new BorderLayout(12, 12));
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(new EmptyBorder(16, 20, 16, 20));

        // Top: Active Non-Empty Clickable Buttons Bar
        JPanel topLinksBox = new JPanel(new BorderLayout(6, 6));
        topLinksBox.setBackground(Theme.LIGHT_BG);
        topLinksBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1),
                new EmptyBorder(10, 12, 10, 12)
        ));
        JLabel lblActiveTitle = new JLabel("Active Profile Links (Click to Open in Browser — Empty Links Are Automatically Hidden)");
        lblActiveTitle.setFont(Theme.FONT_BOLD);
        lblActiveTitle.setForeground(Theme.MIDNIGHT_NAVY);
        topLinksBox.add(lblActiveTitle, BorderLayout.NORTH);

        clickableSocialLinksPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        clickableSocialLinksPanel.setOpaque(false);
        topLinksBox.add(clickableSocialLinksPanel, BorderLayout.CENTER);

        wrapper.add(topLinksBox, BorderLayout.NORTH);

        // Middle: Editable platform fields
        JPanel form = createFormContainer();
        form.setBorder(new EmptyBorder(10, 0, 10, 0));
        GridBagConstraints gbc = createGbc();

        txtLinkedin = new JTextField();
        txtGithub = new JTextField();
        txtPortfolio = new JTextField();
        txtLeetcode = new JTextField();
        txtHackerrank = new JTextField();
        txtCodechef = new JTextField();
        txtGfg = new JTextField();
        txtKaggle = new JTextField();
        txtGoogleDev = new JTextField();

        int r = 0;
        addFieldRow(panelOr(form), gbc, r++, "LinkedIn Profile URL", txtLinkedin, "GitHub Profile URL", txtGithub);
        addFieldRow(panelOr(form), gbc, r++, "Personal Portfolio Website", txtPortfolio, "LeetCode Profile", txtLeetcode);
        addFieldRow(panelOr(form), gbc, r++, "HackerRank Profile", txtHackerrank, "CodeChef Profile", txtCodechef);
        addFieldRow(panelOr(form), gbc, r++, "GeeksforGeeks Profile", txtGfg, "Kaggle Profile", txtKaggle);
        addSingleFieldRow(panelOr(form), gbc, r++, "Google Developer Profile", txtGoogleDev);

        // Bottom: Custom Links Table
        customLinksModel = new DefaultTableModel(new String[]{"Link Title", "URL"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        JTable tblCustom = new JTable(customLinksModel);
        JPanel customCard = createSectionTableCard("Other Custom Professional / Social Links", tblCustom,
                () -> openCustomLinkDialog(-1),
                () -> { int row = tblCustom.getSelectedRow(); if (row >= 0) openCustomLinkDialog(row); },
                () -> { int row = tblCustom.getSelectedRow(); if (row >= 0 && workingProfile != null) { workingProfile.getCustomLinks().remove(row); refreshCustomLinksTable(); rebuildClickableSocialButtons(); } },
                () -> { int row = tblCustom.getSelectedRow(); if (row >= 0 && workingProfile != null) openUrlInBrowser(workingProfile.getCustomLinks().get(row).getUrl()); }
        );
        customCard.setPreferredSize(new Dimension(600, 190));

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.setOpaque(false);
        center.add(form, BorderLayout.NORTH);
        center.add(customCard, BorderLayout.CENTER);
        wrapper.add(center, BorderLayout.CENTER);

        return wrapScroll(wrapper);
    }

    private JPanel panelOr(JPanel p) { return p; }

    private JPanel createSectionTableCard(
            String title,
            JTable table,
            Runnable onAdd,
            Runnable onEdit,
            Runnable onRemove,
            Runnable onOpenUrl
    ) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1),
                new EmptyBorder(10, 12, 10, 12)
        ));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel lbl = new JLabel(title);
        lbl.setFont(Theme.FONT_BOLD);
        lbl.setForeground(Theme.MIDNIGHT_NAVY);
        head.add(lbl, BorderLayout.WEST);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        JButton bAdd = Theme.createPrimaryButton("+ Add");
        JButton bEdit = Theme.createSecondaryButton("Edit");
        JButton bDel = Theme.createDangerButton("Remove");
        listActionButtons.add(bAdd);
        listActionButtons.add(bEdit);
        listActionButtons.add(bDel);

        bAdd.addActionListener(e -> onAdd.run());
        bEdit.addActionListener(e -> onEdit.run());
        bDel.addActionListener(e -> onRemove.run());
        btns.add(bAdd);
        btns.add(bEdit);
        btns.add(bDel);

        if (onOpenUrl != null) {
            JButton bOpen = Theme.createSecondaryButton("Open URL");
            bOpen.addActionListener(e -> onOpenUrl.run());
            btns.add(bOpen);
        }

        head.add(btns, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(new JScrollPane(table), BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // DATA LOADING & SAVING (ASYNC SWINGWORKER + MYSQL TRANSACTIONS)
    // =========================================================================

    public void loadProfileAsync() {
        if (currentUser == null || currentUser.getId() == null) return;
        setBanner(null, false);
        btnRefresh.setEnabled(false);

        SwingWorker<UserProfileData, Void> worker = new SwingWorker<>() {
            @Override
            protected UserProfileData doInBackground() throws Exception {
                return profileDao.loadFullProfile(currentUser.getId());
            }

            @Override
            protected void done() {
                btnRefresh.setEnabled(true);
                try {
                    UserProfileData data = get();
                    if (data != null) {
                        loadedProfile = data;
                        workingProfile = cloneProfileData(data);
                        populateFormFromProfile(workingProfile);
                        setEditMode(false);
                    } else {
                        setBanner("Profile record could not be loaded from gcet_placement_hub.", true);
                    }
                } catch (Exception ex) {
                    setBanner("Database load error: " + ex.getMessage(), true);
                }
            }
        };
        worker.execute();
    }

    private void saveProfileAsync() {
        if (workingProfile == null || currentUser == null) return;
        try {
            collectFormIntoWorkingProfile();
            ProfileDao.validateProfileData(currentUser, workingProfile);
        } catch (Exception valEx) {
            setBanner(valEx.getMessage(), true);
            JOptionPane.showMessageDialog(this, valEx.getMessage(), "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnSaveChanges.setEnabled(false);
        btnCancelEdit.setEnabled(false);
        setBanner(null, false);

        SwingWorker<UserProfileData, Void> worker = new SwingWorker<>() {
            @Override
            protected UserProfileData doInBackground() throws Exception {
                return profileDao.saveFullProfile(currentUser, workingProfile);
            }

            @Override
            protected void done() {
                btnSaveChanges.setEnabled(true);
                btnCancelEdit.setEnabled(true);
                try {
                    UserProfileData saved = get();
                    loadedProfile = saved;
                    workingProfile = cloneProfileData(saved);
                    if (saved.getUser() != null) {
                        currentUser.setFullName(saved.getUser().getFullName());
                        currentUser.setEmail(saved.getUser().getEmail());
                    }
                    populateFormFromProfile(workingProfile);
                    setEditMode(false);
                    setBanner("Profile changes saved permanently to MySQL database gcet_placement_hub.", false);
                    if (onUserProfileUpdated != null) {
                        onUserProfileUpdated.run();
                    }
                    JOptionPane.showMessageDialog(ProfilePage.this,
                            "Your profile has been updated and saved permanently to MySQL.",
                            "Profile Saved", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    setBanner("Failed to save profile: " + msg, true);
                    JOptionPane.showMessageDialog(ProfilePage.this, msg, "Save Failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void cancelEdit() {
        if (loadedProfile != null) {
            workingProfile = cloneProfileData(loadedProfile);
            populateFormFromProfile(workingProfile);
        }
        setBanner(null, false);
        setEditMode(false);
    }

    // =========================================================================
    // PHOTO / LOGO UPLOAD & VALIDATION
    // =========================================================================

    private void handleImageUpload(String target) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("company_logo".equals(target) ? "Select Company Logo (PNG, JPG, WEBP - Max 2MB)" : "Select Profile Photo (PNG, JPG, WEBP - Max 2MB)");
        chooser.setFileFilter(new FileNameExtensionFilter("Image Files (PNG, JPG, JPEG, WEBP)", "png", "jpg", "jpeg", "webp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            if (file.length() > MAX_PHOTO_BYTES) {
                throw new IllegalArgumentException("Selected image exceeds the 2 MB size limit.");
            }
            String lower = file.getName().toLowerCase();
            String ext = lower.endsWith(".png") ? "png" : (lower.endsWith(".webp") ? "webp" : "jpeg");

            byte[] rawBytes = Files.readAllBytes(file.toPath());
            String dataUrl;
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (img != null && !ext.equals("webp")) {
                BufferedImage scaled = scaleImage(img, 420);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(scaled, ext.equals("png") ? "png" : "jpg", baos);
                dataUrl = "data:image/" + ext + ";base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
            } else {
                dataUrl = "data:image/" + ext + ";base64," + Base64.getEncoder().encodeToString(rawBytes);
            }

            if (workingProfile != null) {
                if ("company_logo".equals(target) && workingProfile.getCompany() != null) {
                    workingProfile.getCompany().setCompanyLogo(dataUrl);
                    renderPhotoOnLabel(lblCompanyLogoPreview, dataUrl, "LOGO", 84);
                } else {
                    workingProfile.setProfilePhoto(dataUrl);
                    renderPhotoOnLabel(lblPhotoPreview, dataUrl, getInitials(workingProfile.getUser().getFullName()), 92);
                }
            }

            if (!isEditing) {
                profileDao.updateProfilePhoto(currentUser, target, dataUrl);
                loadProfileAsync();
                setBanner("Image uploaded and saved to MySQL.", false);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Image error: " + ex.getMessage(), "Upload Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleImageRemove(String target) {
        if (workingProfile == null) return;
        if ("company_logo".equals(target) && workingProfile.getCompany() != null) {
            workingProfile.getCompany().setCompanyLogo(null);
            renderPhotoOnLabel(lblCompanyLogoPreview, null, "LOGO", 84);
        } else {
            workingProfile.setProfilePhoto(null);
            renderPhotoOnLabel(lblPhotoPreview, null, getInitials(workingProfile.getUser().getFullName()), 92);
        }

        if (!isEditing) {
            try {
                profileDao.updateProfilePhoto(currentUser, target, null);
                loadProfileAsync();
                setBanner("Photo removed.", false);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private BufferedImage scaleImage(BufferedImage src, int maxDim) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= maxDim && h <= maxDim) return src;
        double scale = Math.min((double) maxDim / w, (double) maxDim / h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private void renderPhotoOnLabel(JLabel target, String dataUrl, String fallbackText, int boxSize) {
        if (target == null) return;
        target.setIcon(null);
        if (dataUrl != null && dataUrl.contains("base64,")) {
            try {
                String b64 = dataUrl.substring(dataUrl.indexOf("base64,") + 7);
                byte[] bytes = Base64.getDecoder().decode(b64);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    Image scaled = img.getScaledInstance(boxSize - 6, boxSize - 6, Image.SCALE_SMOOTH);
                    target.setIcon(new ImageIcon(scaled));
                    target.setText("");
                    return;
                }
            } catch (Exception ignored) {}
        }
        target.setText(fallbackText);
    }

    // =========================================================================
    // FORM POPULATION & COLLECTION
    // =========================================================================

    private void populateFormFromProfile(UserProfileData p) {
        if (p == null || p.getUser() == null) return;
        User u = p.getUser();

        lblHeaderName.setText(nonNull(u.getFullName()));
        String deptStr = !isBlank(u.getDepartment()) ? u.getDepartment() : "GCET";
        lblHeaderSub.setText("@" + nonNull(u.getUsername()) + "  ·  " + u.getRole().name() + "  ·  " + deptStr
                + (!isBlank(p.getLocation()) ? "  ·  " + p.getLocation() : ""));
        lblHeaderBio.setText(!isBlank(p.getAboutMe()) ? p.getAboutMe() : "No professional bio added yet.");
        renderPhotoOnLabel(lblPhotoPreview, p.getProfilePhoto(), getInitials(u.getFullName()), 92);

        ProfileDao.computeCompletion(p);
        progressCompletion.setValue(p.getCompletionPercentage());
        progressCompletion.setString(p.getCompletionPercentage() + "%");
        lblCompletionText.setText("Profile Completion: " + p.getCompletionPercentage() + "%");

        txtFullName.setText(nonNull(u.getFullName()));
        txtUsername.setText(nonNull(u.getUsername()));
        txtEmail.setText(nonNull(u.getEmail()));
        txtPhone.setText(nonNull(p.getPhone()));
        if (txtDesignation != null) txtDesignation.setText(nonNull(p.getDesignation()));
        if (txtDepartment != null) txtDepartment.setText(nonNull(u.getDepartment()));
        txtLocation.setText(nonNull(p.getLocation()));
        txtAboutMe.setText(nonNull(p.getAboutMe()));

        if (u.getRole() == User.Role.STUDENT) {
            txtDob.setText(nonNull(p.getDateOfBirth()));
            cmbGender.setSelectedItem(nonNull(p.getGender()));
            txtAddress.setText(nonNull(p.getAddress()));

            Student st = p.getStudent();
            if (st != null) {
                txtResumeUrl.setText(nonNull(st.getResumeUrl()));
                txtRollNumber.setText(nonNull(st.getRollNumber()));
                txtStudentBranch.setText(nonNull(st.getBranch()));
                txtApprovalStatus.setText(nonNull(st.getApprovalStatus()));
                txtPlacementStatus.setText(nonNull(st.getPlacementStatus()));
                txtSection.setText(nonNull(st.getSection()));
                spnAcademicYear.setValue(Math.max(1, Math.min(4, st.getAcademicYear())));
                spnCurrentSem.setValue(Math.max(1, Math.min(8, st.getCurrentSemester())));
                spnGradYear.setValue(st.getGraduationYear() > 0 ? st.getGraduationYear() : 2026);
                txtCgpa.setText(String.format("%.2f", st.getCgpa()));
                spnBacklogs.setValue(Math.max(0, st.getBacklogs()));
                txtTenthPct.setText(String.format("%.1f", st.getTenthPercentage()));
                txtInterPct.setText(String.format("%.1f", st.getInterPercentage()));

                txtTechSkills.setText(nonNull(st.getTechnicalSkills()));
                txtProgLangs.setText(nonNull(st.getProgrammingLanguages()));
                txtFrameworks.setText(nonNull(st.getFrameworksTechnologies()));
                txtSoftSkills.setText(nonNull(st.getSoftSkills()));
                txtCareerInterests.setText(nonNull(st.getCareerInterests()));
                txtPreferredRoles.setText(nonNull(st.getPreferredJobRoles()));

                verifiedSkillsModel.setRowCount(0);
                if (st.getSkills() != null) {
                    for (StudentSkill sk : st.getSkills()) {
                        verifiedSkillsModel.addRow(new Object[]{
                                sk.getSkillName(),
                                sk.getSkillCategory(),
                                "Level " + sk.getProficiency() + "/5",
                                sk.isVerifiedByFaculty() ? "Faculty Verified" : "Self-Assessed"
                        });
                    }
                }
            }
            refreshProjectsTable();
            refreshInternshipsTable();
            refreshCertificationsTable();
            refreshAchievementsTable();
        } else if (u.getRole() == User.Role.FACULTY) {
            Faculty f = p.getFaculty();
            if (f != null) {
                txtFacultyId.setText(!isBlank(f.getEmployeeId()) ? f.getEmployeeId() : nonNull(f.getFacultyId()));
                txtFacultyDept.setText(nonNull(f.getDepartment()));
                txtFacultyVerifyStatus.setText(f.getStatus() + " (Faculty: " + (f.isFacultyApproved() ? "Yes" : "No") + ", Admin: " + (f.isAdminApproved() ? "Yes" : "No") + ")");
                txtQualifications.setText(nonNull(f.getQualifications()));
                txtExpertise.setText(nonNull(f.getAreasOfExpertise()));
                txtSubjectsHandled.setText(nonNull(f.getSubjectsHandled()));
                txtResearchInterests.setText(nonNull(f.getResearchInterests()));
            }
            refreshPublicationsTable();
            refreshCertificationsTable();
        } else if (u.getRole() == User.Role.COMPANY) {
            Company c = p.getCompany();
            if (c != null) {
                renderPhotoOnLabel(lblCompanyLogoPreview, c.getCompanyLogo(), "LOGO", 84);
                txtCompanyName.setText(nonNull(c.getName()));
                txtCompanyIndustry.setText(nonNull(c.getIndustry()));
                txtRecruiterName.setText(!isBlank(c.getRecruiterName()) ? c.getRecruiterName() : nonNull(c.getHrName()));
                txtRecruiterDesignation.setText(!isBlank(c.getRecruiterDesignation()) ? c.getRecruiterDesignation() : nonNull(p.getDesignation()));
                txtCompanyWebsite.setText(nonNull(c.getWebsite()));
                txtCompanyStatusTier.setText(nonNull(c.getApprovalStatus()) + " · " + nonNull(c.getTier()));
                txtCompanyDescription.setText(nonNull(c.getDescription()));
            }
        }

        txtLinkedin.setText(nonNull(p.getLinkedinUrl()));
        txtGithub.setText(nonNull(p.getGithubUrl()));
        txtPortfolio.setText(nonNull(p.getPortfolioUrl()));
        txtLeetcode.setText(nonNull(p.getLeetcodeUrl()));
        txtHackerrank.setText(nonNull(p.getHackerrankUrl()));
        txtCodechef.setText(nonNull(p.getCodechefUrl()));
        txtGfg.setText(nonNull(p.getGfgUrl()));
        txtKaggle.setText(nonNull(p.getKaggleUrl()));
        txtGoogleDev.setText(nonNull(p.getGoogleDevUrl()));
        refreshCustomLinksTable();
        rebuildClickableSocialButtons();
    }

    private void collectFormIntoWorkingProfile() {
        UserProfileData p = workingProfile;
        p.getUser().setFullName(txtFullName.getText().trim());
        p.getUser().setEmail(txtEmail.getText().trim().toLowerCase());
        p.setPhone(txtPhone.getText().trim());
        if (txtDesignation != null) p.setDesignation(txtDesignation.getText().trim());
        p.setLocation(txtLocation.getText().trim());
        p.setAboutMe(txtAboutMe.getText().trim());

        p.setLinkedinUrl(txtLinkedin.getText().trim());
        p.setGithubUrl(txtGithub.getText().trim());
        p.setPortfolioUrl(txtPortfolio.getText().trim());
        p.setLeetcodeUrl(txtLeetcode.getText().trim());
        p.setHackerrankUrl(txtHackerrank.getText().trim());
        p.setCodechefUrl(txtCodechef.getText().trim());
        p.setGfgUrl(txtGfg.getText().trim());
        p.setKaggleUrl(txtKaggle.getText().trim());
        p.setGoogleDevUrl(txtGoogleDev.getText().trim());

        User.Role role = p.getUser().getRole();
        if (role == User.Role.STUDENT) {
            p.setDateOfBirth(txtDob.getText().trim());
            p.setGender(cmbGender.getSelectedItem() != null ? cmbGender.getSelectedItem().toString() : "");
            p.setAddress(txtAddress.getText().trim());
            if (p.getStudent() != null) {
                Student st = p.getStudent();
                st.setSection(txtSection.getText().trim());
                st.setAcademicYear((Integer) spnAcademicYear.getValue());
                st.setCurrentSemester((Integer) spnCurrentSem.getValue());
                st.setGraduationYear((Integer) spnGradYear.getValue());
                st.setCgpa(parseDoubleOrThrow(txtCgpa.getText(), "CGPA"));
                st.setBacklogs((Integer) spnBacklogs.getValue());
                st.setTenthPercentage(parseDoubleOrThrow(txtTenthPct.getText(), "10th Percentage"));
                st.setInterPercentage(parseDoubleOrThrow(txtInterPct.getText(), "Inter/Diploma Percentage"));
                st.setResumeUrl(txtResumeUrl.getText().trim());
                st.setTechnicalSkills(txtTechSkills.getText().trim());
                st.setProgrammingLanguages(txtProgLangs.getText().trim());
                st.setFrameworksTechnologies(txtFrameworks.getText().trim());
                st.setSoftSkills(txtSoftSkills.getText().trim());
                st.setCareerInterests(txtCareerInterests.getText().trim());
                st.setPreferredJobRoles(txtPreferredRoles.getText().trim());
            }
        } else if (role == User.Role.FACULTY && p.getFaculty() != null) {
            Faculty f = p.getFaculty();
            f.setQualifications(txtQualifications.getText().trim());
            f.setAreasOfExpertise(txtExpertise.getText().trim());
            f.setSubjectsHandled(txtSubjectsHandled.getText().trim());
            f.setResearchInterests(txtResearchInterests.getText().trim());
        } else if (role == User.Role.COMPANY && p.getCompany() != null) {
            Company c = p.getCompany();
            c.setName(txtCompanyName.getText().trim());
            c.setIndustry(txtCompanyIndustry.getText().trim());
            c.setRecruiterName(txtRecruiterName.getText().trim());
            c.setRecruiterDesignation(txtRecruiterDesignation.getText().trim());
            c.setWebsite(txtCompanyWebsite.getText().trim());
            c.setDescription(txtCompanyDescription.getText().trim());
            c.setLocation(p.getLocation());
            c.setLinkedinUrl(p.getLinkedinUrl());
        }
    }

    private void setEditMode(boolean editing) {
        this.isEditing = editing;
        btnEditProfile.setVisible(!editing);
        btnSaveChanges.setVisible(editing);
        btnCancelEdit.setVisible(editing);
        btnUploadPhoto.setVisible(true);
        btnRemovePhoto.setVisible(true);

        if (btnUploadLogo != null) btnUploadLogo.setVisible(editing);
        if (btnRemoveLogo != null) btnRemoveLogo.setVisible(editing);

        for (JButton b : listActionButtons) {
            b.setEnabled(editing);
        }

        setEditable(txtFullName, editing);
        setEditable(txtEmail, editing);
        setEditable(txtPhone, editing);
        setEditable(txtDesignation, editing);
        setEditable(txtLocation, editing);
        setEditable(txtDob, editing);
        if (cmbGender != null) cmbGender.setEnabled(editing);
        setEditable(txtAddress, editing);
        setEditable(txtResumeUrl, editing);
        setEditable(txtAboutMe, editing);

        setEditable(txtSection, editing);
        if (spnAcademicYear != null) spnAcademicYear.setEnabled(editing);
        if (spnCurrentSem != null) spnCurrentSem.setEnabled(editing);
        if (spnGradYear != null) spnGradYear.setEnabled(editing);
        setEditable(txtCgpa, editing);
        if (spnBacklogs != null) spnBacklogs.setEnabled(editing);
        setEditable(txtTenthPct, editing);
        setEditable(txtInterPct, editing);

        setEditable(txtTechSkills, editing);
        setEditable(txtProgLangs, editing);
        setEditable(txtFrameworks, editing);
        setEditable(txtSoftSkills, editing);
        setEditable(txtCareerInterests, editing);
        setEditable(txtPreferredRoles, editing);

        setEditable(txtQualifications, editing);
        setEditable(txtExpertise, editing);
        setEditable(txtSubjectsHandled, editing);
        setEditable(txtResearchInterests, editing);

        setEditable(txtCompanyName, editing);
        setEditable(txtCompanyIndustry, editing);
        setEditable(txtRecruiterName, editing);
        setEditable(txtRecruiterDesignation, editing);
        setEditable(txtCompanyWebsite, editing);
        setEditable(txtCompanyDescription, editing);

        setEditable(txtLinkedin, editing);
        setEditable(txtGithub, editing);
        setEditable(txtPortfolio, editing);
        setEditable(txtLeetcode, editing);
        setEditable(txtHackerrank, editing);
        setEditable(txtCodechef, editing);
        setEditable(txtGfg, editing);
        setEditable(txtKaggle, editing);
        setEditable(txtGoogleDev, editing);
    }

    private void rebuildClickableSocialButtons() {
        if (clickableSocialLinksPanel == null || workingProfile == null) return;
        clickableSocialLinksPanel.removeAll();

        addSocialLinkBtnIfPresent("LinkedIn", workingProfile.getLinkedinUrl());
        addSocialLinkBtnIfPresent("GitHub", workingProfile.getGithubUrl());
        addSocialLinkBtnIfPresent("Portfolio", workingProfile.getPortfolioUrl());
        addSocialLinkBtnIfPresent("LeetCode", workingProfile.getLeetcodeUrl());
        addSocialLinkBtnIfPresent("HackerRank", workingProfile.getHackerrankUrl());
        addSocialLinkBtnIfPresent("CodeChef", workingProfile.getCodechefUrl());
        addSocialLinkBtnIfPresent("GeeksforGeeks", workingProfile.getGfgUrl());
        addSocialLinkBtnIfPresent("Kaggle", workingProfile.getKaggleUrl());
        addSocialLinkBtnIfPresent("Google Dev", workingProfile.getGoogleDevUrl());

        if (workingProfile.getCustomLinks() != null) {
            for (CustomLink cl : workingProfile.getCustomLinks()) {
                if (!isBlank(cl.getTitle()) && !isBlank(cl.getUrl())) {
                    addSocialLinkBtnIfPresent(cl.getTitle(), cl.getUrl());
                }
            }
        }

        if (clickableSocialLinksPanel.getComponentCount() == 0) {
            JLabel empty = new JLabel("No social or professional links added yet.");
            empty.setFont(Theme.FONT_SMALL);
            empty.setForeground(Color.GRAY);
            clickableSocialLinksPanel.add(empty);
        }

        clickableSocialLinksPanel.revalidate();
        clickableSocialLinksPanel.repaint();
    }

    private void addSocialLinkBtnIfPresent(String label, String url) {
        if (isBlank(url)) return;
        JButton btn = Theme.createSecondaryButton("🔗 " + label + " ↗");
        btn.setFont(Theme.FONT_SMALL);
        btn.setToolTipText(url);
        btn.addActionListener(e -> openUrlInBrowser(url));
        clickableSocialLinksPanel.add(btn);
    }

    private void openUrlInBrowser(String url) {
        if (isBlank(url)) {
            JOptionPane.showMessageDialog(this, "No URL is configured for this entry.", "No Link", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            ProfileDao.validateRequiredUrl(url, "Link");
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url.trim()));
            } else {
                JOptionPane.showMessageDialog(this, "Open in browser: " + url, "External Link", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not open URL: " + ex.getMessage(), "Invalid URL", JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================================
    // SUB-DIALOGS FOR LIST ITEMS
    // =========================================================================

    private void openProjectDialog(int index) {
        if (workingProfile == null) return;
        StudentProject existing = index >= 0 ? workingProfile.getProjects().get(index) : new StudentProject();
        JTextField fTitle = new JTextField(nonNull(existing.getTitle()));
        JTextField fTech = new JTextField(nonNull(existing.getTechnologies()));
        JTextField fDur = new JTextField(nonNull(existing.getDuration()));
        JTextField fUrl = new JTextField(nonNull(existing.getProjectUrl()));
        JTextArea fDesc = new JTextArea(nonNull(existing.getDescription()), 4, 28);
        fDesc.setLineWrap(true);

        JPanel form = buildModalGrid(
                new String[]{"Project Title *", "Technologies (comma-separated)", "Duration", "Project URL (https://...)"},
                new JComponent[]{fTitle, fTech, fDur, fUrl}, "Description", fDesc);

        if (JOptionPane.showConfirmDialog(this, form, index >= 0 ? "Edit Project" : "Add Project", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (isBlank(fTitle.getText())) {
                JOptionPane.showMessageDialog(this, "Project title is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                ProfileDao.validateOptionalUrl(fUrl.getText(), "Project URL");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            existing.setId(isBlank(existing.getId()) ? UUID.randomUUID().toString() : existing.getId());
            existing.setTitle(fTitle.getText().trim());
            existing.setTechnologies(fTech.getText().trim());
            existing.setDuration(fDur.getText().trim());
            existing.setProjectUrl(fUrl.getText().trim());
            existing.setDescription(fDesc.getText().trim());
            if (index < 0) workingProfile.getProjects().add(existing);
            refreshProjectsTable();
        }
    }

    private void openInternshipDialog(int index) {
        if (workingProfile == null) return;
        StudentInternship existing = index >= 0 ? workingProfile.getInternships().get(index) : new StudentInternship();
        JTextField fComp = new JTextField(nonNull(existing.getCompanyName()));
        JTextField fRole = new JTextField(nonNull(existing.getRole()));
        JTextField fDur = new JTextField(nonNull(existing.getDuration()));
        JTextField fLoc = new JTextField(nonNull(existing.getLocation()));
        JTextArea fDesc = new JTextArea(nonNull(existing.getDescription()), 4, 28);
        fDesc.setLineWrap(true);

        JPanel form = buildModalGrid(
                new String[]{"Company Name *", "Role / Designation *", "Duration", "Location"},
                new JComponent[]{fComp, fRole, fDur, fLoc}, "Work Description", fDesc);

        if (JOptionPane.showConfirmDialog(this, form, index >= 0 ? "Edit Experience" : "Add Experience", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (isBlank(fComp.getText()) || isBlank(fRole.getText())) {
                JOptionPane.showMessageDialog(this, "Company Name and Role are required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            existing.setId(isBlank(existing.getId()) ? UUID.randomUUID().toString() : existing.getId());
            existing.setCompanyName(fComp.getText().trim());
            existing.setRole(fRole.getText().trim());
            existing.setDuration(fDur.getText().trim());
            existing.setLocation(fLoc.getText().trim());
            existing.setDescription(fDesc.getText().trim());
            if (index < 0) workingProfile.getInternships().add(existing);
            refreshInternshipsTable();
        }
    }

    private void openCertificationDialog(int index) {
        if (workingProfile == null) return;
        UserCertification existing = index >= 0 ? workingProfile.getCertifications().get(index) : new UserCertification();
        JTextField fName = new JTextField(nonNull(existing.getName()));
        JTextField fOrg = new JTextField(nonNull(existing.getIssuingOrg()));
        JTextField fDate = new JTextField(nonNull(existing.getIssueDate()));
        JTextField fCredId = new JTextField(nonNull(existing.getCredentialId()));
        JTextField fUrl = new JTextField(nonNull(existing.getCredentialUrl()));

        JPanel form = buildModalGrid(
                new String[]{"Certificate Name *", "Issuing Organization *", "Issue Date (YYYY-MM-DD)", "Credential ID", "Credential URL (https://...)"},
                new JComponent[]{fName, fOrg, fDate, fCredId, fUrl}, null, null);

        if (JOptionPane.showConfirmDialog(this, form, index >= 0 ? "Edit Certification" : "Add Certification", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (isBlank(fName.getText()) || isBlank(fOrg.getText())) {
                JOptionPane.showMessageDialog(this, "Certificate Name and Issuing Organization are required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                ProfileDao.validateOptionalUrl(fUrl.getText(), "Credential URL");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            existing.setId(isBlank(existing.getId()) ? UUID.randomUUID().toString() : existing.getId());
            existing.setName(fName.getText().trim());
            existing.setIssuingOrg(fOrg.getText().trim());
            existing.setIssueDate(fDate.getText().trim());
            existing.setCredentialId(fCredId.getText().trim());
            existing.setCredentialUrl(fUrl.getText().trim());
            if (index < 0) workingProfile.getCertifications().add(existing);
            refreshCertificationsTable();
        }
    }

    private void openAchievementDialog(int index) {
        if (workingProfile == null) return;
        StudentAchievement existing = index >= 0 ? workingProfile.getAchievements().get(index) : new StudentAchievement();
        JTextField fTitle = new JTextField(nonNull(existing.getTitle()));
        JComboBox<String> fCat = new JComboBox<>(new String[]{"AWARD", "ACHIEVEMENT", "WORKSHOP", "HACKATHON"});
        fCat.setSelectedItem(!isBlank(existing.getCategory()) ? existing.getCategory().toUpperCase() : "AWARD");
        JTextField fOrg = new JTextField(nonNull(existing.getOrganizer()));
        JTextField fDate = new JTextField(nonNull(existing.getEventDate()));
        JTextField fUrl = new JTextField(nonNull(existing.getCredentialUrl()));
        JTextArea fDesc = new JTextArea(nonNull(existing.getDescription()), 3, 28);
        fDesc.setLineWrap(true);

        JPanel form = buildModalGrid(
                new String[]{"Title *", "Category", "Organizer / Event", "Date (YYYY-MM-DD)", "Certificate / Proof URL"},
                new JComponent[]{fTitle, fCat, fOrg, fDate, fUrl}, "Description", fDesc);

        if (JOptionPane.showConfirmDialog(this, form, index >= 0 ? "Edit Entry" : "Add Achievement / Workshop / Hackathon", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (isBlank(fTitle.getText())) {
                JOptionPane.showMessageDialog(this, "Title is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                ProfileDao.validateOptionalUrl(fUrl.getText(), "Credential URL");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            existing.setId(isBlank(existing.getId()) ? UUID.randomUUID().toString() : existing.getId());
            existing.setTitle(fTitle.getText().trim());
            existing.setCategory(String.valueOf(fCat.getSelectedItem()));
            existing.setOrganizer(fOrg.getText().trim());
            existing.setEventDate(fDate.getText().trim());
            existing.setCredentialUrl(fUrl.getText().trim());
            existing.setDescription(fDesc.getText().trim());
            if (index < 0) workingProfile.getAchievements().add(existing);
            refreshAchievementsTable();
        }
    }

    private void openPublicationDialog(int index) {
        if (workingProfile == null) return;
        FacultyPublication existing = index >= 0 ? workingProfile.getPublications().get(index) : new FacultyPublication();
        JTextField fTitle = new JTextField(nonNull(existing.getTitle()));
        JTextField fJournal = new JTextField(nonNull(existing.getJournalOrConference()));
        JTextField fYear = new JTextField(nonNull(existing.getPublicationYear()));
        JTextField fAuthors = new JTextField(nonNull(existing.getAuthors()));
        JTextField fUrl = new JTextField(nonNull(existing.getDoiOrUrl()));

        JPanel form = buildModalGrid(
                new String[]{"Publication Title *", "Journal / Conference", "Year", "Authors", "DOI / Publication URL"},
                new JComponent[]{fTitle, fJournal, fYear, fAuthors, fUrl}, null, null);

        if (JOptionPane.showConfirmDialog(this, form, index >= 0 ? "Edit Publication" : "Add Publication", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (isBlank(fTitle.getText())) {
                JOptionPane.showMessageDialog(this, "Publication title is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                ProfileDao.validateOptionalUrl(fUrl.getText(), "DOI / URL");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            existing.setId(isBlank(existing.getId()) ? UUID.randomUUID().toString() : existing.getId());
            existing.setTitle(fTitle.getText().trim());
            existing.setJournalOrConference(fJournal.getText().trim());
            existing.setPublicationYear(fYear.getText().trim());
            existing.setAuthors(fAuthors.getText().trim());
            existing.setDoiOrUrl(fUrl.getText().trim());
            if (index < 0) workingProfile.getPublications().add(existing);
            refreshPublicationsTable();
        }
    }

    private void openCustomLinkDialog(int index) {
        if (workingProfile == null) return;
        CustomLink existing = index >= 0 ? workingProfile.getCustomLinks().get(index) : new CustomLink();
        JTextField fTitle = new JTextField(nonNull(existing.getTitle()));
        JTextField fUrl = new JTextField(nonNull(existing.getUrl()));

        JPanel form = buildModalGrid(
                new String[]{"Link Title (e.g. Medium Blog, ResearchGate) *", "Full URL (https://...) *"},
                new JComponent[]{fTitle, fUrl}, null, null);

        if (JOptionPane.showConfirmDialog(this, form, index >= 0 ? "Edit Custom Link" : "Add Custom Link", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (isBlank(fTitle.getText())) {
                JOptionPane.showMessageDialog(this, "Link title is required.", "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                ProfileDao.validateRequiredUrl(fUrl.getText(), "URL");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            existing.setId(isBlank(existing.getId()) ? UUID.randomUUID().toString() : existing.getId());
            existing.setTitle(fTitle.getText().trim());
            existing.setUrl(fUrl.getText().trim());
            if (index < 0) workingProfile.getCustomLinks().add(existing);
            refreshCustomLinksTable();
            rebuildClickableSocialButtons();
        }
    }

    // =========================================================================
    // TABLE REFRESHERS
    // =========================================================================

    private void refreshProjectsTable() {
        if (projectsModel == null || workingProfile == null) return;
        projectsModel.setRowCount(0);
        for (StudentProject p : workingProfile.getProjects()) {
            projectsModel.addRow(new Object[]{p.getTitle(), p.getTechnologies(), p.getDuration(), p.getProjectUrl(), p.getDescription()});
        }
    }

    private void refreshInternshipsTable() {
        if (internshipsModel == null || workingProfile == null) return;
        internshipsModel.setRowCount(0);
        for (StudentInternship i : workingProfile.getInternships()) {
            internshipsModel.addRow(new Object[]{i.getCompanyName(), i.getRole(), i.getDuration(), i.getLocation(), i.getDescription()});
        }
    }

    private void refreshCertificationsTable() {
        if (certificationsModel == null || workingProfile == null) return;
        certificationsModel.setRowCount(0);
        for (UserCertification c : workingProfile.getCertifications()) {
            certificationsModel.addRow(new Object[]{c.getName(), c.getIssuingOrg(), c.getIssueDate(), c.getCredentialId(), c.getCredentialUrl()});
        }
    }

    private void refreshAchievementsTable() {
        if (achievementsModel == null || workingProfile == null) return;
        achievementsModel.setRowCount(0);
        for (StudentAchievement a : workingProfile.getAchievements()) {
            achievementsModel.addRow(new Object[]{a.getTitle(), a.getCategory(), a.getOrganizer(), a.getEventDate(), a.getDescription(), a.getCredentialUrl()});
        }
    }

    private void refreshPublicationsTable() {
        if (publicationsModel == null || workingProfile == null) return;
        publicationsModel.setRowCount(0);
        for (FacultyPublication p : workingProfile.getPublications()) {
            publicationsModel.addRow(new Object[]{p.getTitle(), p.getJournalOrConference(), p.getPublicationYear(), p.getAuthors(), p.getDoiOrUrl()});
        }
    }

    private void refreshCustomLinksTable() {
        if (customLinksModel == null || workingProfile == null) return;
        customLinksModel.setRowCount(0);
        for (CustomLink l : workingProfile.getCustomLinks()) {
            customLinksModel.addRow(new Object[]{l.getTitle(), l.getUrl()});
        }
    }

    // =========================================================================
    // UI & CLONE HELPERS
    // =========================================================================

    private void setBanner(String msg, boolean isError) {
        if (isBlank(msg)) {
            lblStatusBanner.setVisible(false);
            return;
        }
        lblStatusBanner.setText(msg);
        lblStatusBanner.setVisible(true);
        if (isError) {
            lblStatusBanner.setBackground(new Color(0xFE, 0xE2, 0xE2));
            lblStatusBanner.setForeground(new Color(0x99, 0x1B, 0x1B));
        } else {
            lblStatusBanner.setBackground(new Color(0xD1, 0xFA, 0xE5));
            lblStatusBanner.setForeground(new Color(0x06, 0x5F, 0x46));
        }
    }

    private JPanel createFormContainer() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(new EmptyBorder(16, 20, 16, 20));
        return p;
    }

    private GridBagConstraints createGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        return gbc;
    }

    private JScrollPane wrapScroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private JTextField createReadOnlyField() {
        JTextField tf = new JTextField();
        tf.setEditable(false);
        tf.setBackground(new Color(0xF1, 0xF5, 0xF9));
        tf.setForeground(new Color(0x47, 0x55, 0x69));
        return tf;
    }

    private void setEditable(JComponent c, boolean editable) {
        if (c == null) return;
        if (c instanceof JTextField) ((JTextField) c).setEditable(editable);
        if (c instanceof JTextArea) ((JTextArea) c).setEditable(editable);
        c.setBackground(editable ? Color.WHITE : new Color(0xF8, 0xFA, 0xFC));
    }

    private void addFieldRow(JPanel p, GridBagConstraints gbc, int row, String l1, JComponent c1, String l2, JComponent c2) {
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.gridx = 0; p.add(boldLabel(l1), gbc);
        gbc.weightx = 0.5;
        gbc.gridx = 1; p.add(c1, gbc);
        gbc.weightx = 0.0;
        gbc.gridx = 2; p.add(boldLabel(l2), gbc);
        gbc.weightx = 0.5;
        gbc.gridx = 3; p.add(c2, gbc);
    }

    private void addSingleFieldRow(JPanel p, GridBagConstraints gbc, int row, String label, JComponent comp) {
        gbc.gridy = row;
        gbc.weightx = 0.0; gbc.gridwidth = 1; gbc.gridx = 0;
        p.add(boldLabel(label), gbc);
        gbc.weightx = 1.0; gbc.gridwidth = 3; gbc.gridx = 1;
        p.add(comp, gbc);
        gbc.gridwidth = 1;
    }

    private void addTextAreaRow(JPanel p, GridBagConstraints gbc, int row, String label, JTextArea area) {
        gbc.gridy = row;
        gbc.weightx = 0.0; gbc.gridwidth = 1; gbc.gridx = 0;
        p.add(boldLabel(label), gbc);
        gbc.weightx = 1.0; gbc.gridwidth = 3; gbc.gridx = 1;
        p.add(new JScrollPane(area), gbc);
        gbc.gridwidth = 1;
    }

    private void addFiller(JPanel p, GridBagConstraints gbc, int row) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.weighty = 1.0;
        p.add(Box.createVerticalGlue(), gbc);
    }

    private JLabel boldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_BOLD);
        l.setForeground(Theme.DARK_TEXT);
        return l;
    }

    private JPanel buildModalGrid(String[] labels, JComponent[] fields, String areaLabel, JTextArea area) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < labels.length; i++) {
            g.gridy = i;
            g.gridx = 0; g.weightx = 0;
            p.add(boldLabel(labels[i]), g);
            g.gridx = 1; g.weightx = 1;
            fields[i].setPreferredSize(new Dimension(280, 28));
            p.add(fields[i], g);
        }
        if (areaLabel != null && area != null) {
            g.gridy = labels.length;
            g.gridx = 0; g.weightx = 0;
            p.add(boldLabel(areaLabel), g);
            g.gridx = 1; g.weightx = 1;
            p.add(new JScrollPane(area), g);
        }
        return p;
    }

    private UserProfileData cloneProfileData(UserProfileData src) {
        UserProfileData copy = new UserProfileData();
        if (src.getUser() != null) {
            User u = new User();
            u.setId(src.getUser().getId());
            u.setUsername(src.getUser().getUsername());
            u.setEmail(src.getUser().getEmail());
            u.setFullName(src.getUser().getFullName());
            u.setRole(src.getUser().getRole());
            u.setDepartment(src.getUser().getDepartment());
            u.setStudentRollNumber(src.getUser().getStudentRollNumber());
            u.setCompanyId(src.getUser().getCompanyId());
            u.setStatus(src.getUser().getStatus());
            copy.setUser(u);
        }
        if (src.getStudent() != null) {
            Student s = src.getStudent();
            Student sc = new Student();
            sc.setId(s.getId());
            sc.setRollNumber(s.getRollNumber());
            sc.setFullName(s.getFullName());
            sc.setEmail(s.getEmail());
            sc.setPhone(s.getPhone());
            sc.setBranch(s.getBranch());
            sc.setSection(s.getSection());
            sc.setAcademicYear(s.getAcademicYear());
            sc.setCurrentSemester(s.getCurrentSemester());
            sc.setGraduationYear(s.getGraduationYear());
            sc.setCgpa(s.getCgpa());
            sc.setBacklogs(s.getBacklogs());
            sc.setTenthPercentage(s.getTenthPercentage());
            sc.setInterPercentage(s.getInterPercentage());
            sc.setPlacementStatus(s.getPlacementStatus());
            sc.setApprovalStatus(s.getApprovalStatus());
            sc.setResumeUrl(s.getResumeUrl());
            sc.setTechnicalSkills(s.getTechnicalSkills());
            sc.setProgrammingLanguages(s.getProgrammingLanguages());
            sc.setFrameworksTechnologies(s.getFrameworksTechnologies());
            sc.setSoftSkills(s.getSoftSkills());
            sc.setCareerInterests(s.getCareerInterests());
            sc.setPreferredJobRoles(s.getPreferredJobRoles());
            sc.setSkills(new ArrayList<>(s.getSkills()));
            copy.setStudent(sc);
        }
        if (src.getFaculty() != null) {
            Faculty f = src.getFaculty();
            Faculty fc = new Faculty();
            fc.setId(f.getId());
            fc.setFacultyId(f.getFacultyId());
            fc.setEmployeeId(f.getEmployeeId());
            fc.setFullName(f.getFullName());
            fc.setDepartment(f.getDepartment());
            fc.setDesignation(f.getDesignation());
            fc.setEmail(f.getEmail());
            fc.setPhone(f.getPhone());
            fc.setStatus(f.getStatus());
            fc.setFacultyApproved(f.isFacultyApproved());
            fc.setAdminApproved(f.isAdminApproved());
            fc.setQualifications(f.getQualifications());
            fc.setAreasOfExpertise(f.getAreasOfExpertise());
            fc.setSubjectsHandled(f.getSubjectsHandled());
            fc.setResearchInterests(f.getResearchInterests());
            copy.setFaculty(fc);
        }
        if (src.getCompany() != null) {
            Company c = src.getCompany();
            Company cc = new Company();
            cc.setId(c.getId());
            cc.setName(c.getName());
            cc.setIndustry(c.getIndustry());
            cc.setWebsite(c.getWebsite());
            cc.setLocation(c.getLocation());
            cc.setDescription(c.getDescription());
            cc.setHrName(c.getHrName());
            cc.setHrEmail(c.getHrEmail());
            cc.setHrPhone(c.getHrPhone());
            cc.setTier(c.getTier());
            cc.setApprovalStatus(c.getApprovalStatus());
            cc.setRecruiterName(c.getRecruiterName());
            cc.setRecruiterDesignation(c.getRecruiterDesignation());
            cc.setCompanyLogo(c.getCompanyLogo());
            cc.setLinkedinUrl(c.getLinkedinUrl());
            copy.setCompany(cc);
        }

        copy.setProfilePhoto(src.getProfilePhoto());
        copy.setPhone(src.getPhone());
        copy.setDesignation(src.getDesignation());
        copy.setLocation(src.getLocation());
        copy.setAddress(src.getAddress());
        copy.setAboutMe(src.getAboutMe());
        copy.setDateOfBirth(src.getDateOfBirth());
        copy.setGender(src.getGender());
        copy.setLinkedinUrl(src.getLinkedinUrl());
        copy.setGithubUrl(src.getGithubUrl());
        copy.setPortfolioUrl(src.getPortfolioUrl());
        copy.setLeetcodeUrl(src.getLeetcodeUrl());
        copy.setHackerrankUrl(src.getHackerrankUrl());
        copy.setCodechefUrl(src.getCodechefUrl());
        copy.setGfgUrl(src.getGfgUrl());
        copy.setKaggleUrl(src.getKaggleUrl());
        copy.setGoogleDevUrl(src.getGoogleDevUrl());

        for (CustomLink l : src.getCustomLinks()) {
            copy.getCustomLinks().add(new CustomLink(l.getId(), l.getTitle(), l.getUrl()));
        }
        for (StudentProject p : src.getProjects()) {
            copy.getProjects().add(new StudentProject(p.getId(), p.getTitle(), p.getDescription(), p.getTechnologies(), p.getProjectUrl(), p.getDuration()));
        }
        for (StudentInternship i : src.getInternships()) {
            copy.getInternships().add(new StudentInternship(i.getId(), i.getCompanyName(), i.getRole(), i.getDuration(), i.getLocation(), i.getDescription()));
        }
        for (UserCertification c : src.getCertifications()) {
            copy.getCertifications().add(new UserCertification(c.getId(), c.getName(), c.getIssuingOrg(), c.getIssueDate(), c.getCredentialId(), c.getCredentialUrl()));
        }
        for (StudentAchievement a : src.getAchievements()) {
            copy.getAchievements().add(new StudentAchievement(a.getId(), a.getTitle(), a.getCategory(), a.getOrganizer(), a.getEventDate(), a.getDescription(), a.getCredentialUrl()));
        }
        for (FacultyPublication p : src.getPublications()) {
            copy.getPublications().add(new FacultyPublication(p.getId(), p.getTitle(), p.getJournalOrConference(), p.getPublicationYear(), p.getAuthors(), p.getDoiOrUrl()));
        }
        return copy;
    }

    private double parseDoubleOrThrow(String raw, String field) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a valid decimal number.");
        }
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String nonNull(String s) { return s == null ? "" : s; }
    private static String getInitials(String name) {
        if (isBlank(name)) return "U";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
    }
}
