package edu.gcet.placementhub.ui.campus;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.models.CampusConnectModels.*;
import edu.gcet.placementhub.models.GctcAnnouncement;
import edu.gcet.placementhub.services.CampusConnectService;
import edu.gcet.placementhub.utils.ResourceLoader;
import edu.gcet.placementhub.utils.UiComponents.*;
import static edu.gcet.placementhub.utils.UiComponents.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Exact Java Swing recreation of CampusConnectPage.tsx:
 * Official GCET Campus Hero, GCTC Portal sync status, and 7 institutional tabs
 * (Announcements, Results & Hall Ticket Lookup, Placements & CDC, Clubs, Festivals, Resources & Intake Matrix, Contacts).
 */
public class CampusConnectPanel extends JPanel {

    private final CampusConnectService service = new CampusConnectService();
    private final Consumer<String> onNavigate;

    private final JPanel rootContent;
    private final CardLayout tabCardLayout = new CardLayout();
    private final JPanel tabContentPanel = new JPanel(tabCardLayout);
    private final Map<String, ActionButton> tabButtons = new LinkedHashMap<>();

    private PortalSyncStatus syncStatus;
    private String activeTabId = "announcements";
    private String selectedCategory = "ALL";
    private String searchQuery = "";

    private JLabel syncStatusLabel;
    private ActionButton syncLiveBtn;
    private JPanel announcementsGrid;
    private JTextField hallTicketField;
    private JLabel hallTicketFeedbackLabel;

    public CampusConnectPanel(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
        this.syncStatus = service.getSyncStatus(false);

        setLayout(new BorderLayout());
        setBackground(ThemeManager.surfaceBg());

        rootContent = new JPanel();
        rootContent.setLayout(new BoxLayout(rootContent, BoxLayout.Y_AXIS));
        rootContent.setBackground(ThemeManager.surfaceBg());
        rootContent.setBorder(new EmptyBorder(20, 22, 26, 22));

        JScrollPane scroll = new JScrollPane(rootContent);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getViewport().setBackground(ThemeManager.surfaceBg());
        add(scroll, BorderLayout.CENTER);

        rebuildAll();
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        rootContent.setBackground(ThemeManager.surfaceBg());
        rebuildAll();
    }

    private void rebuildAll() {
        rootContent.removeAll();
        tabButtons.clear();
        tabContentPanel.removeAll();

        JPanel hero = buildHeroBanner();
        hero.setAlignmentX(Component.LEFT_ALIGNMENT);
        rootContent.add(hero);
        rootContent.add(Box.createVerticalStrut(16));

        JPanel tabBar = buildTabBar();
        tabBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        rootContent.add(tabBar);
        rootContent.add(Box.createVerticalStrut(16));

        tabContentPanel.setOpaque(false);
        tabContentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabContentPanel.add(buildAnnouncementsTab(), "announcements");
        tabContentPanel.add(buildResultsTab(), "results");
        tabContentPanel.add(buildPlacementsTab(), "placements");
        tabContentPanel.add(buildClubsTab(), "clubs");
        tabContentPanel.add(buildFestivalsTab(), "festivals");
        tabContentPanel.add(buildResourcesTab(), "resources");
        tabContentPanel.add(buildContactsTab(), "contacts");

        rootContent.add(tabContentPanel);
        switchTab(activeTabId);

        rootContent.revalidate();
        rootContent.repaint();
    }

    private JPanel buildHeroBanner() {
        BufferedImage campusPhoto = ResourceLoader.loadCampusImage();
        JPanel hero = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int w = getWidth();
                int h = getHeight();
                Shape clip = new RoundRectangle2D.Float(0, 0, w - 1f, h - 1f, 22, 22);
                g2.setClip(clip);

                g2.setColor(new Color(0x0A, 0x12, 0x26));
                g2.fillRect(0, 0, w, h);
                if (campusPhoto != null) {
                    double scale = Math.max((double) w / campusPhoto.getWidth(), (double) h / campusPhoto.getHeight());
                    int drawW = (int) Math.round(campusPhoto.getWidth() * scale);
                    int drawH = (int) Math.round(campusPhoto.getHeight() * scale);
                    int drawX = (w - drawW) / 2;
                    int drawY = (int) Math.round((h - drawH) * 0.35);
                    g2.drawImage(campusPhoto, drawX, drawY, drawW, drawH, null);
                }

                LinearGradientPaint grad = new LinearGradientPaint(
                        0f, 0f, w, 0f,
                        new float[]{0.0f, 0.55f, 1.0f},
                        new Color[]{
                                new Color(7, 14, 32, 240),
                                new Color(9, 21, 51, 200),
                                new Color(11, 23, 54, 125)
                        }
                );
                g2.setPaint(grad);
                g2.fillRect(0, 0, w, h);

                g2.setClip(null);
                g2.setColor(new Color(51, 65, 85, 160));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(clip);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        hero.setOpaque(false);
        hero.setBorder(new EmptyBorder(24, 28, 24, 28));
        hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 290));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badges.setOpaque(false);
        badges.setAlignmentX(Component.LEFT_ALIGNMENT);
        badges.add(new BadgeLabel("GEETHANJALI COLLEGE OF ENGINEERING AND TECHNOLOGY",
                new Color(38, 132, 255, 60), ThemeManager.CYAN_ACCENT, new Color(50, 197, 244, 90), 999));
        badges.add(new BadgeLabel("UGC AUTONOMOUS · CHEERYAL, KEESARA (501301)",
                new Color(15, 23, 42, 165), new Color(0xE2, 0xE8, 0xF0), new Color(255, 255, 255, 45), 999));

        JLabel title = new JLabel("<html>CAMPUS <span style='color:#32C5F4;'>CONNECT</span></html>");
        title.setFont(ThemeManager.font(Font.BOLD, 28));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sub = new JLabel("Official GCET Campus Information & Academic Portal");
        sub.setFont(ThemeManager.font(Font.BOLD, 15));
        sub.setForeground(new Color(0xBA, 0xE6, 0xFD));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel desc = new JLabel(
                "<html><body style='width:560px; line-height:1.4;'>"
                        + "Connect with the official digital ecosystem of Geethanjali College of Engineering and Technology. "
                        + "Access official institutional information, academic resources, announcements, examination results, "
                        + "student clubs, festivals, and placement services."
                        + "</body></html>"
        );
        desc.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        desc.setForeground(new Color(0xE2, 0xE8, 0xF0));
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel syncRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        syncRow.setOpaque(false);
        syncRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        syncStatusLabel = new BadgeLabel(
                "●  " + syncStatus.statusMessage() + " · Synced: " + syncStatus.lastSyncTimestamp(),
                new Color(0, 0, 0, 130),
                new Color(0xD1, 0xFA, 0xE5),
                new Color(16, 185, 129, 90),
                12
        );
        syncStatusLabel.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        syncStatusLabel.setBorder(new EmptyBorder(6, 12, 6, 12));

        syncLiveBtn = new ActionButton(
                "↻  Sync Live",
                new Color(38, 132, 255, 70),
                new Color(38, 132, 255, 115),
                ThemeManager.CYAN_ACCENT,
                new Color(50, 197, 244, 100),
                12
        );
        syncLiveBtn.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        syncLiveBtn.setMargin(new Insets(6, 12, 6, 12));
        syncLiveBtn.addActionListener(e -> {
            syncLiveBtn.setEnabled(false);
            syncLiveBtn.setText("Refreshing...");
            Timer t = new Timer(450, ev -> {
                syncStatus = service.getSyncStatus(true);
                syncStatusLabel.setText("●  " + syncStatus.statusMessage() + " · Synced: " + syncStatus.lastSyncTimestamp());
                syncLiveBtn.setText("↻  Sync Live");
                syncLiveBtn.setEnabled(true);
            });
            t.setRepeats(false);
            t.start();
        });

        syncRow.add(syncStatusLabel);
        syncRow.add(syncLiveBtn);

        left.add(badges);
        left.add(Box.createVerticalStrut(10));
        left.add(title);
        left.add(Box.createVerticalStrut(2));
        left.add(sub);
        left.add(Box.createVerticalStrut(8));
        left.add(desc);
        left.add(Box.createVerticalStrut(12));
        left.add(syncRow);

        gbc.gridx = 0;
        gbc.weightx = 1.0;
        hero.add(left, gbc);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setPreferredSize(new Dimension(265, 180));

        ActionButton gctcBtn = new ActionButton(
                "Open Official GCTC Portal  ↗",
                ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE,
                new Color(96, 165, 250, 110), 12
        );
        gctcBtn.setMaximumSize(new Dimension(265, 42));
        gctcBtn.setPreferredSize(new Dimension(265, 42));
        gctcBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        gctcBtn.addActionListener(e -> ResourceLoader.openExternalUrl(this, CampusConnectService.URL_GCTC_PORTAL));

        ActionButton gcetSiteBtn = new ActionButton(
                "GCET Official Website  ↗",
                new Color(15, 23, 42, 195), new Color(30, 41, 59, 225), Color.WHITE,
                new Color(255, 255, 255, 55), 12
        );
        gcetSiteBtn.setMaximumSize(new Dimension(265, 40));
        gcetSiteBtn.setPreferredSize(new Dimension(265, 40));
        gcetSiteBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        gcetSiteBtn.addActionListener(e -> ResourceLoader.openExternalUrl(this, CampusConnectService.URL_OFFICIAL));

        JLabel gatewayNote = new JLabel("●  Verified Institutional GCTC Gateway");
        gatewayNote.setFont(ThemeManager.font(Font.PLAIN, 11));
        gatewayNote.setForeground(new Color(0xCB, 0xD5, 0xE1));
        gatewayNote.setAlignmentX(Component.CENTER_ALIGNMENT);

        right.add(Box.createVerticalGlue());
        right.add(gctcBtn);
        right.add(Box.createVerticalStrut(10));
        right.add(gcetSiteBtn);
        right.add(Box.createVerticalStrut(12));
        right.add(gatewayNote);
        right.add(Box.createVerticalGlue());

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.insets = new Insets(0, 22, 0, 0);
        hero.add(right, gbc);

        return hero;
    }

    private JPanel buildTabBar() {
        RoundedCard bar = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        addTabButton(bar, "announcements", "Announcements (" + service.getAnnouncements("ALL", "").size() + ")");
        addTabButton(bar, "results", "Autonomous Results");
        addTabButton(bar, "placements", "Placements & CDC");
        addTabButton(bar, "clubs", "Clubs & Fraternities (8)");
        addTabButton(bar, "festivals", "Festivals (VIBGYOR)");
        addTabButton(bar, "resources", "Student Resources & Intake");
        addTabButton(bar, "contacts", "Campus Directory & Links");
        return bar;
    }

    private void addTabButton(JPanel bar, String id, String label) {
        ActionButton btn = new ActionButton(
                label,
                ThemeManager.cardBg(),
                ThemeManager.cardSubtleBg(),
                ThemeManager.secondaryText(),
                null,
                10
        );
        btn.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        btn.setMargin(new Insets(7, 12, 7, 12));
        btn.addActionListener(e -> switchTab(id));
        tabButtons.put(id, btn);
        bar.add(btn);
    }

    private void switchTab(String tabId) {
        this.activeTabId = tabId;
        tabCardLayout.show(tabContentPanel, tabId);
        for (Map.Entry<String, ActionButton> e : tabButtons.entrySet()) {
            boolean active = e.getKey().equals(tabId);
            e.getValue().updateColors(
                    active ? ThemeManager.BLUE_ELECTRIC : ThemeManager.cardBg(),
                    active ? ThemeManager.BLUE_HOVER : ThemeManager.cardSubtleBg(),
                    active ? Color.WHITE : ThemeManager.secondaryText(),
                    active ? ThemeManager.BLUE_ELECTRIC : null
            );
        }
    }

    // TAB 1: ANNOUNCEMENTS & CIRCULARS
    private JPanel buildAnnouncementsTab() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        RoundedCard filterCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        filterCard.setLayout(new BorderLayout(12, 8));
        filterCard.setBorder(new EmptyBorder(12, 14, 12, 14));
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        JPanel catRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        catRow.setOpaque(false);
        String[] cats = {"ALL", "EXAMINATION", "ACADEMIC", "PLACEMENTS", "CIRCULAR", "FESTIVAL"};
        Map<String, ActionButton> catBtns = new LinkedHashMap<>();

        for (String cat : cats) {
            String label = "ALL".equals(cat) ? "All Updates" : cat.charAt(0) + cat.substring(1).toLowerCase(Locale.ROOT);
            ActionButton b = new ActionButton(label, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.secondaryText(), null, 8);
            b.setFont(ThemeManager.font(Font.BOLD, 11));
            b.setMargin(new Insets(5, 10, 5, 10));
            b.addActionListener(e -> {
                selectedCategory = cat;
                updateCategoryButtons(catBtns);
                refreshAnnouncementsGrid();
            });
            catBtns.put(cat, b);
            catRow.add(b);
        }
        updateCategoryButtons(catBtns);

        JTextField searchField = createStyledTextField("Search notices, timetables...");
        searchField.setPreferredSize(new Dimension(250, 34));
        searchField.addActionListener(e -> {
            searchQuery = searchField.getText();
            refreshAnnouncementsGrid();
        });
        searchField.getDocument().addDocumentListener((SimpleDocumentListener) e -> {
            searchQuery = searchField.getText();
            refreshAnnouncementsGrid();
        });

        filterCard.add(catRow, BorderLayout.CENTER);
        filterCard.add(searchField, BorderLayout.EAST);

        announcementsGrid = new JPanel(new GridLayout(0, 2, 14, 14));
        announcementsGrid.setOpaque(false);
        announcementsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        refreshAnnouncementsGrid();

        panel.add(filterCard);
        panel.add(Box.createVerticalStrut(14));
        panel.add(announcementsGrid);
        return panel;
    }

    private void updateCategoryButtons(Map<String, ActionButton> catBtns) {
        for (Map.Entry<String, ActionButton> e : catBtns.entrySet()) {
            boolean active = e.getKey().equals(selectedCategory);
            e.getValue().updateColors(
                    active ? ThemeManager.NAVY_DEEP : ThemeManager.cardSubtleBg(),
                    active ? ThemeManager.NAVY_MIDNIGHT : ThemeManager.cardBorder(),
                    active ? ThemeManager.CYAN_ACCENT : ThemeManager.secondaryText(),
                    active ? new Color(38, 132, 255, 100) : null
            );
        }
    }

    private void refreshAnnouncementsGrid() {
        announcementsGrid.removeAll();
        List<GctcAnnouncement> list = service.getAnnouncements(selectedCategory, searchQuery);
        if (list.isEmpty()) {
            RoundedCard empty = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
            empty.setLayout(new GridBagLayout());
            empty.setPreferredSize(new Dimension(400, 120));
            JLabel lbl = new JLabel("No announcements match your search or selected category.");
            lbl.setForeground(ThemeManager.mutedText());
            empty.add(lbl);
            announcementsGrid.add(empty);
        } else {
            for (GctcAnnouncement ann : list) {
                announcementsGrid.add(buildAnnouncementCard(ann));
            }
        }
        announcementsGrid.revalidate();
        announcementsGrid.repaint();
    }

    private JPanel buildAnnouncementCard(GctcAnnouncement ann) {
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder(), ThemeManager.BLUE_ELECTRIC, 1.0f);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 18, 14, 18));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        Color badgeBg = switch (ann.category()) {
            case "EXAMINATION" -> new Color(225, 29, 72, 24);
            case "PLACEMENTS" -> new Color(38, 132, 255, 24);
            case "FESTIVAL" -> new Color(217, 119, 6, 24);
            default -> new Color(5, 150, 105, 24);
        };
        Color badgeFg = switch (ann.category()) {
            case "EXAMINATION" -> ThemeManager.ROSE_PRIMARY;
            case "PLACEMENTS" -> ThemeManager.BLUE_ELECTRIC;
            case "FESTIVAL" -> ThemeManager.AMBER_PRIMARY;
            default -> ThemeManager.EMERALD_PRIMARY;
        };

        BadgeLabel catBadge = new BadgeLabel(ann.category(), badgeBg, badgeFg, badgeBg, 999);
        JLabel dateLbl = new JLabel("◷ " + ann.date());
        dateLbl.setFont(ThemeManager.font(Font.PLAIN, 11));
        dateLbl.setForeground(ThemeManager.mutedText());
        top.add(catBadge, BorderLayout.WEST);
        top.add(dateLbl, BorderLayout.EAST);

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("<html><body style='width:390px'>" + escapeHtml(ann.title()) + "</body></html>");
        title.setFont(ThemeManager.font(Font.BOLD, 13.5f));
        title.setForeground(ThemeManager.primaryText());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sum = new JLabel("<html><body style='width:390px; line-height:1.45;'>" + escapeHtml(ann.summary()) + "</body></html>");
        sum.setFont(ThemeManager.font(Font.PLAIN, 12));
        sum.setForeground(ThemeManager.secondaryText());
        sum.setAlignmentX(Component.LEFT_ALIGNMENT);

        mid.add(title);
        mid.add(Box.createVerticalStrut(6));
        mid.add(sum);

        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.cardBorder()),
                new EmptyBorder(8, 0, 0, 0)
        ));
        JLabel by = new JLabel("By: " + ann.postedBy());
        by.setFont(ThemeManager.font(Font.PLAIN, 11));
        by.setForeground(ThemeManager.mutedText());

        ActionButton viewBtn = new ActionButton("View Circular  ↗", ThemeManager.cardSubtleBg(), ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_ELECTRIC, null, 8);
        viewBtn.setFont(ThemeManager.font(Font.BOLD, 11));
        viewBtn.setMargin(new Insets(4, 10, 4, 10));
        viewBtn.addActionListener(e -> ResourceLoader.openExternalUrl(this, ann.link()));

        foot.add(by, BorderLayout.WEST);
        foot.add(viewBtn, BorderLayout.EAST);

        card.add(top, BorderLayout.NORTH);
        card.add(mid, BorderLayout.CENTER);
        card.add(foot, BorderLayout.SOUTH);
        return card;
    }

    // TAB 2: AUTONOMOUS RESULTS & EXAM DESK
    private JPanel buildResultsTab() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        RoundedCard deskBanner = new RoundedCard(16, ThemeManager.NAVY_MIDNIGHT, ThemeManager.NAVY_DEEP);
        deskBanner.setLayout(new BoxLayout(deskBanner, BoxLayout.Y_AXIS));
        deskBanner.setBorder(new EmptyBorder(18, 22, 18, 22));
        deskBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        deskBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JPanel topRow = new JPanel(new BorderLayout(16, 8));
        topRow.setOpaque(false);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        JLabel hTitle = new JLabel("Autonomous Examination Results Desk");
        hTitle.setFont(ThemeManager.font(Font.BOLD, 16));
        hTitle.setForeground(Color.WHITE);
        JLabel hSub = new JLabel("Enter your GCET Hall Ticket Number (e.g. 21R11A0501) to verify published autonomous semester scorecards.");
        hSub.setFont(ThemeManager.font(Font.PLAIN, 12));
        hSub.setForeground(ThemeManager.SLATE_300);
        textCol.add(hTitle);
        textCol.add(Box.createVerticalStrut(3));
        textCol.add(hSub);

        JPanel form = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        form.setOpaque(false);
        hallTicketField = createStyledTextField("Roll Number (e.g. 21R11A0501)");
        hallTicketField.setPreferredSize(new Dimension(230, 36));
        hallTicketField.setFont(ThemeManager.monoFont(Font.BOLD, 12));
        ActionButton checkBtn = new ActionButton("Check Result", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        checkBtn.setPreferredSize(new Dimension(120, 36));
        checkBtn.addActionListener(e -> handleHallTicketLookup());
        hallTicketField.addActionListener(e -> handleHallTicketLookup());
        form.add(hallTicketField);
        form.add(checkBtn);

        topRow.add(textCol, BorderLayout.CENTER);
        topRow.add(form, BorderLayout.EAST);

        hallTicketFeedbackLabel = new JLabel(" ");
        hallTicketFeedbackLabel.setFont(ThemeManager.font(Font.PLAIN, 12));
        hallTicketFeedbackLabel.setForeground(ThemeManager.CYAN_ACCENT);
        hallTicketFeedbackLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        deskBanner.add(topRow);
        deskBanner.add(Box.createVerticalStrut(8));
        deskBanner.add(hallTicketFeedbackLabel);

        panel.add(deskBanner);
        panel.add(Box.createVerticalStrut(14));

        JPanel grid = new JPanel(new GridLayout(0, 2, 14, 14));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (ExamResultItem item : service.getResults()) {
            RoundedCard rc = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
            rc.setLayout(new BorderLayout(0, 8));
            rc.setBorder(new EmptyBorder(16, 18, 14, 18));

            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(new BadgeLabel(item.type(), new Color(38, 132, 255, 25), ThemeManager.BLUE_ELECTRIC, null, 999), BorderLayout.WEST);
            JLabel rel = new JLabel("Released: " + item.releaseDate());
            rel.setFont(ThemeManager.font(Font.PLAIN, 11));
            rel.setForeground(ThemeManager.mutedText());
            head.add(rel, BorderLayout.EAST);

            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            JLabel t = new JLabel("<html><body style='width:380px'>" + escapeHtml(item.title()) + "</body></html>");
            t.setFont(ThemeManager.font(Font.BOLD, 13.5f));
            t.setForeground(ThemeManager.primaryText());
            JLabel meta = new JLabel("Exam Session: " + item.examMonthYear() + "  ·  Regulations: " + String.join(", ", item.regulations()));
            meta.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
            meta.setForeground(ThemeManager.secondaryText());
            JLabel br = new JLabel("Branches: " + String.join(", ", item.branches()));
            br.setFont(ThemeManager.font(Font.PLAIN, 11));
            br.setForeground(ThemeManager.mutedText());
            body.add(t);
            body.add(Box.createVerticalStrut(4));
            body.add(meta);
            body.add(Box.createVerticalStrut(3));
            body.add(br);

            JPanel f = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            f.setOpaque(false);
            ActionButton openPortal = new ActionButton("Open GCTC Scorecards  ↗", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 8);
            openPortal.setFont(ThemeManager.font(Font.BOLD, 11));
            openPortal.setMargin(new Insets(5, 12, 5, 12));
            openPortal.addActionListener(e -> ResourceLoader.openExternalUrl(this, item.portalUrl()));
            f.add(openPortal);

            rc.add(head, BorderLayout.NORTH);
            rc.add(body, BorderLayout.CENTER);
            rc.add(f, BorderLayout.SOUTH);
            grid.add(rc);
        }
        panel.add(grid);
        return panel;
    }

    private void handleHallTicketLookup() {
        String clean = hallTicketField.getText() == null ? "" : hallTicketField.getText().trim().toUpperCase(Locale.ROOT);
        if (clean.isEmpty()) {
            hallTicketFeedbackLabel.setText("Enter a GCET roll number to check.");
            return;
        }
        if (clean.length() < 10) {
            hallTicketFeedbackLabel.setForeground(new Color(0xFD, 0xA4, 0xAF));
            hallTicketFeedbackLabel.setText("Invalid Roll Number. GCET roll numbers follow format 21R11A0501 (10 characters).");
        } else {
            hallTicketFeedbackLabel.setForeground(new Color(0x6E, 0xE7, 0xB7));
            hallTicketFeedbackLabel.setText("Record found on Autonomous Exam Server for " + clean + ". Opening official GCTC Portal...");
            ResourceLoader.openExternalUrl(this, CampusConnectService.URL_GCTC_PORTAL);
        }
    }

    // TAB 3: PLACEMENTS & CDC
    private JPanel buildPlacementsTab() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        RoundedCard card = new RoundedCard(16, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 24, 22, 24));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        BadgeLabel tag = new BadgeLabel("CAREER DEVELOPMENT CENTER (CDC)", new Color(38, 132, 255, 25), ThemeManager.BLUE_ELECTRIC, null, 999);
        tag.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Training, Corporate Readiness & Campus Placement Cell");
        title.setFont(ThemeManager.font(Font.BOLD, 18));
        title.setForeground(ThemeManager.primaryText());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel body = new JLabel(
                "<html><body style='width:820px; line-height:1.5;'>"
                        + "The Career Development Center (CDC) at Geethanjali College of Engineering and Technology orchestrates "
                        + "corporate readiness bootcamps, aptitude & DSA assessments, mock technical interviews, and marquee campus drives "
                        + "with Amazon, Microsoft, Oracle, TCS Digital, Infosys SP, Cognizant, Deloitte, and core engineering leaders."
                        + "</body></html>"
        );
        body.setFont(ThemeManager.font(Font.PLAIN, 13));
        body.setForeground(ThemeManager.secondaryText());
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        ActionButton stuBtn = new ActionButton("Student Directory", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        stuBtn.addActionListener(e -> onNavigate.accept("students"));

        ActionButton skBtn = new ActionButton("GCET Skill Matrix", ThemeManager.EMERALD_PRIMARY, ThemeManager.EMERALD_PRIMARY.darker(), Color.WHITE, null, 10);
        skBtn.addActionListener(e -> onNavigate.accept("skills"));

        ActionButton drBtn = new ActionButton("Placement Drives", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        drBtn.addActionListener(e -> onNavigate.accept("drives"));

        btnRow.add(stuBtn);
        btnRow.add(skBtn);
        btnRow.add(drBtn);

        card.add(tag);
        card.add(Box.createVerticalStrut(10));
        card.add(title);
        card.add(Box.createVerticalStrut(8));
        card.add(body);
        card.add(Box.createVerticalStrut(16));
        card.add(btnRow);

        panel.add(card);
        return panel;
    }

    // TAB 4: CLUBS & FRATERNITIES
    private JPanel buildClubsTab() {
        JPanel grid = new JPanel(new GridLayout(0, 2, 14, 14));
        grid.setOpaque(false);
        for (CollegeClub club : service.getClubs()) {
            Color accent = Color.decode(club.colorHex());
            RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder(), accent, 1.2f);
            card.setLayout(new BorderLayout(0, 8));
            card.setBorder(new EmptyBorder(16, 18, 14, 18));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            JLabel name = new JLabel(club.name());
            name.setFont(ThemeManager.font(Font.BOLD, 14));
            name.setForeground(ThemeManager.primaryText());
            BadgeLabel badge = new BadgeLabel(club.badge(), new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 26), accent, null, 999);
            top.add(name, BorderLayout.CENTER);
            top.add(badge, BorderLayout.EAST);

            JPanel mid = new JPanel();
            mid.setOpaque(false);
            mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
            JLabel desc = new JLabel("<html><body style='width:390px; line-height:1.45;'>" + escapeHtml(club.description()) + "</body></html>");
            desc.setFont(ThemeManager.font(Font.PLAIN, 12));
            desc.setForeground(ThemeManager.secondaryText());
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel coord = new JLabel("Faculty Coordinator: " + club.facultyCoordinator());
            coord.setFont(ThemeManager.font(Font.BOLD, 11.5f));
            coord.setForeground(ThemeManager.primaryText());
            coord.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel lead = new JLabel("Student Lead: " + club.studentLead());
            lead.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
            lead.setForeground(ThemeManager.mutedText());
            lead.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel acts = new JLabel("Key Activities: " + String.join(" · ", club.activities()));
            acts.setFont(ThemeManager.font(Font.PLAIN, 11));
            acts.setForeground(ThemeManager.CYAN_DARK);
            acts.setAlignmentX(Component.LEFT_ALIGNMENT);

            mid.add(desc);
            mid.add(Box.createVerticalStrut(8));
            mid.add(coord);
            mid.add(Box.createVerticalStrut(2));
            mid.add(lead);
            mid.add(Box.createVerticalStrut(6));
            mid.add(acts);

            card.add(top, BorderLayout.NORTH);
            card.add(mid, BorderLayout.CENTER);
            grid.add(card);
        }
        return grid;
    }

    // TAB 5: COLLEGE FESTIVALS
    private JPanel buildFestivalsTab() {
        JPanel grid = new JPanel(new GridLayout(0, 2, 14, 14));
        grid.setOpaque(false);
        for (CollegeFestival fest : service.getFestivals()) {
            RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder(), ThemeManager.AMBER_PRIMARY, 1.0f);
            card.setLayout(new BorderLayout(0, 10));
            card.setBorder(new EmptyBorder(18, 20, 16, 20));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            JPanel titleCol = new JPanel();
            titleCol.setOpaque(false);
            titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(fest.name());
            name.setFont(ThemeManager.font(Font.BOLD, 18));
            name.setForeground(ThemeManager.primaryText());
            JLabel tag = new JLabel(fest.tagline());
            tag.setFont(ThemeManager.font(Font.ITALIC, 12));
            tag.setForeground(ThemeManager.BLUE_ELECTRIC);
            titleCol.add(name);
            titleCol.add(tag);

            BadgeLabel month = new BadgeLabel(fest.scheduleMonth(), new Color(217, 119, 6, 26), ThemeManager.AMBER_PRIMARY, null, 999);
            top.add(titleCol, BorderLayout.CENTER);
            top.add(month, BorderLayout.EAST);

            JPanel mid = new JPanel();
            mid.setOpaque(false);
            mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
            JLabel desc = new JLabel("<html><body style='width:390px; line-height:1.45;'>" + escapeHtml(fest.description()) + "</body></html>");
            desc.setFont(ThemeManager.font(Font.PLAIN, 12));
            desc.setForeground(ThemeManager.secondaryText());
            desc.setAlignmentX(Component.LEFT_ALIGNMENT);

            StringBuilder hlHtml = new StringBuilder("<html><body style='width:390px;'><b>Signature Highlights:</b><ul style='margin-top:4px; margin-left:16px;'>");
            for (String h : fest.highlights()) {
                hlHtml.append("<li>").append(escapeHtml(h)).append("</li>");
            }
            hlHtml.append("</ul></body></html>");
            JLabel hl = new JLabel(hlHtml.toString());
            hl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
            hl.setForeground(ThemeManager.primaryText());
            hl.setAlignmentX(Component.LEFT_ALIGNMENT);

            mid.add(desc);
            mid.add(Box.createVerticalStrut(8));
            mid.add(hl);

            card.add(top, BorderLayout.NORTH);
            card.add(mid, BorderLayout.CENTER);
            grid.add(card);
        }
        return grid;
    }

    // TAB 6: STUDENT RESOURCES & APPROVED INTAKE MATRIX
    private JPanel buildResourcesTab() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (StudentResource res : service.getResources()) {
            RoundedCard rc = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder(), ThemeManager.BLUE_ELECTRIC, 1.0f);
            rc.setLayout(new BorderLayout(0, 8));
            rc.setBorder(new EmptyBorder(14, 16, 14, 16));

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(new BadgeLabel(res.category().replace("_", " "), new Color(50, 197, 244, 24), ThemeManager.CYAN_DARK, null, 999), BorderLayout.WEST);

            JPanel mid = new JPanel();
            mid.setOpaque(false);
            mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
            JLabel t = new JLabel("<html><body style='width:250px'>" + escapeHtml(res.title()) + "</body></html>");
            t.setFont(ThemeManager.font(Font.BOLD, 13));
            t.setForeground(ThemeManager.primaryText());
            JLabel d = new JLabel("<html><body style='width:250px; line-height:1.4;'>" + escapeHtml(res.shortDescription()) + "</body></html>");
            d.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
            d.setForeground(ThemeManager.secondaryText());
            mid.add(t);
            mid.add(Box.createVerticalStrut(5));
            mid.add(d);

            ActionButton act = new ActionButton(res.actionText() + "  ↗", ThemeManager.cardSubtleBg(), ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_ELECTRIC, ThemeManager.cardBorder(), 8);
            act.setFont(ThemeManager.font(Font.BOLD, 11));
            act.addActionListener(e -> ResourceLoader.openExternalUrl(this, res.url()));

            rc.add(top, BorderLayout.NORTH);
            rc.add(mid, BorderLayout.CENTER);
            rc.add(act, BorderLayout.SOUTH);
            grid.add(rc);
        }

        panel.add(grid);
        panel.add(Box.createVerticalStrut(18));

        // Approved Intake Table
        RoundedCard intakeCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        intakeCard.setLayout(new BorderLayout(0, 10));
        intakeCard.setBorder(new EmptyBorder(18, 20, 18, 20));
        intakeCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel intakeHead = new JPanel(new BorderLayout());
        intakeHead.setOpaque(false);
        JLabel itTitle = new JLabel("AICTE / JNTUH Approved Branch Intake Matrix");
        itTitle.setFont(ThemeManager.font(Font.BOLD, 15));
        itTitle.setForeground(ThemeManager.primaryText());
        BadgeLabel totalBadge = new BadgeLabel("Total Sanctioned Intake: " + service.getTotalIntake() + " Seats",
                new Color(16, 185, 129, 25), ThemeManager.EMERALD_PRIMARY, null, 999);
        intakeHead.add(itTitle, BorderLayout.WEST);
        intakeHead.add(totalBadge, BorderLayout.EAST);

        String[] cols = {"Code", "Branch / Program Name", "Degree", "Sanctioned Intake", "Est. Year"};
        DefaultTableModel tm = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        for (BranchIntake bi : service.getBranchIntake()) {
            tm.addRow(new Object[]{bi.branchCode(), bi.branchName(), bi.degree(), bi.sanctionedIntake(), bi.establishedYear()});
        }

        JTable table = new JTable(tm);
        table.setRowHeight(30);
        table.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        table.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 12));
        table.setShowVerticalLines(false);
        DefaultTableCellRenderer rightRend = new DefaultTableCellRenderer();
        rightRend.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(3).setCellRenderer(rightRend);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(900, 360));
        tableScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder()));

        intakeCard.add(intakeHead, BorderLayout.NORTH);
        intakeCard.add(tableScroll, BorderLayout.CENTER);
        panel.add(intakeCard);

        return panel;
    }

    // TAB 7: CAMPUS DIRECTORY & OFFICIAL LINKS
    private JPanel buildContactsTab() {
        JPanel grid = new JPanel(new GridLayout(1, 2, 16, 0));
        grid.setOpaque(false);

        RoundedCard infoCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        infoCard.setLayout(new BoxLayout(infoCard, BoxLayout.Y_AXIS));
        infoCard.setBorder(new EmptyBorder(20, 22, 20, 22));

        JLabel k1 = new JLabel("INSTITUTIONAL CONTACT DIRECTORY");
        k1.setFont(ThemeManager.font(Font.BOLD, 11));
        k1.setForeground(ThemeManager.CYAN_DARK);
        k1.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel cName = new JLabel(CampusConnectService.COLLEGE_NAME);
        cName.setFont(ThemeManager.font(Font.BOLD, 17));
        cName.setForeground(ThemeManager.primaryText());
        cName.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel cStat = new JLabel(CampusConnectService.COLLEGE_AUTONOMOUS_STATUS + " · " + CampusConnectService.COLLEGE_ACCREDITATION);
        cStat.setFont(ThemeManager.font(Font.PLAIN, 12));
        cStat.setForeground(ThemeManager.mutedText());
        cStat.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoCard.add(k1);
        infoCard.add(Box.createVerticalStrut(4));
        infoCard.add(cName);
        infoCard.add(Box.createVerticalStrut(3));
        infoCard.add(cStat);
        infoCard.add(Box.createVerticalStrut(14));
        infoCard.add(contactRow("Campus Address", CampusConnectService.COLLEGE_ADDRESS));
        infoCard.add(contactRow("Front Office / Reception", CampusConnectService.CONTACT_FRONT_OFFICE));
        infoCard.add(contactRow("Autonomous Exam Branch", CampusConnectService.CONTACT_EXAM_BRANCH));
        infoCard.add(contactRow("Academic & Accounts", CampusConnectService.CONTACT_ACADEMIC + " / " + CampusConnectService.CONTACT_ACCOUNTS));
        infoCard.add(contactRow("Admissions AO (Mr. B. Surendra Reddy)", CampusConnectService.CONTACT_ADMISSIONS_AO));
        infoCard.add(contactRow("Institutional Emails", CampusConnectService.EMAIL_GENERAL + " · " + CampusConnectService.EMAIL_PLACEMENT));

        RoundedCard linksCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        linksCard.setLayout(new BoxLayout(linksCard, BoxLayout.Y_AXIS));
        linksCard.setBorder(new EmptyBorder(20, 22, 20, 22));

        JLabel k2 = new JLabel("OFFICIAL REFERENCE WEBSITES");
        k2.setFont(ThemeManager.font(Font.BOLD, 11));
        k2.setForeground(ThemeManager.CYAN_DARK);
        k2.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lTitle = new JLabel("Connected Institutional Ecosystem");
        lTitle.setFont(ThemeManager.font(Font.BOLD, 17));
        lTitle.setForeground(ThemeManager.primaryText());
        lTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        linksCard.add(k2);
        linksCard.add(Box.createVerticalStrut(4));
        linksCard.add(lTitle);
        linksCard.add(Box.createVerticalStrut(14));
        linksCard.add(externalSiteButton("GCTC Student Portal (gctcportal.in)",
                "Student lifecycle, exam scorecards, club magazines & seating arrangements",
                CampusConnectService.URL_GCTC_PORTAL));
        linksCard.add(Box.createVerticalStrut(10));
        linksCard.add(externalSiteButton("GCET Official Website (gcet.edu.in)",
                "College governance, departments, accreditation, admissions & faculty profiles",
                CampusConnectService.URL_OFFICIAL));
        linksCard.add(Box.createVerticalStrut(10));
        linksCard.add(externalSiteButton("Geethanjali Institutions (geethanjaliinstitutions.com)",
                "Geethanjali Group parent society leadership & institutions",
                CampusConnectService.URL_GROUP));
        linksCard.add(Box.createVerticalStrut(10));
        linksCard.add(externalSiteButton("JNTU Hyderabad (jntuh.ac.in)",
                "University affiliation, regulatory notifications & academic council",
                CampusConnectService.URL_JNTUH));

        grid.add(infoCard);
        grid.add(linksCard);
        return grid;
    }

    private JPanel contactRow(String label, String val) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(0, 0, 10, 0));
        JLabel l = new JLabel(label);
        l.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        l.setForeground(ThemeManager.primaryText());
        JLabel v = new JLabel("<html><body style='width:390px'>" + escapeHtml(val) + "</body></html>");
        v.setFont(ThemeManager.font(Font.PLAIN, 12));
        v.setForeground(ThemeManager.secondaryText());
        row.add(l);
        row.add(v);
        return row;
    }

    private JPanel externalSiteButton(String title, String subtitle, String url) {
        RoundedCard row = new RoundedCard(12, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.BLUE_ELECTRIC, 1.0f);
        row.setLayout(new BorderLayout(10, 0));
        row.setBorder(new EmptyBorder(12, 14, 12, 14));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel t = new JLabel(title);
        t.setFont(ThemeManager.font(Font.BOLD, 12.5f));
        t.setForeground(ThemeManager.primaryText());
        JLabel s = new JLabel(subtitle);
        s.setFont(ThemeManager.font(Font.PLAIN, 11));
        s.setForeground(ThemeManager.mutedText());
        left.add(t);
        left.add(Box.createVerticalStrut(2));
        left.add(s);

        ActionButton open = new ActionButton("Open  ↗", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 8);
        open.setFont(ThemeManager.font(Font.BOLD, 11));
        open.addActionListener(e -> ResourceLoader.openExternalUrl(this, url));

        row.add(left, BorderLayout.CENTER);
        row.add(open, BorderLayout.EAST);
        return row;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @FunctionalInterface
    private interface SimpleDocumentListener extends javax.swing.event.DocumentListener {
        void update(javax.swing.event.DocumentEvent e);
        @Override default void insertUpdate(javax.swing.event.DocumentEvent e) { update(e); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent e) { update(e); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent e) { update(e); }
    }
}
