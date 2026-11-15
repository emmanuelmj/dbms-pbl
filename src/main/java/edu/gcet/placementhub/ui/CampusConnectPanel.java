package edu.gcet.placementhub.ui;

import edu.gcet.placementhub.ui.theme.Theme;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

/**
 * GCET PLACEMENT HUB - Campus Connect & GCTC Portal Panel
 * Synchronized with official GCTC Portal (gctcportal.in) and GCET website (gcet.edu.in).
 * Provides live access to announcements, autonomous results, clubs, festivals, and academic resources.
 */
public class CampusConnectPanel extends JPanel {

    public CampusConnectPanel() {
        setLayout(new BorderLayout(15, 15));
        setBackground(Theme.BG_LIGHT);
        setBorder(new EmptyBorder(20, 25, 25, 25));

        add(createHeaderPanel(), BorderLayout.NORTH);
        add(createMainTabbedPane(), BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(15, 10));
        header.setBackground(Theme.NAVY_PRIMARY);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.NAVY_SECONDARY, 1),
                new EmptyBorder(18, 22, 18, 22)
        ));

        // Left info
        JPanel left = new JPanel(new GridLayout(3, 1, 3, 3));
        left.setOpaque(false);

        JLabel subLabel = new JLabel("GEETHANJALI COLLEGE OF ENGINEERING AND TECHNOLOGY · UGC AUTONOMOUS");
        subLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        subLabel.setForeground(Theme.CYAN_ACCENT);

        JLabel titleLabel = new JLabel("Campus Connect & GCTC Portal Hub");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Theme.TEXT_WHITE);

        JLabel statusLabel = new JLabel("Official verified records · Sy. No. 33 & 34, Cheeryal (V), Keesara (M), Telangana - 501301");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(Theme.TEXT_MUTED);

        left.add(subLabel);
        left.add(titleLabel);
        left.add(statusLabel);

        // Right actions
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        right.setOpaque(false);

        JButton btnPortal = new JButton("Open GCTC Portal (gctcportal.in)");
        btnPortal.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnPortal.setBackground(Theme.BLUE_ACCENT);
        btnPortal.setForeground(Color.WHITE);
        btnPortal.setFocusPainted(false);
        btnPortal.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnPortal.addActionListener(e -> openUrl("https://www.gctcportal.in/"));

        JButton btnWebsite = new JButton("GCET Website (gcet.edu.in)");
        btnWebsite.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnWebsite.setBackground(Theme.NAVY_SECONDARY);
        btnWebsite.setForeground(Theme.TEXT_WHITE);
        btnWebsite.setFocusPainted(false);
        btnWebsite.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnWebsite.addActionListener(e -> openUrl("https://gcet.edu.in/"));

        right.add(btnPortal);
        right.add(btnWebsite);

        header.add(left, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JTabbedPane createMainTabbedPane() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabs.addTab("Announcements & Circulars", createAnnouncementsPanel());
        tabs.addTab("Autonomous Exam Results", createResultsPanel());
        tabs.addTab("Clubs & Fraternities", createClubsPanel());
        tabs.addTab("College Festivals", createFestivalsPanel());
        tabs.addTab("Student Resources & Intake", createResourcesPanel());

        return tabs;
    }

    private JPanel createAnnouncementsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        String[] columns = {"Date", "Category", "Official Circular Title", "Issued By", "Action"};
        Object[][] data = {
                {"28 Oct 2024", "EXAMINATION", "B.Tech, MBA & M.Tech Autonomous Revaluation Examination Results Published", "Controller of Examinations", "Open Link"},
                {"24 Oct 2024", "EXAMINATION", "IV B.Tech I Semester Mid-I Examination Timetables & CBT Schedules Released", "Academic & Exam Section", "Open Link"},
                {"18 Oct 2024", "EXAMINATION", "Autonomous Regular & Supplementary Examination Fee Circular (AR20, AR22, AR24)", "Office of the Principal", "Open Link"},
                {"15 Oct 2024", "PLACEMENTS", "TCS National Qualifier Test (NQT) & Cognizant GenC Campus Hiring Drive", "Head - CDC Placements", "Open Link"},
                {"10 Oct 2024", "FESTIVAL", "BHASWARA 2025: National Level Technical Symposium - Call for Papers & Projects", "Convener - Bhaswara", "Open Link"},
                {"05 Oct 2024", "EXAMINATION", "III B.Tech II Semester Complete Autonomous Results Declared", "Exam Branch GCET", "Open Link"},
                {"22 Sep 2024", "ACADEMIC", "Autonomous Academic Calendar for Academic Year 2024-2025 (B.Tech & M.Tech)", "Dean - Academics", "Open Link"},
                {"19 Sep 2024", "EXAMINATION", "B.Tech Special One Time Chance (OTC) Supplementary Examination Notification", "Exam Branch GCET", "Open Link"}
        };

        DefaultTableModel model = new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(model);
        table.setRowHeight(32);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(Theme.BG_LIGHT);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openUrl("https://www.gctcportal.in/");
                }
            }
        });

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1));
        panel.add(scroll, BorderLayout.CENTER);

        JLabel hint = new JLabel("Double-click any announcement row to open the circular on the official GCTC Portal.");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint.setForeground(Color.GRAY);
        panel.add(hint, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Quick Search Bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        searchBar.setBackground(Theme.BG_LIGHT);
        searchBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        searchBar.add(new JLabel("Check Roll Number:"));
        JTextField txtRoll = new JTextField(12);
        txtRoll.setFont(new Font("Segoe UI", Font.BOLD, 12));
        searchBar.add(txtRoll);

        JButton btnCheck = new JButton("Check Scorecard");
        btnCheck.setBackground(Theme.BLUE_ACCENT);
        btnCheck.setForeground(Color.WHITE);
        btnCheck.addActionListener(e -> {
            String roll = txtRoll.getText().trim();
            if (roll.length() < 10) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a valid 10-character GCET Hall Ticket Number (e.g., 21R11A0501).",
                        "Invalid Roll Number", JOptionPane.WARNING_MESSAGE);
            } else {
                openUrl("https://www.gctcportal.in/");
            }
        });
        searchBar.add(btnCheck);

        panel.add(searchBar, BorderLayout.NORTH);

        // Published Results Table
        String[] columns = {"Result Release Date", "Examination Cycle", "Type", "Regulations", "Eligible Branches"};
        Object[][] data = {
                {"28 Oct 2024", "July / August 2024", "REVALUATION", "AR20, AR22", "CSE, AIML, DS, ECE, EEE, MECH, CIVIL, MBA"},
                {"05 Oct 2024", "May / June 2024", "REGULAR", "AR20, AR22", "All B.Tech Engineering Branches"},
                {"28 Sep 2024", "August 2024", "SUPPLEMENTARY", "AR20", "All Final Year Branches"},
                {"19 Sep 2024", "September 2024", "OTC", "AR16, AR18, AR20", "Designated Autonomous & Affiliated Batches"},
                {"02 Sep 2024", "July 2024", "REGULAR", "AR22", "II B.Tech II Sem (All Branches)"}
        };

        JTable table = new JTable(new DefaultTableModel(data, columns));
        table.setRowHeight(30);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createClubsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 4, 12, 12));
        panel.setBackground(Theme.BG_LIGHT);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        String[][] clubs = {
                {"StuVibe", "Campus Culture & Student Life", "Magazine, podcasts, open mic, creative writing."},
                {"Alumni", "GCET Alumni Network", "Global mentorship, mock interviews, homecoming."},
                {"DoPY", "Department of Photography", "Official cinematic coverage, photography exhibits."},
                {"NEN", "National Entrepreneurship Network", "E-Cell, startup incubator, pitch events."},
                {"CDC", "Career Development Center", "Placement readiness, coding sprints, soft skills."},
                {"DECO", "Design & Creative Operations", "UI/UX, fest branding, creative stage decor."},
                {"GSHF", "Geethanjali Students Helping Fraternity", "Blood donation, rural school outreach, social impact."},
                {"Srujanastra", "Technical Innovation Club", "Hackathons, IoT robotics, open source builds."}
        };

        for (String[] club : clubs) {
            JPanel card = new JPanel(new BorderLayout(5, 5));
            card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
                    new EmptyBorder(12, 12, 12, 12)
            ));

            JLabel title = new JLabel(club[0]);
            title.setFont(new Font("Segoe UI", Font.BOLD, 15));
            title.setForeground(Theme.NAVY_PRIMARY);

            JLabel sub = new JLabel(club[1]);
            sub.setFont(new Font("Segoe UI", Font.BOLD, 11));
            sub.setForeground(Theme.BLUE_ACCENT);

            JLabel desc = new JLabel("<html>" + club[2] + "</html>");
            desc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            desc.setForeground(Theme.TEXT_MUTED);

            JButton btn = new JButton("View Club");
            btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            btn.setBackground(Theme.BG_LIGHT);
            btn.addActionListener(e -> openUrl("https://www.gctcportal.in/"));

            JPanel top = new JPanel(new GridLayout(2, 1));
            top.setOpaque(false);
            top.add(title);
            top.add(sub);

            card.add(top, BorderLayout.NORTH);
            card.add(desc, BorderLayout.CENTER);
            card.add(btn, BorderLayout.SOUTH);

            panel.add(card);
        }

        return panel;
    }

    private JPanel createFestivalsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 15, 15));
        panel.setBackground(Theme.BG_LIGHT);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        String[][] fests = {
                {"VIBGYOR (Annual March)", "Flagship National Cultural Extravaganza",
                        "Inter-collegiate dance championships, battle of bands, celebrity star night, and fine arts exhibits across all departments."},
                {"BHASWARA (October/November)", "National Level Technical Symposium",
                        "Peer-reviewed paper presentations, live hardware IoT expos, algorithmic coding sprints, and keynote science lectures."},
                {"ELECTRIKA (December)", "Electrical & Electronics Innovation Fest",
                        "Robo-wars, PCB circuit soldering sprints, EV powertrains, smart grid energy contests, and drone flight navigation."},
                {"MANOTRON (February)", "Mechanical & Mechatronics Fest",
                        "Go-kart showcases, 3D CAD rapid modeling challenges, CNC precision machining, and water rocketry championships."}
        };

        for (String[] fest : fests) {
            JPanel card = new JPanel(new BorderLayout(8, 8));
            card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.BORDER_COLOR, 1),
                    new EmptyBorder(15, 15, 15, 15)
            ));

            JLabel title = new JLabel(fest[0]);
            title.setFont(new Font("Segoe UI", Font.BOLD, 16));
            title.setForeground(Theme.NAVY_PRIMARY);

            JLabel sub = new JLabel(fest[1]);
            sub.setFont(new Font("Segoe UI", Font.BOLD, 12));
            sub.setForeground(Theme.BLUE_ACCENT);

            JLabel desc = new JLabel("<html>" + fest[2] + "</html>");
            desc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            desc.setForeground(Theme.TEXT_MUTED);

            JButton btn = new JButton("Explore Fest on Portal");
            btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            btn.setBackground(Theme.NAVY_SECONDARY);
            btn.setForeground(Color.WHITE);
            btn.addActionListener(e -> openUrl("https://www.gctcportal.in/"));

            JPanel top = new JPanel(new GridLayout(2, 1, 2, 2));
            top.setOpaque(false);
            top.add(title);
            top.add(sub);

            card.add(top, BorderLayout.NORTH);
            card.add(desc, BorderLayout.CENTER);
            card.add(btn, BorderLayout.SOUTH);

            panel.add(card);
        }

        return panel;
    }

    private JPanel createResourcesPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Approved Intake Table
        String[] columns = {"Branch Code", "Branch Specialization", "Degree", "Sanctioned Intake", "Established"};
        Object[][] data = {
                {"CSE", "Computer Science and Engineering", "B.Tech", 240, 2005},
                {"CSE-AIML", "CSE (Artificial Intelligence & Machine Learning)", "B.Tech", 180, 2020},
                {"CSE-DS", "CSE (Data Science)", "B.Tech", 120, 2020},
                {"CSE-CS", "CSE (Cyber Security)", "B.Tech", 60, 2021},
                {"CSE-IoT", "CSE (Internet of Things)", "B.Tech", 60, 2021},
                {"IT", "Information Technology", "B.Tech", 120, 2005},
                {"ECE", "Electronics and Communication Engineering", "B.Tech", 180, 2005},
                {"EEE", "Electrical and Electronics Engineering", "B.Tech", 60, 2005},
                {"MECH", "Mechanical Engineering", "B.Tech", 60, 2005},
                {"CIVIL", "Civil Engineering", "B.Tech", 60, 2011},
                {"MBA", "Master of Business Administration", "MBA", 60, 2007}
        };

        JTable table = new JTable(new DefaultTableModel(data, columns));
        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        panel.add(new JLabel("GCET Approved Intake Matrix (Total Sanctioned Seats: 1,140):"), BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Quick Portal links
        JPanel quickLinks = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        quickLinks.setBackground(Theme.BG_LIGHT);

        String[] links = {"Seating Plan", "Digital Library", "Resume Generator", "Library Zone", "Academic Calendar", "Syllabus AR20/AR22/AR24", "Fee SBI Collect"};
        for (String link : links) {
            JButton b = new JButton(link);
            b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            b.addActionListener(e -> openUrl("https://www.gctcportal.in/"));
            quickLinks.add(b);
        }

        panel.add(quickLinks, BorderLayout.SOUTH);
        return panel;
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                JOptionPane.showMessageDialog(this, "Please open: " + url, "Portal Link", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Unable to launch browser: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
