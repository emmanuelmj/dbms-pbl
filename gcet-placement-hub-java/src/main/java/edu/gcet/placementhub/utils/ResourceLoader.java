package edu.gcet.placementhub.utils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;

/**
 * Loads institutional images (GCET logo & campus photograph) and paints crisp vector UI icons.
 */
public final class ResourceLoader {

    private static BufferedImage cachedLogo;
    private static BufferedImage cachedCampus;

    private ResourceLoader() {
    }

    public static BufferedImage loadLogoImage() {
        if (cachedLogo != null) return cachedLogo;
        try (InputStream is = ResourceLoader.class.getResourceAsStream("/assets/gcet_logo.png")) {
            if (is != null) {
                cachedLogo = ImageIO.read(is);
            }
        } catch (Exception ignored) {
        }
        return cachedLogo;
    }

    public static BufferedImage loadCampusImage() {
        if (cachedCampus != null) return cachedCampus;
        try (InputStream is = ResourceLoader.class.getResourceAsStream("/assets/gcet_campus_clean.png")) {
            if (is != null) {
                cachedCampus = ImageIO.read(is);
            }
        } catch (Exception ignored) {
        }
        return cachedCampus;
    }

    /**
     * Renders the official GCET circular crest with white circle backing & border ring.
     */
    public static ImageIcon createCircularLogoIcon(int size, Color ringColor) {
        BufferedImage logo = loadLogoImage();
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        g2.setColor(Color.WHITE);
        g2.fill(new Ellipse2D.Double(1, 1, size - 2, size - 2));

        if (logo != null) {
            int pad = Math.max(3, size / 10);
            Shape oldClip = g2.getClip();
            g2.setClip(new Ellipse2D.Double(pad, pad, size - pad * 2, size - pad * 2));
            g2.drawImage(logo, pad, pad, size - pad * 2, size - pad * 2, null);
            g2.setClip(oldClip);
        } else {
            g2.setColor(new Color(0x10, 0x1A, 0x35));
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, size / 3)));
            FontMetrics fm = g2.getFontMetrics();
            String txt = "GCET";
            g2.drawString(txt, (size - fm.stringWidth(txt)) / 2, (size + fm.getAscent()) / 2 - 2);
        }

        if (ringColor != null) {
            g2.setColor(ringColor);
            g2.setStroke(new BasicStroke(1.6f));
            g2.draw(new Ellipse2D.Double(1, 1, size - 2.5, size - 2.5));
        }

        g2.dispose();
        return new ImageIcon(out);
    }

    public static void openExternalUrl(Component parent, String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ignored) {
        }
        JOptionPane.showMessageDialog(
                parent,
                "Open URL in browser:\n" + url,
                "Institutional Portal Link",
                JOptionPane.INFORMATION_MESSAGE
        );
    }
}
