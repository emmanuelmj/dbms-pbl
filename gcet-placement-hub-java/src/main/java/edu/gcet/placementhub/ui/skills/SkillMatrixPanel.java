package edu.gcet.placementhub.ui.skills;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.StudentController;
import edu.gcet.placementhub.models.Skill;
import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.models.StudentSkill;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.AlertBanner;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Exact Java Swing recreation of SkillMatrixPage.tsx:
 * 10-category technical skill catalog backed by MySQL `skills` & `student_skills`,
 * with Add/Edit/Delete skill catalog management and Talent Distribution student inspector.
 */
public class SkillMatrixPanel extends JPanel {

    private static final List<String> CATEGORIES = List.of(
            "Programming Languages",
            "Data Structures & Algorithms",
            "Database Management",
            "Web Development",
            "AI & Machine Learning",
            "Cloud Computing",
            "Operating Systems & Networks",
            "Communication",
            "Aptitude",
            "Software Tools"
    );

    private static final String[] PROFICIENCY_LABELS = {
            "", "Beginner", "Foundational", "Intermediate", "Advanced", "Expert"
    };

    private final StudentController controller = new StudentController();
    private final Consumer<String> onSelectStudentProfile;

    private List<Skill> skills = List.of();
    private List<Student> students = List.of();
    private String selectedCategory = "ALL";
    private String searchQuery = "";
    private Skill inspectingSkill;
    private boolean suppressEvents = false;

    private AlertBanner statusBanner;
    private JTextField searchField;
    private JComboBox<String> categoryCombo;
    private JPanel categoryChipsRow;
    private JPanel skillsGrid;
    private JPanel inspectorBody;
    private JLabel inspectorSubtitle;
    private ActionButton addSkillBtn;

    public SkillMatrixPanel(Consumer<String> onSelectStudentProfile) {
        this.onSelectStudentProfile = onSelectStudentProfile;
        setLayout(new BorderLayout(0, 14));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(20, 22, 22, 22));
        buildUi();
    }

    public void loadData() {
        User u = AppSession.getInstance().getCurrentUser();
        if (addSkillBtn != null) {
            addSkillBtn.setVisible(canManageSkills(u));
        }
        controller.loadDirectoryBundleAsync(u, bundle -> {
            if (bundle.error != null) {
                statusBanner.showError(bundle.error);
            } else {
                statusBanner.clear();
            }
            this.skills = bundle.skills != null ? bundle.skills : List.of();
            this.students = bundle.students != null ? bundle.students : List.of();
            if (inspectingSkill != null) {
                inspectingSkill = skills.stream()
                        .filter(s -> s.getId().equals(inspectingSkill.getId()))
                        .findFirst()
                        .orElse(null);
            }
            rebuildCategoryChips();
            renderSkillCards();
            renderInspector();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        removeAll();
        buildUi();
        rebuildCategoryChips();
        renderSkillCards();
        renderInspector();
        revalidate();
        repaint();
    }

    private boolean canManageSkills(User u) {
        return u != null && (u.getRole().isPrivilegedRole() || u.getRole() == UserRole.FACULTY);
    }

    private void buildUi() {
        // Top Header
        JPanel header = new JPanel(new BorderLayout(12, 6));
        header.setOpaque(false);

        JPanel tCol = new JPanel();
        tCol.setOpaque(false);
        tCol.setLayout(new BoxLayout(tCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("GCET Technical Skill Matrix");
        title.setFont(ThemeManager.font(Font.BOLD, 20));
        title.setForeground(ThemeManager.primaryText());
        JLabel sub = new JLabel("Institutional proficiency repository mapping curriculum domains to industry hiring benchmarks.");
        sub.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        sub.setForeground(ThemeManager.mutedText());
        tCol.add(title);
        tCol.add(Box.createVerticalStrut(3));
        tCol.add(sub);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.setOpaque(false);
        ActionButton refreshBtn = new ActionButton("↻  Refresh", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        refreshBtn.addActionListener(e -> loadData());
        addSkillBtn = new ActionButton("+  Add Skill to Catalog", ThemeManager.CYAN_DARK, ThemeManager.CYAN_DARK.darker(), Color.WHITE, null, 10);
        addSkillBtn.addActionListener(e -> openSkillForm(null));
        addSkillBtn.setVisible(canManageSkills(AppSession.getInstance().getCurrentUser()));
        rightBtns.add(refreshBtn);
        rightBtns.add(addSkillBtn);

        header.add(tCol, BorderLayout.WEST);
        header.add(rightBtns, BorderLayout.EAST);

        // Filter Bar Card
        RoundedCard filterCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        filterCard.setLayout(new BoxLayout(filterCard, BoxLayout.Y_AXIS));
        filterCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        statusBanner = new AlertBanner();
        statusBanner.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel fTop = new JPanel(new BorderLayout(12, 0));
        fTop.setOpaque(false);
        fTop.setAlignmentX(Component.LEFT_ALIGNMENT);
        fTop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search skill (e.g. Java, MySQL, React, Docker)...");
        searchField.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onSearchChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onSearchChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onSearchChanged(); }
        });

        categoryCombo = new JComboBox<>();
        categoryCombo.addItem("All 10 Categories");
        for (String c : CATEGORIES) categoryCombo.addItem(c);
        categoryCombo.setPreferredSize(new Dimension(240, 34));
        categoryCombo.addActionListener(e -> {
            if (suppressEvents) return;
            int idx = categoryCombo.getSelectedIndex();
            selectedCategory = idx <= 0 ? "ALL" : CATEGORIES.get(idx - 1);
            rebuildCategoryChips();
            renderSkillCards();
        });

        fTop.add(searchField, BorderLayout.CENTER);
        fTop.add(categoryCombo, BorderLayout.EAST);

        categoryChipsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        categoryChipsRow.setOpaque(false);
        categoryChipsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        filterCard.add(statusBanner);
        filterCard.add(Box.createVerticalStrut(4));
        filterCard.add(fTop);
        filterCard.add(Box.createVerticalStrut(8));
        filterCard.add(categoryChipsRow);

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(header);
        topStack.add(Box.createVerticalStrut(12));
        topStack.add(filterCard);

        // Main Split Area (Left: Skill Cards Grid, Right: Talent Distribution)
        JPanel split = new JPanel(new BorderLayout(16, 0));
        split.setOpaque(false);

        skillsGrid = new JPanel(new GridLayout(0, 2, 12, 12));
        skillsGrid.setOpaque(false);
        JPanel gridWrap = new JPanel(new BorderLayout());
        gridWrap.setOpaque(false);
        gridWrap.add(skillsGrid, BorderLayout.NORTH);

        JScrollPane leftScroll = new JScrollPane(gridWrap);
        leftScroll.setBorder(null);
        leftScroll.getVerticalScrollBar().setUnitIncrement(16);
        leftScroll.getViewport().setBackground(ThemeManager.surfaceBg());

        RoundedCard inspectorCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        inspectorCard.setLayout(new BorderLayout(0, 10));
        inspectorCard.setBorder(new EmptyBorder(16, 16, 16, 16));
        inspectorCard.setPreferredSize(new Dimension(350, 480));

        JPanel inspHead = new JPanel(new BorderLayout());
        inspHead.setOpaque(false);
        inspHead.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.cardBorder()),
                new EmptyBorder(0, 0, 10, 0)
        ));
        JPanel ihCol = new JPanel();
        ihCol.setOpaque(false);
        ihCol.setLayout(new BoxLayout(ihCol, BoxLayout.Y_AXIS));
        JLabel ihTitle = new JLabel("Talent Distribution");
        ihTitle.setFont(ThemeManager.font(Font.BOLD, 14.5f));
        ihTitle.setForeground(ThemeManager.primaryText());
        inspectorSubtitle = new JLabel("Select a skill to view qualified candidates");
        inspectorSubtitle.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        inspectorSubtitle.setForeground(ThemeManager.mutedText());
        ihCol.add(ihTitle);
        ihCol.add(Box.createVerticalStrut(2));
        ihCol.add(inspectorSubtitle);
        inspHead.add(ihCol, BorderLayout.CENTER);

        inspectorBody = new JPanel();
        inspectorBody.setOpaque(false);
        inspectorBody.setLayout(new BoxLayout(inspectorBody, BoxLayout.Y_AXIS));
        JScrollPane inspScroll = new JScrollPane(inspectorBody);
        inspScroll.setBorder(null);
        inspScroll.getVerticalScrollBar().setUnitIncrement(14);
        inspScroll.getViewport().setBackground(ThemeManager.cardBg());

        inspectorCard.add(inspHead, BorderLayout.NORTH);
        inspectorCard.add(inspScroll, BorderLayout.CENTER);

        split.add(leftScroll, BorderLayout.CENTER);
        split.add(inspectorCard, BorderLayout.EAST);

        add(topStack, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    private void onSearchChanged() {
        searchQuery = searchField.getText().trim().toLowerCase(Locale.ROOT);
        renderSkillCards();
    }

    private void rebuildCategoryChips() {
        categoryChipsRow.removeAll();
        addCatChip("ALL", "All (" + skills.size() + ")");
        for (String cat : CATEGORIES) {
            long count = skills.stream().filter(s -> cat.equalsIgnoreCase(s.getCategory())).count();
            addCatChip(cat, cat + " (" + count + ")");
        }
        categoryChipsRow.revalidate();
        categoryChipsRow.repaint();
    }

    private void addCatChip(String catKey, String label) {
        boolean active = catKey.equals(selectedCategory);
        ActionButton b = new ActionButton(
                label,
                active ? ThemeManager.NAVY_DEEP : ThemeManager.cardSubtleBg(),
                active ? ThemeManager.NAVY_MIDNIGHT : ThemeManager.cardBorder(),
                active ? ThemeManager.CYAN_ACCENT : ThemeManager.secondaryText(),
                active ? ThemeManager.BLUE_ELECTRIC : null,
                8
        );
        b.setFont(ThemeManager.font(Font.BOLD, 10.5f));
        b.setMargin(new Insets(4, 9, 4, 9));
        b.addActionListener(e -> {
            selectedCategory = catKey;
            suppressEvents = true;
            categoryCombo.setSelectedItem("ALL".equals(catKey) ? "All 10 Categories" : catKey);
            suppressEvents = false;
            rebuildCategoryChips();
            renderSkillCards();
        });
        categoryChipsRow.add(b);
    }

    private void renderSkillCards() {
        skillsGrid.removeAll();
        List<Skill> filtered = skills.stream()
                .filter(s -> "ALL".equals(selectedCategory) || selectedCategory.equalsIgnoreCase(s.getCategory()))
                .filter(s -> searchQuery.isEmpty()
                        || safeLower(s.getName()).contains(searchQuery)
                        || safeLower(s.getDescription()).contains(searchQuery))
                .toList();

        if (filtered.isEmpty()) {
            RoundedCard empty = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
            empty.setLayout(new GridBagLayout());
            empty.setPreferredSize(new Dimension(400, 120));
            JLabel lbl = new JLabel("No skills match your category or search query.");
            lbl.setForeground(ThemeManager.mutedText());
            empty.add(lbl);
            skillsGrid.add(empty);
        } else {
            boolean canManage = canManageSkills(AppSession.getInstance().getCurrentUser());
            for (Skill skill : filtered) {
                skillsGrid.add(buildSkillCard(skill, canManage));
            }
        }
        skillsGrid.revalidate();
        skillsGrid.repaint();
    }

    private JPanel buildSkillCard(Skill skill, boolean canManage) {
        boolean isSelected = inspectingSkill != null && inspectingSkill.getId().equals(skill.getId());
        Color bg = isSelected ? new Color(38, 132, 255, 16) : ThemeManager.cardBg();
        Color border = isSelected ? ThemeManager.CYAN_DARK : ThemeManager.cardBorder();

        RoundedCard card = new RoundedCard(14, bg, border, ThemeManager.BLUE_ELECTRIC, isSelected ? 1.5f : 1.0f);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(14, 16, 12, 16));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                inspectingSkill = skill;
                renderSkillCards();
                renderInspector();
            }
        });

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        JPanel titleCol = new JPanel();
        titleCol.setOpaque(false);
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(skill.getName());
        name.setFont(ThemeManager.font(Font.BOLD, 13.5f));
        name.setForeground(ThemeManager.primaryText());
        JLabel cat = new JLabel(skill.getCategory());
        cat.setFont(ThemeManager.font(Font.BOLD, 10.5f));
        cat.setForeground(ThemeManager.CYAN_DARK);
        titleCol.add(name);
        titleCol.add(cat);
        top.add(titleCol, BorderLayout.CENTER);

        if (canManage) {
            JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            tools.setOpaque(false);
            ActionButton edit = new ActionButton("Edit", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.secondaryText(), null, 6);
            edit.setFont(ThemeManager.font(Font.BOLD, 10));
            edit.setMargin(new Insets(2, 6, 2, 6));
            edit.addActionListener(e -> openSkillForm(skill));
            ActionButton del = new ActionButton("Del", ThemeManager.ROSE_SOFT, new Color(0xFE, 0xCA, 0xCA), ThemeManager.ROSE_PRIMARY, null, 6);
            del.setFont(ThemeManager.font(Font.BOLD, 10));
            del.setMargin(new Insets(2, 6, 2, 6));
            del.addActionListener(e -> confirmDeleteSkill(skill));
            tools.add(edit);
            tools.add(del);
            top.add(tools, BorderLayout.EAST);
        }

        String descText = skill.getDescription() != null && !skill.getDescription().isBlank()
                ? skill.getDescription()
                : "Core curriculum competency domain";
        JLabel desc = new JLabel("<html><body style='width:290px'>" + escapeHtml(descText) + "</body></html>");
        desc.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        desc.setForeground(ThemeManager.secondaryText());

        long enrolled = students.stream()
                .filter(st -> st.getSkills().stream().anyMatch(sk -> sk.getSkillId().equals(skill.getId())))
                .count();

        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.cardBorder()),
                new EmptyBorder(8, 0, 0, 0)
        ));
        JLabel cnt = new JLabel(enrolled + " Students Qualified");
        cnt.setFont(ThemeManager.font(Font.PLAIN, 11));
        cnt.setForeground(ThemeManager.mutedText());
        JLabel act = new JLabel(isSelected ? "Inspecting  ✓" : "View Students  →");
        act.setFont(ThemeManager.font(Font.BOLD, 11));
        act.setForeground(ThemeManager.CYAN_DARK);
        foot.add(cnt, BorderLayout.WEST);
        foot.add(act, BorderLayout.EAST);

        card.add(top, BorderLayout.NORTH);
        card.add(desc, BorderLayout.CENTER);
        card.add(foot, BorderLayout.SOUTH);
        return card;
    }

    private void renderInspector() {
        inspectorBody.removeAll();
        if (inspectingSkill == null) {
            inspectorSubtitle.setText("Select a skill to view qualified candidates");
            JLabel hint = new JLabel("<html><body style='width:280px; text-align:center;'>Click on any technical skill on the left to inspect GCET students assessed in that domain.</body></html>");
            hint.setFont(ThemeManager.font(Font.PLAIN, 12));
            hint.setForeground(ThemeManager.mutedText());
            hint.setBorder(new EmptyBorder(40, 10, 20, 10));
            inspectorBody.add(hint);
        } else {
            inspectorSubtitle.setText(inspectingSkill.getName() + "  ·  " + inspectingSkill.getCategory());
            List<Student> matched = new ArrayList<>();
            Map<String, StudentSkill> matchedSkillMap = new HashMap<>();
            for (Student st : students) {
                for (StudentSkill sk : st.getSkills()) {
                    if (sk.getSkillId().equals(inspectingSkill.getId())) {
                        matched.add(st);
                        matchedSkillMap.put(st.getId(), sk);
                        break;
                    }
                }
            }

            if (matched.isEmpty()) {
                JLabel none = new JLabel("<html><body style='width:280px;'>No GCET students have recorded proficiency in "
                        + escapeHtml(inspectingSkill.getName()) + " yet.</body></html>");
                none.setFont(ThemeManager.font(Font.PLAIN, 12));
                none.setForeground(ThemeManager.mutedText());
                none.setBorder(new EmptyBorder(30, 8, 10, 8));
                inspectorBody.add(none);
            } else {
                for (Student st : matched) {
                    StudentSkill sk = matchedSkillMap.get(st.getId());
                    int prof = Math.min(5, Math.max(1, sk.getProficiency()));
                    RoundedCard row = new RoundedCard(10, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.BLUE_ELECTRIC, 1.0f);
                    row.setLayout(new BorderLayout(8, 2));
                    row.setBorder(new EmptyBorder(10, 12, 10, 12));
                    row.setAlignmentX(Component.LEFT_ALIGNMENT);
                    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
                    row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    row.addMouseListener(new MouseAdapter() {
                        @Override public void mouseClicked(MouseEvent e) {
                            if (onSelectStudentProfile != null) {
                                onSelectStudentProfile.accept(st.getId());
                            }
                        }
                    });

                    JPanel left = new JPanel();
                    left.setOpaque(false);
                    left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
                    JLabel sn = new JLabel(st.getFullName());
                    sn.setFont(ThemeManager.font(Font.BOLD, 12.5f));
                    sn.setForeground(ThemeManager.primaryText());
                    JLabel sm = new JLabel(st.getRollNumber() + " · " + st.getBranch() + String.format(Locale.US, " (CGPA %.2f)", st.getCgpa()));
                    sm.setFont(ThemeManager.font(Font.PLAIN, 11));
                    sm.setForeground(ThemeManager.mutedText());
                    left.add(sn);
                    left.add(sm);

                    JPanel right = new JPanel();
                    right.setOpaque(false);
                    right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
                    JLabel lv = new JLabel("Lvl " + prof + ": " + PROFICIENCY_LABELS[prof]);
                    lv.setFont(ThemeManager.font(Font.BOLD, 11));
                    lv.setForeground(ThemeManager.CYAN_DARK);
                    lv.setAlignmentX(Component.RIGHT_ALIGNMENT);
                    JLabel vf = new JLabel(sk.isVerifiedByFaculty() ? "✓ Verified" : "Self-Assessed");
                    vf.setFont(ThemeManager.font(Font.BOLD, 10.5f));
                    vf.setForeground(sk.isVerifiedByFaculty() ? ThemeManager.EMERALD_PRIMARY : ThemeManager.mutedText());
                    vf.setAlignmentX(Component.RIGHT_ALIGNMENT);
                    right.add(lv);
                    right.add(vf);

                    row.add(left, BorderLayout.CENTER);
                    row.add(right, BorderLayout.EAST);
                    inspectorBody.add(row);
                    inspectorBody.add(Box.createVerticalStrut(8));
                }
            }
        }
        inspectorBody.revalidate();
        inspectorBody.repaint();
    }

    private void openSkillForm(Skill editing) {
        JTextField nameField = new JTextField(editing != null ? editing.getName() : "");
        JComboBox<String> catCombo = new JComboBox<>(CATEGORIES.toArray(new String[0]));
        if (editing != null && editing.getCategory() != null) {
            catCombo.setSelectedItem(editing.getCategory());
        }
        JTextArea descArea = new JTextArea(editing != null && editing.getDescription() != null ? editing.getDescription() : "", 3, 28);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 6));
        p.add(new JLabel("Skill Name *:"));
        p.add(nameField);
        p.add(new JLabel("Skill Category *:"));
        p.add(catCombo);
        p.add(new JLabel("Description & Assessment Topics:"));
        p.add(new JScrollPane(descArea));

        String title = editing == null ? "Add New Skill to Matrix" : "Edit Skill: " + editing.getName();
        if (JOptionPane.showConfirmDialog(this, p, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        if (nameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Skill name is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        User actor = AppSession.getInstance().getCurrentUser();
        Skill sk = new Skill(
                editing != null ? editing.getId() : null,
                nameField.getText().trim(),
                catCombo.getSelectedItem() != null ? catCombo.getSelectedItem().toString() : CATEGORIES.get(0),
                descArea.getText().trim()
        );

        if (editing == null) {
            controller.createSkillAsync(actor, sk, res -> {
                if (res.success) {
                    JOptionPane.showMessageDialog(this, res.message, "Skill Created", JOptionPane.INFORMATION_MESSAGE);
                    loadData();
                } else {
                    JOptionPane.showMessageDialog(this, res.message, "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        } else {
            controller.updateSkillAsync(actor, sk, res -> {
                if (res.success) {
                    JOptionPane.showMessageDialog(this, res.message, "Skill Updated", JOptionPane.INFORMATION_MESSAGE);
                    loadData();
                } else {
                    JOptionPane.showMessageDialog(this, res.message, "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
        }
    }

    private void confirmDeleteSkill(Skill skill) {
        if (JOptionPane.showConfirmDialog(this,
                "Delete skill \"" + skill.getName() + "\" from MySQL `skills` catalog?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        User actor = AppSession.getInstance().getCurrentUser();
        controller.deleteSkillAsync(actor, skill, res -> {
            if (res.success) {
                if (inspectingSkill != null && inspectingSkill.getId().equals(skill.getId())) {
                    inspectingSkill = null;
                }
                JOptionPane.showMessageDialog(this, res.message, "Skill Deleted", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Cannot Delete Skill", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static String safeLower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
