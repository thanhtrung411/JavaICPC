package UI;

import javax.swing.*;
import java.awt.*;

final class ManualSampleCodeDialog {
    private ManualSampleCodeDialog() {
    }

    static Result show(Component parent, String language) {
        JTextArea acArea = createCodeInputArea();
        JTextArea tleArea = createCodeInputArea();
        JTextArea waArea = createCodeInputArea();

        JTabbedPane inputTabs = new JTabbedPane();
        inputTabs.addTab("AC", new JScrollPane(acArea));
        inputTabs.addTab("TLE", new JScrollPane(tleArea));
        inputTabs.addTab("WA", new JScrollPane(waArea));
        inputTabs.setPreferredSize(new Dimension(820, 520));

        int result = JOptionPane.showConfirmDialog(
                parent,
                inputTabs,
                "Nhập code mẫu thủ công (" + language + ")",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String acCode = acArea.getText().trim();
        String tleCode = tleArea.getText().trim();
        String waCode = waArea.getText().trim();
        if (acCode.isBlank() && tleCode.isBlank() && waCode.isBlank()) {
            JOptionPane.showMessageDialog(parent, "Bạn cần nhập ít nhất một phiên bản code AC, TLE hoặc WA.", "Thiếu code mẫu", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        return new Result(language, buildRaw(language, acCode, tleCode, waCode));
    }

    private static JTextArea createCodeInputArea() {
        JTextArea area = new JTextArea();
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setTabSize(4);
        area.setLineWrap(false);
        return area;
    }

    private static String buildRaw(String language, String acCode, String tleCode, String waCode) {
        String codeFenceLanguage = "C++".equalsIgnoreCase(language) ? "cpp" : language.toLowerCase();
        StringBuilder sb = new StringBuilder();
        appendCodeSection(sb, "1. CODE AC (Nhập thủ công)", codeFenceLanguage, acCode);
        appendCodeSection(sb, "2. CODE TLE (Nhập thủ công)", codeFenceLanguage, tleCode);
        appendCodeSection(sb, "3. CODE WA (Nhập thủ công)", codeFenceLanguage, waCode);
        return sb.toString().trim();
    }

    private static void appendCodeSection(StringBuilder sb, String title, String codeFenceLanguage, String code) {
        if (code == null || code.isBlank()) {
            return;
        }

        sb.append("### ").append(title).append("\n");
        sb.append("```").append(codeFenceLanguage).append("\n");
        sb.append(code.strip()).append("\n");
        sb.append("```\n\n");
    }

    record Result(String language, String rawCode) {
    }
}
