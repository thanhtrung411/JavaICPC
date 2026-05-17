package UI;

import Service.DbService;

import java.util.List;

final class HistoryFormatter {
    private HistoryFormatter() {
    }

    static String formatSavedTestcases(List<DbService.SavedTestcase> testcases) {
        if (testcases == null || testcases.isEmpty()) {
            return "Chưa lưu testcase.";
        }

        StringBuilder sb = new StringBuilder();
        for (DbService.SavedTestcase testcase : testcases) {
            sb.append("--- ").append(defaultIfBlank(testcase.name(), "Testcase")).append(" ---\n");
            sb.append("[Input]\n").append(defaultIfBlank(testcase.input(), "")).append("\n");
            sb.append("[Output]\n").append(defaultIfBlank(testcase.output(), "")).append("\n\n");
        }
        return sb.toString();
    }

    static String formatSavedSampleCodes(List<DbService.SavedSampleCode> sampleCodes) {
        if (sampleCodes == null || sampleCodes.isEmpty()) {
            return "Chưa lưu code mẫu.";
        }

        StringBuilder sb = new StringBuilder();
        for (DbService.SavedSampleCode sampleCode : sampleCodes) {
            sb.append("### ").append(sampleCode.verdictType()).append(" - ")
                    .append(defaultIfBlank(sampleCode.language(), "-")).append(" - ")
                    .append(defaultIfBlank(sampleCode.codeSource(), "-")).append("\n");
            sb.append(sampleCode.codeText()).append("\n\n");
        }
        return sb.toString();
    }

    static String formatSavedTestcaseArtifact(DbService.SavedTestcaseArtifact artifact) {
        if (artifact == null || artifact.rawResponse() == null || artifact.rawResponse().isBlank()) {
            return "Chưa lưu generator/checker.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("LOẠI CHECKER: ").append(defaultIfBlank(artifact.checkerType(), "UNKNOWN")).append("\n\n");

        if (artifact.generatorCode() != null && !artifact.generatorCode().isBlank()) {
            sb.append("MÃ NGUỒN GENERATOR\n");
            sb.append("========================================\n");
            sb.append(artifact.generatorCode()).append("\n\n");
        }

        if (artifact.checkerCode() != null && !artifact.checkerCode().isBlank()) {
            sb.append("MÃ NGUỒN CHECKER\n");
            sb.append("========================================\n");
            sb.append(artifact.checkerCode()).append("\n\n");
        } else if ("DIFF".equalsIgnoreCase(artifact.checkerType())) {
            sb.append("CHECKER\n");
            sb.append("========================================\n");
            sb.append("Bài toán có đáp án duy nhất, có thể dùng Diff Checker chuẩn.\n\n");
        }

        sb.append("PHẢN HỒI TESTCASE GỐC\n");
        sb.append("========================================\n");
        sb.append(artifact.rawResponse());
        return sb.toString();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
