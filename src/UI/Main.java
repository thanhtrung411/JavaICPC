package UI;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.util.Enumeration;

public class Main {
    public static void main(String[] args) {
        configureLookAndFeel();
        SwingUtilities.invokeLater(() -> new MainUI().setVisible(true));
    }

    private static void configureLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                 UnsupportedLookAndFeelException e) {
            System.err.println("Không thể dùng giao diện mặc định của hệ thống: " + e.getMessage());
        }

        FontUIResource appFont = new FontUIResource("Segoe UI", Font.PLAIN, 14);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                UIManager.put(key, appFont);
            }
        }
    }
}
