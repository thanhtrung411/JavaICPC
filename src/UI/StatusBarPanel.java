package UI;

import javax.swing.*;
import java.awt.*;

final class StatusBarPanel extends JPanel {
    private final JLabel dbStatusIcon;
    private final JLabel dbStatusText;
    private final JLabel aiStatusIcon;
    private final JLabel aiStatusText;

    StatusBarPanel() {
        super(new FlowLayout(FlowLayout.LEFT, 16, 6));
        setBackground(new Color(240, 240, 240));
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));

        dbStatusIcon = createStatusIconLabel(false);
        dbStatusText = createStatusTextLabel("DB: Chưa kết nối", Color.RED);
        aiStatusIcon = createStatusIconLabel(false);
        aiStatusText = createStatusTextLabel("AI: Chưa kết nối", Color.RED);

        add(dbStatusIcon);
        add(dbStatusText);
        add(Box.createHorizontalStrut(20));
        add(aiStatusIcon);
        add(aiStatusText);
    }

    void setDbStatus(String message, boolean connected) {
        setStatus(dbStatusIcon, dbStatusText, message, connected);
    }

    void setAiStatus(String message, boolean connected) {
        setStatus(aiStatusIcon, aiStatusText, message, connected);
    }

    private void setStatus(JLabel icon, JLabel text, String message, boolean connected) {
        Color color = connected ? new Color(0, 160, 0) : Color.RED;
        icon.setForeground(color);
        text.setForeground(color);
        text.setText(message);
    }

    private JLabel createStatusIconLabel(boolean connected) {
        JLabel label = new JLabel("\u25cf");
        label.setFont(new Font("Segoe UI", Font.BOLD, 16));
        label.setForeground(connected ? new Color(0, 160, 0) : Color.RED);
        return label;
    }

    private JLabel createStatusTextLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        return label;
    }
}
