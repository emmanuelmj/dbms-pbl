package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.dao.ProfileDao;
import edu.gcet.placementhub.model.*;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Base64;
import java.util.List;

/**
 * GCET PLACEMENT HUB - Student Profile Details Dialog
 */
public class StudentProfileDialog extends JDialog {

    public StudentProfileDialog(Window owner, Student student) {
        super(owner, "Student Placement Profile - " + student.getRollNumber(), ModalityType.APPLICATION_MODAL);
        UserProfileData extended = null;
        try {
            extended = new ProfileDao().loadStudentProfileByStudentId(student.getId());
        } catch (Exception ignored) {}
        initUI(student, extended);
    }

    private void initUI(Student s, UserProfileData ext) {
        setSize(720, 720);
        setLocationRelativeTo(getOwner());
        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.LIGHT_BG);

        // Header
        JPanel header = new JPanel(new BorderLayout(15, 5));
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel leftHeader = new JPanel(new BorderLayout(12, 0));
        leftHeader.setOpaque(false);

        JLabel lblAvatar = new JLabel(s.getFullName() != null && !s.getFullName().isEmpty() ? s.getFullName().substring(0, 1).toUpperCase() : "S", JLabel.CENTER);
        lblAvatar.setPreferredSize(new Dimension(56, 56));
        lblAvatar.setOpaque(true);
        lblAvatar.setBackground(Theme.DEEP_BLUE);
        lblAvatar.setForeground(Theme.CYAN_ACCENT);
        lblAvatar.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblAvatar.setBorder(BorderFactory.createLineBorder(Theme.CYAN_ACCENT, 1));
        if (ext != null && ext.getProfilePhoto() != null && ext.getProfilePhoto().contains("base64,")) {
            try {
                String b64 = ext.getProfilePhoto().substring(ext.getProfilePhoto().indexOf("base64,") + 7);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(b64)));
                if (img != null) {
                    lblAvatar.setIcon(new ImageIcon(img.getScaledInstance(52, 52, Image.SCALE_SMOOTH)));
                    lblAvatar.setText("");
                }
            } catch (Exception ignored) {}
        }
        leftHeader.add(lblAvatar, BorderLayout.WEST);

        JPanel namePanel = new JPanel(new GridLayout(3, 1, 2, 2));
        namePanel.setOpaque(false);

        JLabel lblName = new JLabel(s.getFullName());
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblName.setForeground(Color.WHITE);

        String secSem = (s.getSection() != null ? "Sec " + s.getSection() + " · " : "") + "Sem " + s.getCurrentSemester();
        JLabel lblRoll = new JLabel(s.getRollNumber() + " · " + s.getBranch() + " · " + secSem + " · Class of " + s.getGraduationYear());
        lblRoll.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblRoll.setForeground(Theme.CYAN_ACCENT);

        String loc = ext != null && ext.getLocation() != null && !ext.getLocation().isEmpty() ? ext.getLocation() : "GCET Cheeryal Campus";
        JLabel lblMeta = new JLabel("📍 " + loc + (ext != null ? "  ·  Profile Readiness: " + ext.getCompletionPercentage() + "%" : ""));
        lblMeta.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblMeta.setForeground(Theme.SOFT_WHITE);

        namePanel.add(lblName);
        namePanel.add(lblRoll);
        namePanel.add(lblMeta);
        leftHeader.add(namePanel, BorderLayout.CENTER);

        JLabel lblStatus = new JLabel(" " + s.getPlacementStatus() + " ");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatus.setOpaque(true);
        if ("PLACED".equalsIgnoreCase(s.getPlacementStatus())) {
            lblStatus.setBackground(new Color(0x10, 0xB9, 0x81, 0x30));
            lblStatus.setForeground(Theme.SUCCESS_GREEN);
            lblStatus.setBorder(BorderFactory.createLineBorder(Theme.SUCCESS_GREEN, 1));
        } else {
            lblStatus.setBackground(new Color(0x26, 0x84, 0xFF, 0x30));
            lblStatus.setForeground(Theme.ELECTRIC_BLUE);
            lblStatus.setBorder(BorderFactory.createLineBorder(Theme.ELECTRIC_BLUE, 1));
        }

        header.add(leftHeader, BorderLayout.CENTER);
        header.add(lblStatus, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Main content
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(16, 20, 16, 20));
        content.setBackground(Theme.LIGHT_BG);

        // Bio & Social Links (if present)
        String about = ext != null && ext.getAboutMe() != null ? ext.getAboutMe() : s.getAboutMe();
        if ((about != null && !about.trim().isEmpty()) || hasAnyLinks(ext)) {
            JPanel bioCard = createCard("Professional Summary & Links");
            bioCard.setLayout(new BorderLayout(8, 8));
            if (about != null && !about.trim().isEmpty()) {
                JTextArea txtBio = new JTextArea(about);
                txtBio.setEditable(false);
                txtBio.setLineWrap(true);
                txtBio.setWrapStyleWord(true);
                txtBio.setOpaque(false);
                txtBio.setFont(Theme.FONT_BODY);
                bioCard.add(txtBio, BorderLayout.NORTH);
            }
            if (ext != null && hasAnyLinks(ext)) {
                JPanel linksRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
                linksRow.setOpaque(false);
                addLinkBtn(linksRow, "LinkedIn", ext.getLinkedinUrl());
                addLinkBtn(linksRow, "GitHub", ext.getGithubUrl());
                addLinkBtn(linksRow, "Portfolio", ext.getPortfolioUrl());
                addLinkBtn(linksRow, "LeetCode", ext.getLeetcodeUrl());
                addLinkBtn(linksRow, "HackerRank", ext.getHackerrankUrl());
                addLinkBtn(linksRow, "CodeChef", ext.getCodechefUrl());
                addLinkBtn(linksRow, "GFG", ext.getGfgUrl());
                addLinkBtn(linksRow, "Kaggle", ext.getKaggleUrl());
                addLinkBtn(linksRow, "Google Dev", ext.getGoogleDevUrl());
                for (CustomLink cl : ext.getCustomLinks()) {
                    addLinkBtn(linksRow, cl.getTitle(), cl.getUrl());
                }
                bioCard.add(linksRow, BorderLayout.CENTER);
            }
            content.add(bioCard);
            content.add(Box.createVerticalStrut(12));
        }

        // 1. Academic Performance Card
        JPanel academicCard = createCard("Academic Performance & Eligibility");
        academicCard.setLayout(new GridLayout(2, 4, 10, 10));

        academicCard.add(createMetric("CGPA", String.format("%.2f / 10.0", s.getCgpa()), Theme.ELECTRIC_BLUE));
        academicCard.add(createMetric("10th SSC", String.format("%.1f %%", s.getTenthPercentage()), Theme.DARK_TEXT));
        academicCard.add(createMetric("Inter / Dip", String.format("%.1f %%", s.getInterPercentage()), Theme.DARK_TEXT));
        academicCard.add(createMetric("Backlogs", String.valueOf(s.getBacklogs()), s.getBacklogs() > 0 ? Theme.DANGER_ROSE : Theme.SUCCESS_GREEN));

        academicCard.add(createMetric("Email", s.getEmail(), Theme.DARK_TEXT));
        academicCard.add(createMetric("Phone", s.getPhone() != null ? s.getPhone() : "N/A", Theme.DARK_TEXT));
        academicCard.add(createMetric("Package", s.getPlacedPackageLpa() != null ? s.getPlacedPackageLpa() + " LPA" : "N/A", Theme.SUCCESS_GREEN));
        academicCard.add(createMetric("Campus", "GCET Cheeryal", Theme.DARK_TEXT));

        content.add(academicCard);
        content.add(Box.createVerticalStrut(12));

        // 2. Technical Skills Matrix Card
        JPanel skillsCard = createCard("Technical Skills & Faculty Verification (Levels 1-5)");
        skillsCard.setLayout(new BorderLayout(5, 5));

        List<StudentSkill> skills = (ext != null && ext.getStudent() != null && !ext.getStudent().getSkills().isEmpty())
                ? ext.getStudent().getSkills()
                : s.getSkills();
        if (skills == null || skills.isEmpty()) {
            JLabel lblEmpty = new JLabel("No skills currently recorded in matrix for this student.");
            lblEmpty.setFont(Theme.FONT_SMALL);
            lblEmpty.setForeground(Color.GRAY);
            lblEmpty.setBorder(new EmptyBorder(10, 10, 10, 10));
            skillsCard.add(lblEmpty, BorderLayout.CENTER);
        } else {
            JPanel skillsList = new JPanel(new GridLayout(Math.max(1, skills.size()), 1, 6, 6));
            skillsList.setOpaque(false);

            for (StudentSkill sk : skills) {
                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setBackground(Color.WHITE);
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1),
                        new EmptyBorder(6, 10, 6, 10)
                ));

                JLabel lblSkill = new JLabel(sk.getSkillName() + " (" + sk.getSkillCategory() + ")");
                lblSkill.setFont(Theme.FONT_BOLD);

                StringBuilder stars = new StringBuilder();
                for (int i = 1; i <= 5; i++) {
                    stars.append(i <= sk.getProficiency() ? "★ " : "☆ ");
                }
                JLabel lblRating = new JLabel(stars.toString() + "Level " + sk.getProficiency());
                lblRating.setFont(new Font("Segoe UI", Font.BOLD, 12));
                lblRating.setForeground(new Color(0xF5, 0x9E, 0x0B));

                JLabel lblVerify = new JLabel(sk.isVerifiedByFaculty() ? " [Faculty Verified] " : " [Self-Rating] ");
                lblVerify.setFont(new Font("Segoe UI", Font.BOLD, 10));
                lblVerify.setForeground(sk.isVerifiedByFaculty() ? Theme.SUCCESS_GREEN : Color.GRAY);

                JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
                right.setOpaque(false);
                right.add(lblRating);
                right.add(lblVerify);

                row.add(lblSkill, BorderLayout.WEST);
                row.add(right, BorderLayout.EAST);
                skillsList.add(row);
            }
            skillsCard.add(skillsList, BorderLayout.CENTER);
        }
        content.add(skillsCard);

        // 3. Projects, Internships, Certifications & Achievements summary
        if (ext != null) {
            if (!ext.getProjects().isEmpty()) {
                content.add(Box.createVerticalStrut(12));
                JPanel projCard = createCard("Technical Projects (" + ext.getProjects().size() + ")");
                projCard.setLayout(new GridLayout(ext.getProjects().size(), 1, 6, 6));
                for (StudentProject p : ext.getProjects()) {
                    JLabel l = new JLabel("<html><b>" + esc(p.getTitle()) + "</b> (" + esc(p.getTechnologies()) + ")<br><span style='color:gray'>" + esc(p.getDescription()) + "</span></html>");
                    l.setFont(Theme.FONT_SMALL);
                    projCard.add(l);
                }
                content.add(projCard);
            }
            if (!ext.getInternships().isEmpty()) {
                content.add(Box.createVerticalStrut(12));
                JPanel intCard = createCard("Internships & Experience (" + ext.getInternships().size() + ")");
                intCard.setLayout(new GridLayout(ext.getInternships().size(), 1, 6, 6));
                for (StudentInternship i : ext.getInternships()) {
                    JLabel l = new JLabel("<html><b>" + esc(i.getCompanyName()) + "</b> — " + esc(i.getRole()) + " (" + esc(i.getDuration()) + ")</html>");
                    l.setFont(Theme.FONT_SMALL);
                    intCard.add(l);
                }
                content.add(intCard);
            }
            if (!ext.getCertifications().isEmpty() || !ext.getAchievements().isEmpty()) {
                content.add(Box.createVerticalStrut(12));
                int rows = ext.getCertifications().size() + ext.getAchievements().size();
                JPanel certCard = createCard("Certifications & Achievements (" + rows + ")");
                certCard.setLayout(new GridLayout(Math.max(1, rows), 1, 6, 6));
                for (UserCertification c : ext.getCertifications()) {
                    JLabel l = new JLabel("• [Cert] " + c.getName() + " — " + c.getIssuingOrg());
                    l.setFont(Theme.FONT_SMALL);
                    certCard.add(l);
                }
                for (StudentAchievement a : ext.getAchievements()) {
                    JLabel l = new JLabel("• [" + a.getCategory() + "] " + a.getTitle() + (a.getOrganizer() != null ? " — " + a.getOrganizer() : ""));
                    l.setFont(Theme.FONT_SMALL);
                    certCard.add(l);
                }
                content.add(certCard);
            }
        }

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        // Footer with Close and Skill Gap Buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        JButton btnSkillGap = Theme.createSecondaryButton("🎯 Analyze Skill Gap");
        btnSkillGap.addActionListener(e -> {
            SkillGapDialog dlg = new SkillGapDialog(this, null, s.getId());
            dlg.setVisible(true);
        });
        footer.add(btnSkillGap);

        JButton btnClose = Theme.createPrimaryButton("Close Profile");
        btnClose.addActionListener(e -> dispose());
        footer.add(btnClose);

        add(footer, BorderLayout.SOUTH);
    }

    private boolean hasAnyLinks(UserProfileData ext) {
        if (ext == null) return false;
        return notEmpty(ext.getLinkedinUrl()) || notEmpty(ext.getGithubUrl()) || notEmpty(ext.getPortfolioUrl())
                || notEmpty(ext.getLeetcodeUrl()) || notEmpty(ext.getHackerrankUrl()) || notEmpty(ext.getCodechefUrl())
                || notEmpty(ext.getGfgUrl()) || notEmpty(ext.getKaggleUrl()) || notEmpty(ext.getGoogleDevUrl())
                || !ext.getCustomLinks().isEmpty();
    }

    private void addLinkBtn(JPanel panel, String label, String url) {
        if (!notEmpty(url)) return;
        JButton btn = Theme.createSecondaryButton(label + " ↗");
        btn.setFont(Theme.FONT_SMALL);
        btn.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI(url.trim()));
                }
            } catch (Exception ignored) {}
        });
        panel.add(btn);
    }

    private boolean notEmpty(String s) { return s != null && !s.trim().isEmpty(); }
    private String esc(String s) { return s == null ? "" : s.replace("<", "&lt;").replace(">", "&gt;"); }

    private JPanel createCard(String title) {
        JPanel p = new JPanel();
        p.setBackground(Color.WHITE);
        TitledBorder tb = new TitledBorder(BorderFactory.createEmptyBorder(), title);
        tb.setTitleFont(new Font("Segoe UI", Font.BOLD, 13));
        tb.setTitleColor(Theme.MIDNIGHT_NAVY);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1),
                BorderFactory.createCompoundBorder(tb, new EmptyBorder(8, 8, 8, 8))
        ));
        return p;
    }

    private JPanel createMetric(String label, String value, Color valColor) {
        JPanel p = new JPanel(new GridLayout(2, 1, 2, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(label.toUpperCase());
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(Color.GRAY);

        JLabel v = new JLabel(value);
        v.setFont(new Font("Segoe UI", Font.BOLD, 12));
        v.setForeground(valColor);

        p.add(l);
        p.add(v);
        return p;
    }
}
