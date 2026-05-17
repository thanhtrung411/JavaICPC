package Service;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalJudgeService {

    public static class Testcase {
        public String name;
        public String input;
        public String expectedOutput;

        public Testcase(String name, String input, String expectedOutput) {
            this.name = name;
            this.input = input;
            this.expectedOutput = expectedOutput;
        }
    }

    public static class JudgeResult {
        public String testcaseName;
        public String status; // AC, WA, TLE, RE, CE
        public long timeMs;
        public String detail;

        public JudgeResult(String testcaseName, String status, long timeMs, String detail) {
            this.testcaseName = testcaseName;
            this.status = status;
            this.timeMs = timeMs;
            this.detail = detail;
        }
    }

    public List<Testcase> parseTestcases(String rawText) {
        List<Testcase> list = new ArrayList<>();
        rawText = cutBeforeFirstMarker(
                rawText,
                "\nPhần 2:",
                "\nPhan 2:",
                "\n### Phần 2",
                "\n## Phần 2",
                "\nPhần 3:",
                "\nPhan 3:",
                "\nMÃ NGUỒN GENERATOR",
                "\nMA NGUON GENERATOR"
        );
        Pattern p = Pattern.compile(
                "---\\s*(Testcase.*?)\\s*---\\s*\\[Input\\]\\s*(.*?)\\s*\\[Output\\]\\s*(.*?)(?=\\R\\s*---\\s*Testcase|\\z)",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(rawText);
        while (m.find()) {
            String name = m.group(1).trim();
            String input = cleanTestcaseBlock(m.group(2));
            String output = cleanTestcaseBlock(m.group(3));
            list.add(new Testcase(name, input, output));
        }
        return list;
    }

    private String cutBeforeFirstMarker(String text, String... markers) {
        if (text == null) {
            return "";
        }

        int end = text.length();
        Matcher sectionMatcher = Pattern.compile(
                "(?im)^\\s*(?:-{2,}\\s*)?.*(?:GENERATOR|CHECKER|\\bPHAN\\s*[23]\\b|PHẦN\\s*[23]).*$"
        ).matcher(text);
        if (sectionMatcher.find()) {
            end = sectionMatcher.start();
        }

        for (String marker : markers) {
            int index = text.indexOf(marker);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        return text.substring(0, end);
    }

    private String cleanTestcaseBlock(String text) {
        String cleaned = text == null ? "" : text.trim();
        cleaned = cutBeforeUnexpectedTestcaseTail(cleaned);
        cleaned = cleaned.replaceAll("(?m)^```[a-zA-Z+]*\\s*$", "");
        cleaned = cleaned.replaceAll("(?m)^```\\s*$", "");
        cleaned = cleaned.replace("`", "");
        return cleaned.trim();
    }

    private String cutBeforeUnexpectedTestcaseTail(String text) {
        Matcher tailMatcher = Pattern.compile(
                "(?im)^\\s*(?:```|#{1,6}\\s+|---\\s*(?!Testcase)|#include\\b|(?:int|long\\s+long|void)\\s+main\\s*\\(|.*(?:GENERATOR|CHECKER).*)"
        ).matcher(text);
        if (tailMatcher.find()) {
            return text.substring(0, tailMatcher.start());
        }
        return text;
    }

    public String extractCode(String rawText, String type) {
        if (rawText == null || type == null) {
            return null;
        }

        String section = extractCodeSection(rawText, type);
        if (section == null) {
            return null;
        }

        Matcher fencedCode = Pattern.compile("```(?:cpp|c\\+\\+|java)?\\s*(.*?)\\s*```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE)
                .matcher(section);
        if (fencedCode.find()) {
            return fencedCode.group(1).trim();
        }

        return removeMarkdownHeading(section).trim();
    }

    private String extractCodeSection(String rawText, String type) {
        Pattern headingPattern = Pattern.compile(
                "(?im)^\\s*#{1,6}\\s*(?:\\d+\\.\\s*)?CODE\\s+" + Pattern.quote(type) + "\\b.*$"
        );
        Matcher headingMatcher = headingPattern.matcher(rawText);
        if (!headingMatcher.find()) {
            return null;
        }

        int start = headingMatcher.end();
        Matcher nextHeadingMatcher = Pattern.compile("(?im)^\\s*#{1,6}\\s*(?:\\d+\\.\\s*)?CODE\\s+(?:AC|TLE|WA)\\b.*$")
                .matcher(rawText);
        int end = rawText.length();
        while (nextHeadingMatcher.find()) {
            if (nextHeadingMatcher.start() > start) {
                end = nextHeadingMatcher.start();
                break;
            }
        }

        return rawText.substring(start, end).trim();
    }

    private String removeMarkdownHeading(String text) {
        return text.replaceFirst("(?s)^\\s*#{1,6}.*?\\R", "");
    }

    public List<JudgeResult> judgeCode(String code, String language, List<Testcase> testcases) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("local_judge");
        try {
            if ("Java".equalsIgnoreCase(language)) {
                return judgeJava(code, testcases, tempDir);
            } else {
                return judgeCpp(code, testcases, tempDir);
            }
        } finally {
            deleteDirectory(tempDir.toFile());
        }
    }

    private List<JudgeResult> judgeJava(String code, List<Testcase> testcases, Path tempDir) throws IOException, InterruptedException {
        String className = "Main";
        Matcher m = Pattern.compile("public\\s+class\\s+([A-Za-z0-9_]+)").matcher(code);
        if (m.find()) {
            className = m.group(1);
        }
        
        Path sourceFile = tempDir.resolve(className + ".java");
        Files.writeString(sourceFile, code);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new RuntimeException("Không tìm thấy JavaCompiler! Cần chạy ứng dụng bằng JDK.");
        }
        
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        int compileResult = compiler.run(null, null, errStream, sourceFile.toString());
        if (compileResult != 0) {
            List<JudgeResult> res = new ArrayList<>();
            res.add(new JudgeResult("Compile", "CE", 0, "Compilation Error:\n" + errStream.toString(StandardCharsets.UTF_8)));
            return res;
        }

        List<JudgeResult> results = new ArrayList<>();
        Set<String> inputFiles = detectInputFiles(code);
        Set<String> outputFiles = detectOutputFiles(code);
        for (Testcase tc : testcases) {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", tempDir.toString(), className);
            results.add(runProcessWithTimeout(pb, tc, tempDir, inputFiles, outputFiles));
        }
        return results;
    }

    private List<JudgeResult> judgeCpp(String code, List<Testcase> testcases, Path tempDir) throws IOException, InterruptedException {
        Path sourceFile = tempDir.resolve("main.cpp");
        Path exeFile = tempDir.resolve("main.exe");
        Files.writeString(sourceFile, code);

        ProcessBuilder compilePb = new ProcessBuilder("g++", sourceFile.toString(), "-o", exeFile.toString(), "-O2");
        compilePb.redirectErrorStream(true);
        Process compileProc;
        try {
            compileProc = compilePb.start();
        } catch (IOException e) {
            List<JudgeResult> res = new ArrayList<>();
            res.add(new JudgeResult("Compile", "CE", 0, "Không tìm thấy g++ trong PATH. Hãy cài MinGW/MSYS2 hoặc thêm g++ vào PATH.\n" + e.getMessage()));
            return res;
        }
        boolean compiled = compileProc.waitFor(10, TimeUnit.SECONDS);
        if (!compiled || compileProc.exitValue() != 0) {
            String err = new String(compileProc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!compiled) compileProc.destroyForcibly();
            List<JudgeResult> res = new ArrayList<>();
            res.add(new JudgeResult("Compile", "CE", 0, buildCppCompileErrorMessage(err)));
            return res;
        }

        List<JudgeResult> results = new ArrayList<>();
        Set<String> inputFiles = detectInputFiles(code);
        Set<String> outputFiles = detectOutputFiles(code);
        for (Testcase tc : testcases) {
            ProcessBuilder pb = new ProcessBuilder(exeFile.toString());
            results.add(runProcessWithTimeout(pb, tc, tempDir, inputFiles, outputFiles));
        }
        return results;
    }

    private JudgeResult runProcessWithTimeout(
            ProcessBuilder pb,
            Testcase tc,
            Path tempDir,
            Set<String> inputFiles,
            Set<String> outputFiles
    ) {
        try {
            prepareFileIo(tempDir, tc, inputFiles, outputFiles);
            pb.directory(tempDir.toFile());

            long startTime = System.currentTimeMillis();
            Process p = pb.start();
            
            try (OutputStream os = p.getOutputStream()) {
                os.write(tc.input.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            boolean finished = p.waitFor(2, TimeUnit.SECONDS);
            long timeMs = System.currentTimeMillis() - startTime;
            
            if (!finished) {
                p.destroyForcibly();
                return new JudgeResult(tc.name, "TLE", 2000, "Time Limit Exceeded (>2s)\nInput:\n" + tc.input);
            }

            if (p.exitValue() != 0) {
                String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                return new JudgeResult(tc.name, "RE", timeMs, "Runtime Error:\nInput:\n" + tc.input + "\nError:\n" + err);
            }

            String output = readProgramOutput(p, tempDir, outputFiles).trim();
            
            String normalizedExpected = normalizeOutput(tc.expectedOutput);
            String normalizedOutput = normalizeOutput(output);
            
            if (normalizedOutput.equals(normalizedExpected)) {
                return new JudgeResult(tc.name, "AC", timeMs, "Accepted");
            } else {
                return new JudgeResult(tc.name, "WA", timeMs, buildWrongAnswerDetail(tc, normalizedExpected, normalizedOutput));
            }

        } catch (Exception e) {
            return new JudgeResult(tc.name, "RE", 0, "Lỗi chạy process:\nInput:\n" + tc.input + "\nError:\n" + e.getMessage());
        }
    }

    private String normalizeOutput(String text) {
        return (text == null ? "" : text)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
    }

    private String buildCppCompileErrorMessage(String compilerOutput) {
        String detail = compilerOutput == null || compilerOutput.isBlank()
                ? "g++ không trả về chi tiết lỗi."
                : compilerOutput.trim();

        if (detail.contains("undefined reference to `WinMain'") || detail.contains("undefined reference to `main'")) {
            return "Compilation Error (C++): Không tìm thấy hàm main() hợp lệ trong code được chấm.\n" + detail;
        }

        return "Compilation Error (C++):\n" + detail;
    }

    private String buildWrongAnswerDetail(Testcase tc, String expected, String actual) {
        return "Wrong Answer.\n"
                + "Input:\n" + tc.input + "\n\n"
                + "Expected:\n" + expected + "\n\n"
                + "Got:\n" + actual + "\n\n"
                + "Khác biệt đầu tiên:\n" + findFirstDifference(expected, actual);
    }

    private String findFirstDifference(String expected, String actual) {
        String[] expectedLines = expected.split("\\R", -1);
        String[] actualLines = actual.split("\\R", -1);
        int maxLines = Math.max(expectedLines.length, actualLines.length);

        for (int i = 0; i < maxLines; i++) {
            String expectedLine = i < expectedLines.length ? expectedLines[i] : "[không có dòng]";
            String actualLine = i < actualLines.length ? actualLines[i] : "[không có dòng]";
            if (!expectedLine.equals(actualLine)) {
                return "Dòng " + (i + 1) + "\n"
                        + "Expected: " + expectedLine + "\n"
                        + "Got     : " + actualLine;
            }
        }

        if (!expected.equals(actual)) {
            return "Khác nhau ở khoảng trắng hoặc ký tự xuống dòng cuối.";
        }
        return "Không tìm thấy khác biệt sau khi chuẩn hóa output.";
    }

    private void prepareFileIo(Path tempDir, Testcase tc, Set<String> inputFiles, Set<String> outputFiles) throws IOException {
        for (String outputFile : outputFiles) {
            Files.deleteIfExists(tempDir.resolve(outputFile));
        }

        for (String inputFile : inputFiles) {
            Files.writeString(tempDir.resolve(inputFile), tc.input, StandardCharsets.UTF_8);
        }
    }

    private String readProgramOutput(Process process, Path tempDir, Set<String> outputFiles) throws IOException {
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        for (String outputFile : outputFiles) {
            Path outputPath = tempDir.resolve(outputFile);
            if (Files.exists(outputPath)) {
                String fileOutput = Files.readString(outputPath, StandardCharsets.UTF_8);
                if (!fileOutput.isBlank()) {
                    return fileOutput;
                }
            }
        }
        return stdout;
    }

    private Set<String> detectInputFiles(String code) {
        Set<String> files = detectFileNames(code, "\\.(?:inp|in|txt)");
        files.add("tri.inp");
        files.add("input.txt");
        files.add("inp.txt");
        files.add("main.in");
        files.add("test.in");
        files.add("data.in");
        return files;
    }

    private Set<String> detectOutputFiles(String code) {
        Set<String> files = detectFileNames(code, "\\.(?:out|ans)");
        files.add("tri.out");
        files.add("output.txt");
        files.add("out.txt");
        files.add("main.out");
        files.add("test.out");
        files.add("data.out");
        return files;
    }

    private Set<String> detectFileNames(String code, String extensionPattern) {
        Set<String> files = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("\"([A-Za-z0-9_. -]+" + extensionPattern + ")\"").matcher(code);
        while (matcher.find()) {
            files.add(matcher.group(1));
        }
        return files;
    }

    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }
}
