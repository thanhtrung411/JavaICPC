package UI;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.text.Normalizer;

final class StatementTextFilter {
    private StatementTextFilter() {
    }

    static void install(JTextArea textArea) {
        ((AbstractDocument) textArea.getDocument()).setDocumentFilter(new DocumentFilter() {
            @Override
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                super.insertString(fb, offset, normalize(string), attr);
            }

            @Override
            public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                super.replace(fb, offset, length, normalize(text), attrs);
            }
        });
    }

    static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace('\u00A0', ' ')
                .replace('\u2007', ' ')
                .replace('\u202F', ' ')
                .replace('\u2212', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2264', '<')
                .replace('\u2265', '>')
                .replace('\u00D7', 'x')
                .replace('\u2217', '*')
                .replace('\u00F7', '/')
                .replace('\u2215', '/')
                .replace('\u2044', '/')
                .replace('\u221E', 'o')
                .replace('\u2208', 'e')
                .replace('\u2209', 'e')
                .replace('\u2200', 'A')
                .replace('\u2203', 'E')
                .replace('\u03A3', 'S')
                .replace("\u03C0", "pi");
    }
}
