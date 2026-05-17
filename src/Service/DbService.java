package Service;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DbService {
    public static final String DEFAULT_DB_URL = "jdbc:sqlite:data/contest_judge_ai.db";

    private final String dbUrl;

    public static class ConnectionTestResult {
        private final boolean success;
        private final String message;

        public ConnectionTestResult(boolean success, String message) {
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

    public record SavedTestcase(
            String name,
            String input,
            String output,
            String testcaseType
    ) {
    }

    public record SavedSampleCode(
            String verdictType,
            String language,
            String codeText,
            String codeSource
    ) {
    }

    public record SavedTestcaseArtifact(
            String rawResponse,
            String generatorCode,
            String checkerCode,
            String checkerType
    ) {
    }

    public record SaveProblemRequest(
            String code,
            String name,
            String source,
            String statementText,
            String analysisJson,
            List<SavedTestcase> testcases,
            List<SavedSampleCode> sampleCodes,
            SavedTestcaseArtifact testcaseArtifact
    ) {
    }

    public record SaveProblemResult(
            long problemId,
            int analysisCount,
            int testcaseCount,
            int sampleCodeCount,
            int artifactCount
    ) {
    }

    public record SavedProblemSummary(
            long id,
            String code,
            String name,
            String source,
            String createdAt,
            int analysisCount,
            int testcaseCount,
            int sampleCodeCount
    ) {
        @Override
        public String toString() {
            String displayCode = code == null || code.isBlank() ? "-" : code;
            return "#" + id + " [" + displayCode + "] " + name;
        }
    }

    public record SavedProblemDetail(
            long id,
            String code,
            String name,
            String source,
            String statementText,
            String createdAt,
            String latestAnalysisJson,
            List<SavedTestcase> testcases,
            List<SavedSampleCode> sampleCodes,
            SavedTestcaseArtifact testcaseArtifact
    ) {
    }

    public DbService(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    public ConnectionTestResult testConnection() {
        try (Connection connection = getConnection()) {
            initializeDatabase(connection);
            return new ConnectionTestResult(true, "Kết nối SQLite thành công");
        } catch (SQLException e) {
            return new ConnectionTestResult(false, buildSqlErrorMessage(e));
        }
    }

    public Connection getConnection() throws SQLException {
        ensureDataDirectoryExists();
        loadSqliteDriver();
        return DriverManager.getConnection(dbUrl);
    }

    public SaveProblemResult saveProblem(SaveProblemRequest request) throws SQLException {
        try (Connection connection = getConnection()) {
            initializeDatabase(connection);
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                long problemId = insertProblem(connection, request);
                int analysisCount = insertAnalysisIfPresent(connection, problemId, request.analysisJson());
                int testcaseCount = insertTestcases(connection, problemId, request.testcases());
                int sampleCodeCount = insertSampleCodes(connection, problemId, request.sampleCodes());
                int artifactCount = insertTestcaseArtifactIfPresent(connection, problemId, request.testcaseArtifact());
                connection.commit();
                return new SaveProblemResult(problemId, analysisCount, testcaseCount, sampleCodeCount, artifactCount);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    public List<SavedProblemSummary> listSavedProblems() throws SQLException {
        try (Connection connection = getConnection()) {
            initializeDatabase(connection);
            String sql = """
                    SELECT p.id, p.code, p.name, p.source, p.created_at,
                           COUNT(DISTINCT a.id) AS analysis_count,
                           COUNT(DISTINCT t.id) AS testcase_count,
                           COUNT(DISTINCT s.id) AS sample_code_count
                    FROM problems p
                    LEFT JOIN ai_analyses a ON a.problem_id = p.id
                    LEFT JOIN testcases t ON t.problem_id = p.id
                    LEFT JOIN sample_codes s ON s.problem_id = p.id
                    GROUP BY p.id, p.code, p.name, p.source, p.created_at
                    ORDER BY p.id DESC
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                List<SavedProblemSummary> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new SavedProblemSummary(
                            rs.getLong("id"),
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("source"),
                            rs.getString("created_at"),
                            rs.getInt("analysis_count"),
                            rs.getInt("testcase_count"),
                            rs.getInt("sample_code_count")
                    ));
                }
                return list;
            }
        }
    }

    public SavedProblemDetail loadProblemDetail(long problemId) throws SQLException {
        try (Connection connection = getConnection()) {
            initializeDatabase(connection);
            String sql = """
                    SELECT id, code, name, source, statement_text, created_at
                    FROM problems
                    WHERE id = ?
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setLong(1, problemId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Không tìm thấy đề bài id=" + problemId);
                    }

                    return new SavedProblemDetail(
                            rs.getLong("id"),
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("source"),
                            rs.getString("statement_text"),
                            rs.getString("created_at"),
                            loadLatestAnalysis(connection, problemId),
                            loadTestcases(connection, problemId),
                            loadSampleCodes(connection, problemId),
                            loadLatestTestcaseArtifact(connection, problemId)
                    );
                }
            }
        }
    }

    private long insertProblem(Connection connection, SaveProblemRequest request) throws SQLException {
        String sql = """
                INSERT INTO problems(code, name, source, statement_text)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, request.code());
            ps.setString(2, request.name());
            ps.setString(3, request.source());
            ps.setString(4, request.statementText());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Không lấy được id đề bài sau khi lưu.");
    }

    private int insertAnalysisIfPresent(Connection connection, long problemId, String analysisJson) throws SQLException {
        if (analysisJson == null || analysisJson.isBlank()) {
            return 0;
        }

        String sql = "INSERT INTO ai_analyses(problem_id, analysis_json) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, problemId);
            ps.setString(2, analysisJson);
            return ps.executeUpdate();
        }
    }

    private int insertTestcases(Connection connection, long problemId, List<SavedTestcase> testcases) throws SQLException {
        if (testcases == null || testcases.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT INTO testcases(problem_id, name, input_text, output_text, testcase_type)
                VALUES (?, ?, ?, ?, ?)
                """;
        int count = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (SavedTestcase testcase : testcases) {
                ps.setLong(1, problemId);
                ps.setString(2, testcase.name());
                ps.setString(3, testcase.input());
                ps.setString(4, testcase.output());
                ps.setString(5, testcase.testcaseType());
                ps.addBatch();
            }
            for (int updateCount : ps.executeBatch()) {
                if (updateCount > 0) {
                    count += updateCount;
                }
            }
        }
        return count;
    }

    private int insertSampleCodes(Connection connection, long problemId, List<SavedSampleCode> sampleCodes) throws SQLException {
        if (sampleCodes == null || sampleCodes.isEmpty()) {
            return 0;
        }

        String sql = """
                INSERT INTO sample_codes(problem_id, verdict_type, language, code_text, code_source)
                VALUES (?, ?, ?, ?, ?)
                """;
        int count = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (SavedSampleCode sampleCode : sampleCodes) {
                ps.setLong(1, problemId);
                ps.setString(2, sampleCode.verdictType());
                ps.setString(3, sampleCode.language());
                ps.setString(4, sampleCode.codeText());
                ps.setString(5, sampleCode.codeSource());
                ps.addBatch();
            }
            for (int updateCount : ps.executeBatch()) {
                if (updateCount > 0) {
                    count += updateCount;
                }
            }
        }
        return count;
    }

    private int insertTestcaseArtifactIfPresent(
            Connection connection,
            long problemId,
            SavedTestcaseArtifact artifact
    ) throws SQLException {
        if (artifact == null || artifact.rawResponse() == null || artifact.rawResponse().isBlank()) {
            return 0;
        }

        String sql = """
                INSERT INTO testcase_artifacts(problem_id, raw_response, generator_code, checker_code, checker_type)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, problemId);
            ps.setString(2, artifact.rawResponse());
            ps.setString(3, artifact.generatorCode());
            ps.setString(4, artifact.checkerCode());
            ps.setString(5, artifact.checkerType());
            return ps.executeUpdate();
        }
    }

    private String loadLatestAnalysis(Connection connection, long problemId) throws SQLException {
        String sql = """
                SELECT analysis_json
                FROM ai_analyses
                WHERE problem_id = ?
                ORDER BY id DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, problemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("analysis_json") : "";
            }
        }
    }

    private List<SavedTestcase> loadTestcases(Connection connection, long problemId) throws SQLException {
        String sql = """
                SELECT name, input_text, output_text, testcase_type
                FROM testcases
                WHERE problem_id = ?
                ORDER BY id
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, problemId);
            try (ResultSet rs = ps.executeQuery()) {
                List<SavedTestcase> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new SavedTestcase(
                            rs.getString("name"),
                            rs.getString("input_text"),
                            rs.getString("output_text"),
                            rs.getString("testcase_type")
                    ));
                }
                return list;
            }
        }
    }

    private List<SavedSampleCode> loadSampleCodes(Connection connection, long problemId) throws SQLException {
        String sql = """
                SELECT verdict_type, language, code_text, code_source
                FROM sample_codes
                WHERE problem_id = ?
                ORDER BY id
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, problemId);
            try (ResultSet rs = ps.executeQuery()) {
                List<SavedSampleCode> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new SavedSampleCode(
                            rs.getString("verdict_type"),
                            rs.getString("language"),
                            rs.getString("code_text"),
                            rs.getString("code_source")
                    ));
                }
                return list;
            }
        }
    }

    private SavedTestcaseArtifact loadLatestTestcaseArtifact(Connection connection, long problemId) throws SQLException {
        String sql = """
                SELECT raw_response, generator_code, checker_code, checker_type
                FROM testcase_artifacts
                WHERE problem_id = ?
                ORDER BY id DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, problemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new SavedTestcaseArtifact(
                        rs.getString("raw_response"),
                        rs.getString("generator_code"),
                        rs.getString("checker_code"),
                        rs.getString("checker_type")
                );
            }
        }
    }

    private void loadSqliteDriver() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Không tìm thấy SQLite JDBC driver trong thư mục lib.", e);
        }
    }

    private void ensureDataDirectoryExists() {
        if (!dbUrl.startsWith("jdbc:sqlite:")) {
            return;
        }

        String dbPath = dbUrl.substring("jdbc:sqlite:".length());
        File dbFile = new File(dbPath);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    private void initializeDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS problems (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        code TEXT,
                        name TEXT NOT NULL,
                        source TEXT,
                        statement_text TEXT NOT NULL,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ai_analyses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        problem_id INTEGER NOT NULL,
                        analysis_json TEXT NOT NULL,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (problem_id) REFERENCES problems(id)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS testcases (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        problem_id INTEGER NOT NULL,
                        name TEXT,
                        input_text TEXT NOT NULL,
                        output_text TEXT,
                        testcase_type TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (problem_id) REFERENCES problems(id)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS sample_codes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        problem_id INTEGER NOT NULL,
                        verdict_type TEXT NOT NULL,
                        language TEXT,
                        code_text TEXT NOT NULL,
                        code_source TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (problem_id) REFERENCES problems(id)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS testcase_artifacts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        problem_id INTEGER NOT NULL,
                        raw_response TEXT,
                        generator_code TEXT,
                        checker_code TEXT,
                        checker_type TEXT,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (problem_id) REFERENCES problems(id)
                    )
                    """);
        }
    }

    private String buildSqlErrorMessage(SQLException e) {
        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            message = "Không lấy được thông tin lỗi từ SQLite.";
        }

        return message + " (SQLState: " + e.getSQLState() + ", ErrorCode: " + e.getErrorCode() + ")";
    }
}
