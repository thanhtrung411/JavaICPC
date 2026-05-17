package Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

public class AIService {
    public static final String AUTH_API_KEY = "API Key";
    public static final String AUTH_ACCESS_TOKEN = "Access Token";
    public static final String AUTH_SERVICE_ACCOUNT_JSON = "Service Account JSON";
    public static final String DEFAULT_LOCATION = "global";
    public static final String DEFAULT_MODEL = "gemini-2.5-flash";

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final String projectId;
    private final String location;
    private final String model;
    private final String authType;
    private final String credential;
    private final int maxTokens;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private String lastRawResponse = "";
    private long lastRequestDurationMs = -1;

    public static class AITestResult {
        private final boolean success;
        private final String message;

        public AITestResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    public AIService(
            String projectId,
            String location,
            String model,
            String authType,
            String credential,
            int maxTokens,
            int timeoutSeconds
    ) {
        this.projectId = projectId;
        this.location = location;
        this.model = model;
        this.authType = authType;
        this.credential = credential;
        this.maxTokens = maxTokens;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    public AITestResult testConnection() {
        try {
            String responseText = generateText("Reply only: OK", 64);
            if (responseText == null || responseText.isBlank()) {
                return new AITestResult(false, "AI không trả về nội dung.");
            }
            return new AITestResult(true, "Kết nối Google Vertex AI thành công");
        } catch (IllegalArgumentException e) {
            return new AITestResult(false, e.getMessage());
        } catch (IOException e) {
            return new AITestResult(false, "Lỗi gọi Vertex AI: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AITestResult(false, "Đã hủy khi đang gọi Vertex AI.");
        }
    }

    public String analyzeProblem(String statementText) throws IOException, InterruptedException {
        String prompt = """
                Bạn là chuyên gia phân tích đề thi lập trình ICPC/IOI.
                Hãy phân tích đề bài sau và xuất ra JSON hợp lệ (không chứa markdown fence ```json), tuân thủ chính xác dàn ý 7 phần chuyên nghiệp của ICPC.
                
                Quy tắc bắt buộc:
                - Toàn bộ giá trị trong JSON phải viết bằng tiếng Việt.
                - Không dùng tiếng Anh trong nội dung phân tích, trừ tên thuật ngữ lập trình phổ biến.
                - Trình bày ngắn gọn, xúc tích, bám sát các thuật ngữ Khoa học máy tính.
                - Không bịa thêm dữ kiện nếu đề không có, nhưng được quyền phân tích độ phức tạp và hướng tiếp cận từ giới hạn dữ liệu.
                
                JSON cần có đúng các trường sau:
                {
                  "problem_code": "Mã bài (nếu có, VD: 123A. Nếu không có, tự tạo một mã 3-6 ký tự viết hoa, VD: VCS)",
                  "title": "Tên bài toán (nếu tìm thấy hoặc tự đặt tóm tắt)",
                  "problem_type": "Dạng bài toán (ví dụ: Quy hoạch động, Đồ thị, Cấu trúc dữ liệu...)",
                  "problem_summary": "Tóm tắt đề bài: Loại bỏ cốt truyện, phát biểu lại dưới dạng toán học thuần túy.",
                  "constraints_analysis": "Phân tích giới hạn: Từ N, M, Q... suy luận ra độ phức tạp yêu cầu (vd: O(N log N)) và kiểu dữ liệu cần thiết.",
                  "observations": [
                    "Nhận xét 1: Tính chất cốt lõi...",
                    "Nhận xét 2: ..."
                  ],
                  "solution_approach": "Thuật toán / Hướng giải: Giải thích cách làm và cấu trúc dữ liệu một cách chi tiết.",
                  "edge_cases": [
                    "Trường hợp góc 1: N = 0...",
                    "Trường hợp góc 2: Tràn số..."
                  ],
                  "complexity": "Độ phức tạp: Phân tích cụ thể thời gian O(...) và không gian O(...).",
                  "implementation_details": "Chi tiết cài đặt: Lưu ý khi code, Fast I/O, cách khởi tạo mảng, xử lý số lớn..."
                }

                Đề bài:
                """ + statementText;

        return generateText(prompt, maxTokens);
    }

    public String generateTestcasesAndChecker(String statementText, String analysisJson) throws IOException, InterruptedException {
        String prompt = """
                Nhiệm vụ: tạo testcase ngắn gọn cho hệ thống chấm ICPC/IOI.

                QUY TẮC BẮT BUỘC:
                - Không viết lời chào, lời mở đầu, nhận xét, kết luận, hoặc câu dẫn.
                - Không giải thích từng testcase.
                - Không viết "cách sử dụng", lệnh biên dịch, ví dụ chạy, hoặc ghi chú ngoài format.
                - Không thêm markdown ngoài các heading và code fence được yêu cầu.
                - Chỉ xuất đúng 3 phần dưới đây, đúng thứ tự, đúng format.

                Phần 1: TESTCASE THỦ CÔNG
                - Tạo 4-6 testcase thật sự cần thiết, ưu tiên: sample, min/max, biên, bẫy sai logic, tràn số.
                - Mỗi testcase chỉ có Input và Output, không có giải thích.
                - Format bắt buộc:
                  --- Testcase X ---
                  [Input]
                  ...
                  [Output]
                  ...

                Phần 2: MÃ NGUỒN GENERATOR (C++)
                - Chỉ viết một code block C++ duy nhất.
                - Generator phải dùng thư viện chuẩn C++ (<random>), không phụ thuộc testlib.h.
                - Code không cần comment dài; chỉ giữ comment nếu thật sự cần để hiểu tham số.

                Phần 3: MÃ NGUỒN CHECKER (C++)
                - Nếu bài có đáp án duy nhất, chỉ in đúng một dòng:
                  Bài toán có đáp án duy nhất, có thể dùng Diff Checker chuẩn.
                - Nếu bài có nhiều đáp án, chỉ viết một code block C++ checker, không giải thích thêm.

                Dữ liệu đầu vào để phân tích:
                """ + statementText + "\n\nPhân tích bài toán:\n" + analysisJson;

        return cleanGeneratedTestcaseResponse(generateText(prompt, maxTokens));
    }

    private String cleanGeneratedTestcaseResponse(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String cleaned = text.trim();
        int sectionStart = firstExistingIndex(cleaned, "Phần 1:", "Phan 1:", "--- Testcase");
        if (sectionStart > 0) {
            cleaned = cleaned.substring(sectionStart).trim();
        }

        String diffCheckerLine = "Bài toán có đáp án duy nhất, có thể dùng Diff Checker chuẩn.";
        int diffCheckerIndex = cleaned.indexOf(diffCheckerLine);
        if (diffCheckerIndex >= 0) {
            cleaned = cleaned.substring(0, diffCheckerIndex + diffCheckerLine.length()).trim();
        }

        return cleaned;
    }

    private int firstExistingIndex(String text, String... markers) {
        int bestIndex = -1;
        for (String marker : markers) {
            int index = text.indexOf(marker);
            if (index >= 0 && (bestIndex < 0 || index < bestIndex)) {
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    public String generateSampleCode(String statementText, String analysisJson, String language) throws IOException, InterruptedException {
        String prompt = """
                Bạn là thí sinh thi ICPC. Dựa vào đề bài và phân tích sau, hãy viết MÃ NGUỒN MẪU (Sample Code) bằng ngôn ngữ %s.
                
                Hãy viết 3 phiên bản mã nguồn khác nhau:
                1. CODE AC (Accepted): Mã nguồn tối ưu, giải đúng 100%% testcase.
                2. CODE TLE (Time Limit Exceeded): Mã nguồn dùng thuật toán ngây thơ (trâu bò - Brute Force), độ phức tạp cao, chạy quá thời gian với dữ liệu lớn nhưng đúng logic. (Nếu không thể code trâu, hãy giải thích ngắn gọn).
                3. CODE WA (Wrong Answer): Mã nguồn có thuật toán nhìn có vẻ đúng nhưng bị sai logic ở trường hợp biên, hoặc bỏ sót trường hợp bẫy, hoặc tràn kiểu dữ liệu.
                
                Format trả về (bọc code trong markdown ```%s ... ```):
                ### 1. CODE AC (Chuẩn xác)
                (Code)
                
                ### 2. CODE TLE (Trâu bò)
                (Code)
                
                ### 3. CODE WA (Sai bẫy / Tràn số)
                (Code)

                Quy tắc bắt buộc cho từng phiên bản code:
                - Mỗi phần AC/TLE/WA phải có đúng một code block.
                - Chỉ sinh code mẫu AC/TLE/WA, không sinh lại testcase, generator, checker hoặc phân tích đề.
                - Không viết thêm phần "Phần 1", "Phần 2", "Phần 3", "Testcase", "Generator" hoặc "Checker".
                - Không dùng lại format sinh testcase.
                - Code phải là một file nguồn hoàn chỉnh, tự chạy được, có hàm main/entry point hợp lệ.
                - Không thay code bằng mô tả, pseudo-code, đoạn hàm rời, hoặc lời giải thích.
                - Không dùng thư viện ngoài hệ thống chấm chuẩn.
                
                Đề bài:
                %s
                
                Phân tích bài toán:
                %s
                """.formatted(language, language.toLowerCase(), statementText, analysisJson);

        return generateText(prompt, maxTokens);
    }

    public String generateText(String prompt, int outputTokens) throws IOException, InterruptedException {
        return sendPostRequest(buildRequestBody(prompt, outputTokens));
    }

    public String getLastRawResponse() {
        return lastRawResponse;
    }

    public long getLastRequestDurationMs() {
        return lastRequestDurationMs;
    }

    public String rebuildStatementFromFiles(java.util.List<java.io.File> files) throws IOException, InterruptedException {
        String prompt = "Dưới đây là hình ảnh/PDF của MỘT đề bài lập trình thi đấu (ICPC/IOI). Hãy nhận dạng toàn bộ văn bản và trả về nội dung đề bài thuần túy. Giữ nguyên format toán học nếu có (dùng LaTeX $...$). KHÔNG giải thích, chỉ in ra đề bài.\n"
                      + "LƯU Ý QUAN TRỌNG: Nếu bạn phát hiện trong các hình ảnh/PDF này chứa NHIỀU HƠN 1 ĐỀ BÀI khác nhau, hãy trả về CHÍNH XÁC chuỗi: [LỖI_NHIỀU_ĐỀ] và KHÔNG xuất ra bất kỳ nội dung nào khác.";
        return sendPostRequest(buildMultimodalRequestBody(prompt, files, maxTokens));
    }

    private String sendPostRequest(String requestBody) throws IOException, InterruptedException {
        validateConfig();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(buildGenerateContentUrl()))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json");

        if (AUTH_ACCESS_TOKEN.equals(authType)) {
            requestBuilder.header("Authorization", "Bearer " + credential);
        } else if (AUTH_SERVICE_ACCOUNT_JSON.equals(authType)) {
            requestBuilder.header("Authorization", "Bearer " + createAccessTokenFromServiceAccountJson());
        } else {
            requestBuilder.header("x-goog-api-key", credential);
        }

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        lastRequestDurationMs = System.currentTimeMillis() - startTime;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        lastRawResponse = response.body();
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("data", "vertex_raw_response.json"), response.body());
        } catch (Exception e) {}

        return extractAllTextParts(response.body());
    }

    private String extractAllTextParts(String json) throws IOException {
        StringBuilder fullText = new StringBuilder();
        String key = "\"text\"";
        int searchIndex = 0;
        boolean foundAny = false;
        
        while (true) {
            int keyIndex = json.indexOf(key, searchIndex);
            if (keyIndex < 0) break;
            
            int colonIndex = json.indexOf(':', keyIndex + key.length());
            if (colonIndex < 0) {
                searchIndex = keyIndex + key.length();
                continue;
            }
            
            int quoteIndex = json.indexOf('"', colonIndex + 1);
            if (quoteIndex < 0) {
                searchIndex = colonIndex + 1;
                continue;
            }
            
            // Check if there are only spaces between colon and quote
            boolean valid = true;
            for (int i = colonIndex + 1; i < quoteIndex; i++) {
                if (!Character.isWhitespace(json.charAt(i))) {
                    valid = false;
                    break;
                }
            }
            if (!valid) {
                searchIndex = quoteIndex + 1;
                continue;
            }
            
            StringBuilder result = new StringBuilder();
            boolean escaped = false;
            int endIndex = -1;
            for (int i = quoteIndex + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escaped) {
                    if (c == 'u' && i + 4 < json.length()) {
                        String hex = json.substring(i + 1, i + 5);
                        try {
                            result.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                        i += 4;
                    } else {
                        result.append(unescapeJsonChar(c));
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    endIndex = i;
                    break;
                } else {
                    result.append(c);
                }
            }
            
            if (endIndex > 0) {
                fullText.append(result.toString());
                foundAny = true;
                searchIndex = endIndex + 1;
            } else {
                break;
            }
        }
        
        if (!foundAny) {
            throw new IOException("Không tìm thấy field JSON: text");
        }
        return fullText.toString();
    }

    private String createAccessTokenFromServiceAccountJson() throws IOException, InterruptedException {
        String clientEmail = extractJsonString(credential, "client_email");
        String privateKeyPem = extractJsonString(credential, "private_key");
        String tokenUri = extractOptionalJsonString(credential, "token_uri", DEFAULT_TOKEN_URI);
        String assertion = createSignedJwt(clientEmail, privateKeyPem, tokenUri);

        String formBody = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(assertion, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUri))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Không lấy được access token từ service account. HTTP "
                    + response.statusCode() + ": " + response.body());
        }

        return extractJsonString(response.body(), "access_token");
    }

    private String createSignedJwt(String clientEmail, String privateKeyPem, String tokenUri) throws IOException {
        try {
            long now = Instant.now().getEpochSecond();
            String headerJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
            String claimJson = """
                    {
                      "iss": "%s",
                      "scope": "%s",
                      "aud": "%s",
                      "iat": %d,
                      "exp": %d
                    }
                    """.formatted(escapeJson(clientEmail), CLOUD_PLATFORM_SCOPE, escapeJson(tokenUri), now, now + 3600);

            String signingInput = base64Url(headerJson.getBytes(StandardCharsets.UTF_8))
                    + "." + base64Url(claimJson.getBytes(StandardCharsets.UTF_8));

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(parsePrivateKey(privateKeyPem));
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));

            return signingInput + "." + base64Url(signature.sign());
        } catch (Exception e) {
            throw new IOException("Không ký được JWT từ service account JSON: " + e.getMessage(), e);
        }
    }

    private PrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        String privateKeyContent = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateConfig() {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("Chưa nhập Google Project ID.");
        }
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Chưa nhập Google Location.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Chưa nhập AI Model.");
        }
        if (credential == null || credential.isBlank()) {
            throw new IllegalArgumentException("Chưa nhập Google credential.");
        }
    }

    private String buildGenerateContentUrl() {
        return "https://aiplatform.googleapis.com/v1/projects/" + projectId
                + "/locations/" + location
                + "/publishers/google/models/" + model
                + ":generateContent";
    }

    private String buildRequestBody(String prompt, int outputTokens) {
        return """
                {
                  "contents": [
                    {
                      "role": "user",
                      "parts": [
                        { "text": "%s" }
                      ]
                    }
                  ],
                  "generationConfig": {
                    "maxOutputTokens": %d,
                    "temperature": 0.2
                  }
                }
                """.formatted(prompt.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", ""), maxTokens);
    }

    private String buildMultimodalRequestBody(String prompt, java.util.List<java.io.File> files, int maxTokens) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"contents\": [\n");
        sb.append("    {\n");
        sb.append("      \"role\": \"user\",\n");
        sb.append("      \"parts\": [\n");
        
        boolean firstPart = true;
        if (files != null && !files.isEmpty()) {
            for (java.io.File file : files) {
                String mimeType = getMimeType(file);
                if (mimeType == null) continue;
                
                byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
                String base64 = java.util.Base64.getEncoder().encodeToString(fileBytes);
                
                if (!firstPart) sb.append(",\n");
                sb.append("        {\n");
                sb.append("          \"inlineData\": {\n");
                sb.append("            \"mimeType\": \"").append(mimeType).append("\",\n");
                sb.append("            \"data\": \"").append(base64).append("\"\n");
                sb.append("          }\n");
                sb.append("        }");
                firstPart = false;
            }
        }
        
        if (!firstPart) sb.append(",\n");
        sb.append("        {\n");
        sb.append("          \"text\": \"").append(prompt.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")).append("\"\n");
        sb.append("        }\n");
        
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ],\n");
        sb.append("  \"generationConfig\": {\n");
        sb.append("    \"maxOutputTokens\": ").append(maxTokens).append(",\n");
        sb.append("    \"temperature\": 0.2\n");
        sb.append("  }\n");
        sb.append("}\n");
        return sb.toString();
    }
    
    private String getMimeType(java.io.File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".heic")) return "image/heic";
        return null;
    }

    public static String extractOptionalJsonString(String json, String fieldName, String defaultValue) {
        try {
            return extractJsonString(json, fieldName);
        } catch (IOException e) {
            return defaultValue;
        }
    }

    public static String extractJsonString(String json, String fieldName) throws IOException {
        String key = "\"" + fieldName + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            throw new IOException("Không tìm thấy field JSON: " + fieldName);
        }

        int colonIndex = json.indexOf(':', keyIndex + key.length());
        int quoteIndex = json.indexOf('"', colonIndex + 1);
        if (colonIndex < 0 || quoteIndex < 0) {
            throw new IOException("Field JSON không đúng định dạng: " + fieldName);
        }

        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = quoteIndex + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                if (c == 'u' && i + 4 < json.length()) {
                    String hex = json.substring(i + 1, i + 5);
                    result.append((char) Integer.parseInt(hex, 16));
                    i += 4;
                } else {
                    result.append(unescapeJsonChar(c));
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return result.toString();
            } else {
                result.append(c);
            }
        }

        throw new IOException("Không đọc hết được field JSON: " + fieldName);
    }

    private String escapeJson(String text) {
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static char unescapeJsonChar(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'b' -> '\b';
            case 'f' -> '\f';
            default -> c;
        };
    }
}
