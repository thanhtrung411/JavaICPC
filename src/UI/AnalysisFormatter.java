package UI;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AnalysisFormatter {
    private AnalysisFormatter() {
    }

    static String renderMarkdownToHtml(String md) {
        return renderMarkdownToHtml(md, -1);
    }

    static String renderMarkdownToHtml(String md, int configuredMaxTokens) {
        return renderMarkdownToHtml(md, configuredMaxTokens, null);
    }

    static String renderMarkdownToHtml(String md, int configuredMaxTokens, String rawGoogleStats) {
        return renderMarkdownToHtml(md, configuredMaxTokens, rawGoogleStats, -1);
    }

    static String renderMarkdownToHtml(String md, int configuredMaxTokens, String rawGoogleStats, long apiDurationMs) {
        if (md == null) return "";
        StringBuilder html = new StringBuilder("<html><body style='font-family: \"Segoe UI\", Tahoma, Geneva, Verdana, sans-serif; font-size: 14px; color: #000; line-height: 1.5; margin: 10px;'>");

        String[] parts = md.split("```");
        for (int i = 0; i < parts.length; i++) {
            if (i % 2 == 1) {
                String codeBlock = parts[i];
                int firstNewline = codeBlock.indexOf('\n');
                if (firstNewline != -1 && firstNewline < 10 && codeBlock.substring(0, firstNewline).trim().matches("[a-zA-Z\\+]+")) {
                    codeBlock = codeBlock.substring(firstNewline + 1);
                }
                html.append("<pre style='background:#f4f4f4; padding:10px; border-radius:5px; border:1px solid #ddd; font-family: Consolas, monospace; font-size: 13px; white-space: pre-wrap;'>")
                        .append(escapeHtml(codeBlock))
                        .append("</pre>");
            } else {
                String text = escapeHtml(parts[i]);
                text = text.replaceAll("### (.*?)\n", "<h3 style='border-bottom: 1px solid #ccc; padding-bottom: 5px; color: #2c3e50; margin-top: 20px;'>$1</h3>\n");
                text = text.replaceAll("--- (Testcase.*?) ---", "<h4 style='color: #2980b9; margin-top: 15px;'>--- $1 ---</h4>");
                text = text.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
                text = text.replaceAll("`(.*?)`", "<code style='background:#f0f0f0; padding:2px 4px; border-radius:3px; color: #d35400;'>$1</code>");
                text = text.replace("\n", "<br/>");
                html.append(text);
            }
        }

        if (configuredMaxTokens > 0) {
            appendTokenStats(html, configuredMaxTokens, rawGoogleStats, apiDurationMs);
        }

        html.append("</body></html>");
        return html.toString();
    }

    static String stripMarkdownFence(String text) {
        if (text == null) {
            return "";
        }

        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring("```json".length()).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring("```".length()).trim();
        }

        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }

        return trimmed;
    }

    static String formatAnalysisResult(String json, int configuredMaxTokens) {
        return formatAnalysisResult(json, configuredMaxTokens, null);
    }

    static String formatAnalysisResult(String json, int configuredMaxTokens, String rawGoogleStats) {
        return formatAnalysisResult(json, configuredMaxTokens, rawGoogleStats, -1);
    }

    static String formatAnalysisResult(String json, int configuredMaxTokens, String rawGoogleStats, long apiDurationMs) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("<html><body style='font-family: \"Segoe UI\", Tahoma, Geneva, Verdana, sans-serif; font-size: 14px; color: #000; line-height: 1.5; margin: 10px;'>");

            String title = extractStringSafe(json, "title");
            String type = extractStringSafe(json, "problem_type");

            sb.append("<h2 style='border-bottom: 1px solid #ccc;'>").append(formatMarkdownToHtml(title)).append("</h2>");
            sb.append("<p><b>Dạng bài:</b> ").append(formatMarkdownToHtml(type)).append("</p>");
            sb.append("<h3>1. TÓM TẮT ĐỀ BÀI (TL;DR)</h3>");
            sb.append("<p>").append(formatMarkdownToHtml(extractStringSafe(json, "problem_summary"))).append("</p>");
            sb.append("<h3>2. PHÂN TÍCH GIỚI HẠN (Constraints)</h3>");
            sb.append("<p>").append(formatMarkdownToHtml(extractStringSafe(json, "constraints_analysis"))).append("</p>");
            appendListHtml(sb, "3. QUAN SÁT & TÍNH CHẤT (Observations)", extractArraySafe(json, "observations"));
            sb.append("<h3>4. HƯỚNG GIẢI & THUẬT TOÁN</h3>");
            sb.append("<p>").append(formatMarkdownToHtml(extractStringSafe(json, "solution_approach"))).append("</p>");
            appendListHtml(sb, "5. GÓC LÁCH & TRƯỜNG HỢP BIÊN (Edge Cases)", extractArraySafe(json, "edge_cases"));
            sb.append("<h3>6. ĐỘ PHỨC TẠP (Complexity)</h3>");
            sb.append("<p>").append(getComplexityHtml(json)).append("</p>");
            sb.append("<h3>7. CHI TIẾT CÀI ĐẶT (Implementation Details)</h3>");
            sb.append("<p>").append(formatMarkdownToHtml(extractStringSafe(json, "implementation_details"))).append("</p>");

            appendTokenStats(sb, configuredMaxTokens, rawGoogleStats, apiDurationMs);

            sb.append("</body></html>");
            return sb.toString();
        } catch (Exception e) {
            return "<html><body><h3 style='color:red;'>Lỗi format JSON: " + escapeHtml(e.getMessage()) + "</h3><pre>" + escapeHtml(json) + "</pre></body></html>";
        }
    }

    static String extractStringSafe(String json, String fieldName) {
        if (json == null) {
            return "[Không tìm thấy trường: " + fieldName + "]";
        }

        String key = "\"" + fieldName + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return "[Không tìm thấy trường: " + fieldName + "]";
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return "[Lỗi format dấu hai chấm: " + fieldName + "]";
        int startQuote = json.indexOf('"', colon);
        if (startQuote < 0) return "[Lỗi format dấu ngoặc kép: " + fieldName + "]";
        StringBuilder sb = new StringBuilder();
        boolean esc = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                if (c == 'n') sb.append('\n');
                else if (c == 't') sb.append('\t');
                else sb.append(c);
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        String val = sb.toString().trim();
        if (val.isEmpty()) return "[Trống]";
        return val;
    }

    private static void appendTokenStats(StringBuilder sb, int configuredMaxTokens) {
        appendTokenStats(sb, configuredMaxTokens, null);
    }

    private static void appendTokenStats(StringBuilder sb, int configuredMaxTokens, String rawGoogleStats) {
        appendTokenStats(sb, configuredMaxTokens, rawGoogleStats, -1);
    }

    private static void appendTokenStats(StringBuilder sb, int configuredMaxTokens, String rawGoogleStats, long apiDurationMs) {
        try {
            String rawVertex = rawGoogleStats == null || rawGoogleStats.isBlank()
                    ? Files.readString(Path.of("data", "vertex_raw_response.json"))
                    : rawGoogleStats;
            int promptTokens = Integer.parseInt(extractNumberFromRawJson(rawVertex, "promptTokenCount"));
            int thoughtsTokens = Integer.parseInt(extractNumberFromRawJson(rawVertex, "thoughtsTokenCount"));
            int candidatesTokens = Integer.parseInt(extractNumberFromRawJson(rawVertex, "candidatesTokenCount"));
            int totalTokens = Integer.parseInt(extractNumberFromRawJson(rawVertex, "totalTokenCount"));
            String finishReason = extractStringFromRawJson(rawVertex, "finishReason");
            int usedOutputTokens = thoughtsTokens + candidatesTokens;
            int remainingOutputTokens = configuredMaxTokens - usedOutputTokens;

            sb.append("<hr style='margin-top: 25px;'>");
            sb.append("<h4>THỐNG KÊ TOKENS TỪ GOOGLE</h4>");
            sb.append("<ul style='font-size: 13px;'>");
            sb.append("<li><b>Input Tokens (Đề bài + Prompt):</b> ").append(promptTokens).append("</li>");
            sb.append("<li><b>Output Tokens đã tiêu thụ:</b> ").append(usedOutputTokens)
                    .append(" (Suy nghĩ: ").append(thoughtsTokens).append(", In chữ: ").append(candidatesTokens).append(")</li>");
            sb.append("<li><b>Giới hạn Max Output Tokens hiện tại:</b> ").append(configuredMaxTokens).append("</li>");
            sb.append("<li><b>Số Tokens Output còn lại:</b> ").append(remainingOutputTokens).append("</li>");
            sb.append("<li><b>Tổng toàn bộ Tokens (Input + Output):</b> ").append(totalTokens).append("</li>");
            if (apiDurationMs >= 0) {
                sb.append("<li><b>Thời gian gọi API:</b> ").append(formatDuration(apiDurationMs)).append("</li>");
            }
            sb.append("<li><b>Lý do hoàn thành (Finish Reason):</b> ").append(escapeHtml(finishReason)).append("</li>");
            sb.append("</ul>");

            if ("MAX_TOKENS".equals(finishReason)) {
                sb.append("<p style='color: #c0392b; font-weight: bold;'>CẢNH BÁO: Quá trình xử lý bị cắt ngang vì AI đã hết Output Tokens. Hãy qua tab Cấu hình tăng AI Max Tokens.</p>");
            }
        } catch (Exception ignored) {
        }
    }

    private static String formatDuration(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + " ms";
        }
        return String.format("%.2f giây (%d ms)", durationMs / 1000.0, durationMs);
    }

    private static String formatMarkdownToHtml(String text) {
        if (text == null) return "";
        String html = escapeHtml(text).replace("\n", "<br>");
        html = html.replaceAll("(?s)\\*\\*(.*?)\\*\\*", "<b>$1</b>");
        html = html.replaceAll("(?s)`(.*?)`", "<code style='background-color: #f0f0f0; padding: 2px 4px; border-radius: 3px; font-family: monospace;'>$1</code>");
        return html;
    }

    private static void appendListHtml(StringBuilder sb, String title, List<String> list) {
        sb.append("<h3>").append(title).append("</h3>");
        if (list == null || list.isEmpty() || list.get(0).startsWith("[Không") || list.get(0).startsWith("[Khong")) {
            sb.append("<p><i>- Không có thông tin -</i></p>");
            return;
        }
        sb.append("<ul>");
        for (String item : list) {
            sb.append("<li>").append(formatMarkdownToHtml(item)).append("</li>");
        }
        sb.append("</ul>");
    }

    private static String getComplexityHtml(String json) {
        String time = extractStringSafe(json, "time");
        String space = extractStringSafe(json, "space");
        if (!time.startsWith("[Không") && !time.startsWith("[Khong") && !space.startsWith("[Không") && !space.startsWith("[Khong")) {
            return "<b>Thời gian:</b> " + formatMarkdownToHtml(time) + "<br><b>Không gian:</b> " + formatMarkdownToHtml(space);
        }
        String comp = extractStringSafe(json, "complexity");
        if (comp.startsWith("[Không") || comp.startsWith("[Khong")) return comp;
        return formatMarkdownToHtml(comp);
    }

    private static List<String> extractArraySafe(String json, String fieldName) {
        List<String> list = new ArrayList<>();
        if (json == null) {
            list.add("[Không tìm thấy mảng: " + fieldName + "]");
            return list;
        }

        String key = "\"" + fieldName + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) {
            list.add("[Không tìm thấy mảng: " + fieldName + "]");
            return list;
        }
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return list;
        int startBracket = json.indexOf('[', colon);
        if (startBracket < 0) return list;

        boolean inString = false;
        boolean esc = false;
        StringBuilder currentString = null;

        for (int i = startBracket + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (!inString) {
                if (c == ']') break;
                else if (c == '"') {
                    inString = true;
                    currentString = new StringBuilder();
                }
            } else {
                if (esc) {
                    if (c == 'n') currentString.append('\n');
                    else if (c == 't') currentString.append('\t');
                    else currentString.append(c);
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inString = false;
                    list.add(currentString.toString());
                } else {
                    currentString.append(c);
                }
            }
        }
        return list;
    }

    private static String extractNumberFromRawJson(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "0";
    }

    private static String extractStringFromRawJson(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Không rõ";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
