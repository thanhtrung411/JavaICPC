package UI;

import Service.AIService;
import Service.DbService;

import javax.swing.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Properties;

public class MainUI extends JFrame {
    private static final Path CONFIG_PATH = Path.of("data", "app.properties");
    private static final String CONFIG_DB_URL = "db.url";
    private static final String CONFIG_GOOGLE_PROJECT_ID = "google.projectId";
    private static final String CONFIG_GOOGLE_LOCATION = "google.location";
    private static final String CONFIG_GOOGLE_AUTH_TYPE = "google.authType";
    private static final String CONFIG_GOOGLE_CREDENTIAL = "google.credential";
    private static final String CONFIG_GOOGLE_SERVICE_ACCOUNT_JSON_PATH = "google.serviceAccountJsonPath";
    private static final String CONFIG_AI_MODEL = "ai.model";
    private static final String CONFIG_AI_MAX_TOKENS = "ai.maxTokens";
    private static final String CONFIG_AI_TIMEOUT = "ai.timeout";

    private JTextField txtProblemCode;
    private JTextField txtProblemName;
    private JComboBox<String> cboProblemSource;
    private JTextArea txtStatement;
    private JEditorPane txtAnalysisResult;
    private JEditorPane txtGeneratedTestcases;
    private JEditorPane txtGeneratedCode;
    private JLabel lblSelectedFile;
    private JButton btnGenerateTestcases;
    private JButton btnGenerateSampleCode;
    private JButton btnImportSampleCode;
    private JButton btnRunStrengthCheck;
    private JButton btnAnalyzeProblem;
    private JButton btnRunAllInOne;
    private java.util.List<File> selectedProblemFiles;
    private String lastAnalysisJson;
    private String lastAiSampleCodeLanguage;
    private String lastManualSampleCodeLanguage;
    private String lastGeneratedTestcasesRaw;
    private String lastAiGeneratedCodeRaw;
    private String lastManualCodeRaw;
    private JTabbedPane resultTabs;

    private JTextField txtDbUrl;
    private JTextField txtGoogleProjectId;
    private JTextField txtGoogleLocation;
    private JComboBox<String> cboGoogleAuthType;
    private JTextArea txtGoogleCredential;
    private JTextField txtServiceAccountJsonPath;
    private JTextField txtAIModel;
    private JTextField txtAIMaxTokens;
    private JTextField txtAITimeout;

    private StatusBarPanel statusBarPanel;
    private DefaultListModel<DbService.SavedProblemSummary> historyModel;
    private JList<DbService.SavedProblemSummary> historyList;
    private JPanel historyCards;
    private CardLayout historyCardLayout;
    private JTextArea historyListMessageArea;
    private JTextField historyCodeField;
    private JTextField historyNameField;
    private JTextField historySourceField;
    private JTextField historyCreatedAtField;
    private JTextArea historyStatementArea;
    private JEditorPane historyAnalysisPane;
    private JTextArea historyTestcasesArea;
    private JTextArea historySampleCodesArea;
    private JTextArea historyArtifactsArea;

    public MainUI() {
        initUI();
        loadAppConfig();
        SwingUtilities.invokeLater(this::testConfiguredConnectionsOnStartup);
    }

    private void initUI() {
        setTitle("Hệ thống tạo testcase");
        setSize(1280, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel rootPanel = new JPanel(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Đề bài", create_De_bai_Layout());
        tabbedPane.addTab("Lịch sử", create_Lich_su_Layout());
        tabbedPane.addTab("Cấu hình", create_Cau_hinh_Layout());
        tabbedPane.addChangeListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex >= 0 && "Lịch sử".equals(tabbedPane.getTitleAt(selectedIndex))) {
                refreshHistoryList();
            }
        });

        rootPanel.add(tabbedPane, BorderLayout.CENTER);
        rootPanel.add(createStatusBar(), BorderLayout.SOUTH);

        setContentPane(rootPanel);
    }

    private JPanel createStatusBar() {
        statusBarPanel = new StatusBarPanel();
        return statusBarPanel;
    }

    private JComponent create_De_bai_Layout() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        panel.setPreferredSize(new Dimension(1320, 1220));

        panel.add(createProblemInfoPanel(), BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.34);
        splitPane.setDividerLocation(330);
        splitPane.setMinimumSize(new Dimension(1120, 1060));
        splitPane.setTopComponent(createStatementPanel());
        splitPane.setBottomComponent(createProblemWorkflowPanel());

        panel.add(splitPane, BorderLayout.CENTER);

        JScrollPane pageScroll = new JScrollPane(panel);
        pageScroll.setBorder(null);
        pageScroll.getVerticalScrollBar().setUnitIncrement(16);
        pageScroll.getHorizontalScrollBar().setUnitIncrement(16);
        return pageScroll;
    }

    private JPanel createProblemInfoPanel() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        txtProblemCode = new JTextField();
        txtProblemName = new JTextField();
        cboProblemSource = new JComboBox<>(new String[]{"ICPC", "IOI", "VNOI", "Codeforces", "Khác"});

        addRow(formPanel, gbc, 0, "Mã bài", txtProblemCode);
        addRow(formPanel, gbc, 1, "Tên bài", txtProblemName);
        addRow(formPanel, gbc, 2, "Nguồn", cboProblemSource);

        return formPanel;
    }

    private JScrollPane createStatementPanel() {
        txtStatement = new JTextArea();
        txtStatement.setLineWrap(true);
        txtStatement.setWrapStyleWord(true);
        txtStatement.setMargin(new Insets(10, 10, 10, 10));
        txtStatement.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        StatementTextFilter.install(txtStatement);

        JScrollPane statementScroll = new JScrollPane(txtStatement);
        statementScroll.setBorder(BorderFactory.createTitledBorder("Nội dung đề bài"));
        statementScroll.setMinimumSize(new Dimension(960, 240));
        statementScroll.setPreferredSize(new Dimension(1200, 320));
        return statementScroll;
    }

    private JPanel createProblemWorkflowPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Quy trình xử lý"));
        panel.setMinimumSize(new Dimension(960, 720));
        panel.setPreferredSize(new Dimension(1200, 820));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));

        JButton btnNewProblem = new JButton("Nhập đề mới");
        JButton btnImportFile = new JButton("Nhập ảnh/PDF");
        JButton btnRebuildStatement = new JButton("AI tái tạo đề");
        JButton btnSaveProblem = new JButton("Lưu đề bài");
        btnAnalyzeProblem = new JButton("Phân tích đề");
        btnGenerateTestcases = new JButton("Tạo testcase");
        btnGenerateSampleCode = new JButton("Tạo code mẫu");
        btnImportSampleCode = new JButton("Nhập code mẫu");
        btnRunAllInOne = new JButton("All in one");
        btnRunStrengthCheck = new JButton("Kiểm tra độ mạnh");

        btnGenerateTestcases.setEnabled(false);
        btnGenerateSampleCode.setEnabled(false);
        btnRunStrengthCheck.setEnabled(false);

        btnNewProblem.addActionListener(e -> newProblemAction());
        btnImportFile.addActionListener(e -> chooseProblemFile());
        btnRebuildStatement.addActionListener(e -> rebuildStatementFromFile());
        btnSaveProblem.addActionListener(e -> saveProblemAction(btnSaveProblem));
        btnAnalyzeProblem.addActionListener(e -> analyzeCurrentStatement());
        btnGenerateTestcases.addActionListener(e -> generateTestcasesAction());
        btnGenerateSampleCode.addActionListener(e -> generateSampleCodeAction());
        btnImportSampleCode.addActionListener(e -> importSampleCodeAction());
        btnRunAllInOne.addActionListener(e -> runAllInOneAction());
        btnRunStrengthCheck.addActionListener(e -> runStrengthCheckAction());

        buttonPanel.add(btnNewProblem);
        buttonPanel.add(btnImportFile);
        buttonPanel.add(btnRebuildStatement);
        buttonPanel.add(btnSaveProblem);
        buttonPanel.add(btnAnalyzeProblem);
        buttonPanel.add(btnGenerateTestcases);
        buttonPanel.add(btnGenerateSampleCode);
        buttonPanel.add(btnImportSampleCode);
        buttonPanel.add(btnRunAllInOne);
        buttonPanel.add(btnRunStrengthCheck);

        lblSelectedFile = new JLabel("Chưa chọn file ảnh/PDF");

        JPanel topPanel = new JPanel(new BorderLayout(8, 4));
        topPanel.add(buttonPanel, BorderLayout.CENTER);
        topPanel.add(lblSelectedFile, BorderLayout.SOUTH);

        resultTabs = new JTabbedPane();
        txtAnalysisResult = new JEditorPane();
        txtAnalysisResult.setContentType("text/html");
        txtAnalysisResult.setEditable(false);
        txtAnalysisResult.setMargin(new Insets(10, 10, 10, 10));
        txtAnalysisResult.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        txtAnalysisResult.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        txtGeneratedTestcases = new JEditorPane();
        txtGeneratedTestcases.setContentType("text/html");
        txtGeneratedTestcases.setEditable(false);
        txtGeneratedTestcases.setMargin(new Insets(10, 10, 10, 10));
        txtGeneratedTestcases.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        txtGeneratedCode = new JEditorPane();
        txtGeneratedCode.setContentType("text/html");
        txtGeneratedCode.setEditable(false);
        txtGeneratedCode.setMargin(new Insets(10, 10, 10, 10));
        txtGeneratedCode.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        resultTabs.addTab("Phân tích", new JScrollPane(txtAnalysisResult));
        resultTabs.addTab("Testcase", new JScrollPane(txtGeneratedTestcases));
        resultTabs.addTab("Code mẫu", new JScrollPane(txtGeneratedCode));

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(resultTabs, BorderLayout.CENTER);
        return panel;
    }

    private void newProblemAction() {
        if (hasCurrentProblemDraft()) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Dữ liệu đề hiện tại trên màn hình sẽ được xóa để nhập đề mới.\nBạn có muốn tiếp tục không?",
                    "Nhập đề mới",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }

        txtProblemCode.setText("");
        txtProblemName.setText("");
        cboProblemSource.setSelectedIndex(0);
        txtStatement.setText("");
        selectedProblemFiles = null;
        lblSelectedFile.setText("Chưa chọn file ảnh/PDF");

        lastAnalysisJson = null;
        lastAiSampleCodeLanguage = null;
        lastManualSampleCodeLanguage = null;
        lastGeneratedTestcasesRaw = null;
        lastAiGeneratedCodeRaw = null;
        lastManualCodeRaw = null;

        txtAnalysisResult.setText("");
        txtGeneratedTestcases.setText("");
        txtGeneratedCode.setText("");
        resultTabs.setSelectedIndex(0);

        btnAnalyzeProblem.setEnabled(true);
        btnGenerateTestcases.setEnabled(false);
        btnGenerateSampleCode.setEnabled(false);
        btnRunAllInOne.setEnabled(true);
        btnRunStrengthCheck.setEnabled(false);
        txtStatement.requestFocusInWindow();
    }

    private boolean hasCurrentProblemDraft() {
        return !txtProblemCode.getText().trim().isEmpty()
                || !txtProblemName.getText().trim().isEmpty()
                || !txtStatement.getText().trim().isEmpty()
                || selectedProblemFiles != null && !selectedProblemFiles.isEmpty()
                || lastAnalysisJson != null && !lastAnalysisJson.isBlank()
                || lastGeneratedTestcasesRaw != null && !lastGeneratedTestcasesRaw.isBlank()
                || lastAiGeneratedCodeRaw != null && !lastAiGeneratedCodeRaw.isBlank()
                || lastManualCodeRaw != null && !lastManualCodeRaw.isBlank();
    }

    private JTextArea createReadonlyTextArea() {
        JTextArea textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setMargin(new Insets(8, 8, 8, 8));
        return textArea;
    }

    private void chooseProblemFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn ảnh hoặc PDF đề bài");
        fileChooser.setMultiSelectionEnabled(true);
        javax.swing.filechooser.FileNameExtensionFilter filter = new javax.swing.filechooser.FileNameExtensionFilter(
                "Ảnh & PDF (jpg, png, pdf)", "jpg", "jpeg", "png", "pdf", "webp", "heic");
        fileChooser.setFileFilter(filter);
        
        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File[] files = fileChooser.getSelectedFiles();
        selectedProblemFiles = files != null ? java.util.Arrays.asList(files) : null;
        
        if (selectedProblemFiles != null && !selectedProblemFiles.isEmpty()) {
            if (selectedProblemFiles.size() == 1) {
                lblSelectedFile.setText(selectedProblemFiles.get(0).getName());
                appendAnalysisLog("Đã chọn file: " + selectedProblemFiles.get(0).getAbsolutePath());
            } else {
                lblSelectedFile.setText("Đã chọn " + selectedProblemFiles.size() + " file");
                appendAnalysisLog("Đã chọn " + selectedProblemFiles.size() + " file");
            }
            appendAnalysisLog("Bấm 'AI tái tạo đề' để đưa nội dung từ ảnh/PDF vào ô Nội dung đề bài.");
        }
    }

    private void rebuildStatementFromFile() {
        if (selectedProblemFiles == null || selectedProblemFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bạn cần chọn ảnh/PDF trước.", "Thiếu file", JOptionPane.WARNING_MESSAGE);
            return;
        }

        appendAnalysisLog("Đang xử lý " + selectedProblemFiles.size() + " file ảnh/PDF...");
        
        AIService aiService;
        try {
            aiService = createConfiguredAIService();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Thiếu cấu hình AI", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnAnalyzeProblem.setEnabled(false);
        Timer apiTimer = startTextAreaElapsedTimer(txtStatement, "Đang đọc ảnh/PDF bằng AI, vui lòng đợi...");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return aiService.rebuildStatementFromFiles(selectedProblemFiles);
            }

            @Override
            protected void done() {
                apiTimer.stop();
                try {
                    String result = get();
                    String cleanResult = AnalysisFormatter.stripMarkdownFence(result);
                    
                    if (cleanResult.contains("[LỖI_NHIỀU_ĐỀ]")) {
                        txtStatement.setText("");
                        JOptionPane.showMessageDialog(MainUI.this, "AI phát hiện có nhiều hơn 1 đề bài trong các file đã chọn.\\nVui lòng chỉ chọn các trang/ảnh của 1 đề bài duy nhất!", "Lỗi nhiều đề bài", JOptionPane.ERROR_MESSAGE);
                        appendAnalysisLog("Hủy tái tạo đề do phát hiện nhiều đề bài.");
                        btnAnalyzeProblem.setEnabled(true);
                    } else {
                        txtStatement.setText(cleanResult);
                        appendAnalysisLog("Đã tái tạo đề thành công. Hãy kiểm tra nội dung và bấm 'Phân tích đề'.");
                        btnAnalyzeProblem.setEnabled(true);
                    }
                } catch (Exception e) {
                    txtStatement.setText("");
                    setAiFailed(e.getMessage());
                    JOptionPane.showMessageDialog(MainUI.this, "Lỗi đọc file bằng AI: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    btnAnalyzeProblem.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void analyzeCurrentStatement() {
        String statement = txtStatement.getText().trim();
        if (statement.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bạn cần nhập hoặc tái tạo nội dung đề trước.", "Thiếu đề bài", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AIService aiService;
        try {
            aiService = createConfiguredAIService();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Thiếu cấu hình AI", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnAnalyzeProblem.setEnabled(false);
        btnGenerateTestcases.setEnabled(false);
        btnGenerateSampleCode.setEnabled(false);
        btnRunStrengthCheck.setEnabled(false);
        btnRunAllInOne.setEnabled(false);
        setAiConnecting();
        Timer apiTimer = startEditorElapsedTimer(txtAnalysisResult, "Đang phân tích đề bằng AI...");

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return aiService.analyzeProblem(statement);
            }

            @Override
            protected void done() {
                apiTimer.stop();
                btnAnalyzeProblem.setEnabled(true);
                btnRunAllInOne.setEnabled(true);
                try {
                    String analysisJson = get();
                    String cleanJson = AnalysisFormatter.stripMarkdownFence(analysisJson);
                    lastAnalysisJson = cleanJson;
                    txtAnalysisResult.setText(AnalysisFormatter.formatAnalysisResult(
                            cleanJson,
                            getConfiguredMaxTokensForDisplay(),
                            aiService.getLastRawResponse(),
                            aiService.getLastRequestDurationMs()
                    ));
                    
                    String title = AnalysisFormatter.extractStringSafe(cleanJson, "title");
                    if (!title.startsWith("[Không")) {
                        txtProblemName.setText(title);
                    }
                    String problemCode = AnalysisFormatter.extractStringSafe(cleanJson, "problem_code");
                    if (!problemCode.startsWith("[Không")) {
                        txtProblemCode.setText(problemCode);
                    }
                    
                    setAiConnected();
                    btnGenerateTestcases.setEnabled(true);
                    btnGenerateSampleCode.setEnabled(true);
                    btnRunStrengthCheck.setEnabled(true);
                    txtAnalysisResult.setCaretPosition(0);
                } catch (Exception e) {
                    setAiFailed(e.getMessage());
                    txtAnalysisResult.setText("<html><body><b style='color:red;'>Phân tích đề thất bại:</b><br/>" + e.getMessage() + "</body></html>");
                }
            }
        };
        worker.execute();
    }

    private void generateTestcasesAction() {
        if (lastAnalysisJson == null || lastAnalysisJson.isBlank()) {
            JOptionPane.showMessageDialog(this, "Bạn cần Phân tích đề trước khi tạo testcase.", "Chưa có phân tích", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AIService aiService;
        try {
            aiService = createConfiguredAIService();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Thiếu cấu hình AI", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnGenerateTestcases.setEnabled(false);
        btnRunAllInOne.setEnabled(false);
        resultTabs.setSelectedIndex(1); // Switch to Testcase tab
        Timer apiTimer = startEditorElapsedTimer(
                txtGeneratedTestcases,
                "Đang nhờ AI suy nghĩ testcase hiểm hóc và viết Generator, Checker (có thể mất 15-30s)..."
        );

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return aiService.generateTestcasesAndChecker(txtStatement.getText().trim(), lastAnalysisJson);
            }

            @Override
            protected void done() {
                apiTimer.stop();
                try {
                    String result = get();
                    lastGeneratedTestcasesRaw = result;
                    txtGeneratedTestcases.setText(AnalysisFormatter.renderMarkdownToHtml(
                            result,
                            getConfiguredMaxTokensForDisplay(),
                            aiService.getLastRawResponse(),
                            aiService.getLastRequestDurationMs()
                    ));
                    txtGeneratedTestcases.setCaretPosition(0);
                    updateStrengthCheckAvailability();
                } catch (Exception e) {
                    txtGeneratedTestcases.setText("Lỗi sinh testcase: " + e.getMessage());
                } finally {
                    btnGenerateTestcases.setEnabled(true);
                    btnRunAllInOne.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void generateSampleCodeAction() {
        if (lastAnalysisJson == null || lastAnalysisJson.isBlank()) {
            JOptionPane.showMessageDialog(this, "Bạn cần Phân tích đề trước khi tạo code mẫu.", "Chưa có phân tích", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedLanguage = askSampleCodeLanguage();
        if (selectedLanguage == null) {
            return;
        }

        AIService aiService;
        try {
            aiService = createConfiguredAIService();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Thiếu cấu hình AI", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnGenerateSampleCode.setEnabled(false);
        btnRunAllInOne.setEnabled(false);
        resultTabs.setSelectedIndex(2); // Switch to Code mẫu tab
        Timer apiTimer = startEditorElapsedTimer(
                txtGeneratedCode,
                "Đang nhờ AI viết 3 phiên bản code (AC, TLE, WA) bằng " + selectedLanguage + " (có thể mất 15-30s)..."
        );

        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                return aiService.generateSampleCode(txtStatement.getText().trim(), lastAnalysisJson, selectedLanguage);
            }

            @Override
            protected void done() {
                apiTimer.stop();
                try {
                    String result = get();
                    setActiveSampleCode("AI_GENERATED", result, selectedLanguage);
                    txtGeneratedCode.setText(AnalysisFormatter.renderMarkdownToHtml(
                            result,
                            getConfiguredMaxTokensForDisplay(),
                            aiService.getLastRawResponse(),
                            aiService.getLastRequestDurationMs()
                    ));
                    txtGeneratedCode.setCaretPosition(0);
                } catch (Exception e) {
                    txtGeneratedCode.setText("Lỗi sinh code mẫu: " + e.getMessage());
                } finally {
                    btnGenerateSampleCode.setEnabled(true);
                    btnRunAllInOne.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void importSampleCodeAction() {
        String selectedLanguage = askSampleCodeLanguage();
        if (selectedLanguage == null) {
            return;
        }

        ManualSampleCodeDialog.Result result = ManualSampleCodeDialog.show(this, selectedLanguage);
        if (result == null) {
            return;
        }

        setActiveSampleCode("MANUAL_INPUT", result.rawCode(), result.language());
        txtGeneratedCode.setText(AnalysisFormatter.renderMarkdownToHtml(result.rawCode()));
        txtGeneratedCode.setCaretPosition(0);
        resultTabs.setSelectedIndex(2);
        updateStrengthCheckAvailability();
    }

    private void setActiveSampleCode(String source, String rawCode, String language) {
        if ("AI_GENERATED".equals(source)) {
            lastAiGeneratedCodeRaw = rawCode;
            lastAiSampleCodeLanguage = language;
        } else if ("MANUAL_INPUT".equals(source)) {
            lastManualCodeRaw = rawCode;
            lastManualSampleCodeLanguage = language;
        }
        updateStrengthCheckAvailability();
    }

    private void saveProblemAction(JButton btnSaveProblem) {
        String statement = txtStatement.getText().trim();
        if (statement.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bạn cần nhập hoặc tái tạo nội dung đề bài trước khi lưu.", "Thiếu đề bài", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String name = defaultIfBlank(txtProblemName.getText().trim(), AnalysisFormatter.extractStringSafe(lastAnalysisJson == null ? "" : lastAnalysisJson, "title"));
        if (name.startsWith("[") || name.isBlank()) {
            name = "Đề bài chưa đặt tên";
        }
        txtProblemName.setText(name);

        String code = defaultIfBlank(txtProblemCode.getText().trim(), AnalysisFormatter.extractStringSafe(lastAnalysisJson == null ? "" : lastAnalysisJson, "problem_code"));
        if (code.startsWith("[") || code.isBlank()) {
            code = "PROB-" + System.currentTimeMillis();
        }
        txtProblemCode.setText(code);

        String source = String.valueOf(cboProblemSource.getSelectedItem());
        String dbUrl = txtDbUrl.getText().trim();
        java.util.List<DbService.SavedTestcase> testcases = buildSavedTestcases();
        java.util.List<DbService.SavedSampleCode> sampleCodes = buildSavedSampleCodes();
        DbService.SavedTestcaseArtifact testcaseArtifact = buildSavedTestcaseArtifact();

        DbService.SaveProblemRequest request = new DbService.SaveProblemRequest(
                code,
                name,
                source,
                statement,
                lastAnalysisJson,
                testcases,
                sampleCodes,
                testcaseArtifact
        );

        btnSaveProblem.setEnabled(false);
        setDbConnecting();

        SwingWorker<DbService.SaveProblemResult, Void> worker = new SwingWorker<>() {
            @Override
            protected DbService.SaveProblemResult doInBackground() throws Exception {
                return new DbService(dbUrl).saveProblem(request);
            }

            @Override
            protected void done() {
                btnSaveProblem.setEnabled(true);
                try {
                    DbService.SaveProblemResult result = get();
                    setDbConnected();
                    appendAnalysisLog("Đã lưu đề bài #" + result.problemId() + " vào SQLite.");
                    refreshHistoryList();
                    JOptionPane.showMessageDialog(
                            MainUI.this,
                            "Đã lưu đề bài thành công.\n" +
                                    "Problem ID: " + result.problemId() + "\n" +
                                    "Phân tích AI: " + result.analysisCount() + "\n" +
                                    "Testcase: " + result.testcaseCount() + "\n" +
                                    "Code mẫu: " + result.sampleCodeCount() + "\n" +
                                    "Generator/Checker: " + result.artifactCount(),
                            "Lưu đề bài",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception e) {
                    setDbFailed(e.getMessage());
                    JOptionPane.showMessageDialog(MainUI.this, "Lưu đề bài thất bại:\n" + e.getMessage(), "Lỗi DB", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private java.util.List<DbService.SavedTestcase> buildSavedTestcases() {
        java.util.List<DbService.SavedTestcase> saved = new java.util.ArrayList<>();
        if (lastGeneratedTestcasesRaw == null || lastGeneratedTestcasesRaw.isBlank()) {
            return saved;
        }

        Service.LocalJudgeService judgeService = new Service.LocalJudgeService();
        for (Service.LocalJudgeService.Testcase testcase : judgeService.parseTestcases(lastGeneratedTestcasesRaw)) {
            saved.add(new DbService.SavedTestcase(
                    testcase.name,
                    testcase.input,
                    testcase.expectedOutput,
                    "AI_GENERATED"
            ));
        }
        return saved;
    }

    private java.util.List<DbService.SavedSampleCode> buildSavedSampleCodes() {
        java.util.List<DbService.SavedSampleCode> saved = new java.util.ArrayList<>();
        appendSavedSampleCodes(saved, lastAiGeneratedCodeRaw, lastAiSampleCodeLanguage, "AI_GENERATED");
        appendSavedSampleCodes(saved, lastManualCodeRaw, lastManualSampleCodeLanguage, "MANUAL_INPUT");
        return saved;
    }

    private DbService.SavedTestcaseArtifact buildSavedTestcaseArtifact() {
        if (lastGeneratedTestcasesRaw == null || lastGeneratedTestcasesRaw.isBlank()) {
            return null;
        }

        String generatorCode = extractCodeBlockAfterMarker(lastGeneratedTestcasesRaw, "GENERATOR");
        String checkerCode = extractCodeBlockAfterMarker(lastGeneratedTestcasesRaw, "CHECKER");
        String checkerType = detectCheckerType(lastGeneratedTestcasesRaw, checkerCode);

        return new DbService.SavedTestcaseArtifact(
                lastGeneratedTestcasesRaw,
                generatorCode,
                checkerCode,
                checkerType
        );
    }

    private String extractCodeBlockAfterMarker(String text, String marker) {
        if (text == null || text.isBlank()) {
            return "";
        }

        java.util.regex.Matcher sectionMatcher = java.util.regex.Pattern
                .compile("(?is)" + java.util.regex.Pattern.quote(marker) + ".*?```(?:cpp|c\\+\\+|java)?\\s*(.*?)\\s*```")
                .matcher(text);
        if (sectionMatcher.find()) {
            return sectionMatcher.group(1).trim();
        }
        return "";
    }

    private String detectCheckerType(String rawText, String checkerCode) {
        if (checkerCode != null && !checkerCode.isBlank()) {
            return "SPECIAL";
        }

        String lower = rawText == null ? "" : rawText.toLowerCase();
        if (lower.contains("diff checker") || lower.contains("đáp án duy nhất") || lower.contains("dap an duy nhat")) {
            return "DIFF";
        }
        return "UNKNOWN";
    }

    private void appendSavedSampleCodes(
            java.util.List<DbService.SavedSampleCode> saved,
            String rawCode,
            String language,
            String source
    ) {
        if (rawCode == null || rawCode.isBlank()) {
            return;
        }

        Service.LocalJudgeService judgeService = new Service.LocalJudgeService();
        String normalizedLanguage = defaultIfBlank(language, "Không rõ");
        int before = saved.size();
        for (String verdictType : new String[]{"AC", "TLE", "WA"}) {
            String code = judgeService.extractCode(rawCode, verdictType);
            if (code != null && !code.isBlank()) {
                saved.add(new DbService.SavedSampleCode(
                        verdictType,
                        normalizedLanguage,
                        code,
                        source
                ));
            }
        }

        if (saved.size() == before) {
            saved.add(new DbService.SavedSampleCode(
                    "RAW",
                    normalizedLanguage,
                    rawCode,
                    source
            ));
        }
    }

    private String askSampleCodeLanguage() {
        String[] options = {"C++", "Java", "Hủy"};
        int choice = JOptionPane.showOptionDialog(this,
                "Bạn muốn sinh code mẫu bằng ngôn ngữ nào?",
                "Chọn ngôn ngữ lập trình",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
            return null;
        }
        return options[choice];
    }

    private void runAllInOneAction() {
        String statement = txtStatement.getText().trim();
        if (statement.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Bạn cần nhập hoặc tái tạo nội dung đề trước.", "Thiếu đề bài", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedLanguage = askSampleCodeLanguage();
        if (selectedLanguage == null) {
            return;
        }

        AIService aiService;
        try {
            aiService = createConfiguredAIService();
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Thiếu cấu hình AI", JOptionPane.WARNING_MESSAGE);
            return;
        }

        setWorkflowBusy(true);
        setAiConnecting();
        long allInOneStartedAt = System.currentTimeMillis();
        int[] currentTabIndex = {0};
        String[] currentProgressMessage = {"Đang phân tích đề bằng AI..."};
        long[] currentPhaseStartedAt = {allInOneStartedAt};
        Timer apiTimer = new Timer(1000, e -> showElapsedProgressInTab(
                currentTabIndex[0],
                currentProgressMessage[0],
                currentPhaseStartedAt[0],
                allInOneStartedAt
        ));
        showElapsedProgressInTab(currentTabIndex[0], currentProgressMessage[0], currentPhaseStartedAt[0], allInOneStartedAt);
        apiTimer.start();

        SwingWorker<AllInOneResult, AllInOneProgress> worker = new SwingWorker<>() {
            @Override
            protected AllInOneResult doInBackground() throws Exception {
                publish(new AllInOneProgress(0, "Đang phân tích đề bằng AI..."));
                String analysisJson = AnalysisFormatter.stripMarkdownFence(aiService.analyzeProblem(statement));
                String analysisStats = aiService.getLastRawResponse();
                long analysisDurationMs = aiService.getLastRequestDurationMs();
                lastAnalysisJson = analysisJson;

                publish(new AllInOneProgress(1, "Đang tạo testcase..."));
                String testcases = aiService.generateTestcasesAndChecker(statement, lastAnalysisJson);
                String testcaseStats = aiService.getLastRawResponse();
                long testcaseDurationMs = aiService.getLastRequestDurationMs();
                lastGeneratedTestcasesRaw = testcases;

                publish(new AllInOneProgress(2, "Đang nhờ AI viết 3 phiên bản code (AC, TLE, WA) bằng " + selectedLanguage + " (có thể mất 15-30s)..."));
                String sampleCode = aiService.generateSampleCode(statement, lastAnalysisJson, selectedLanguage);
                String sampleCodeStats = aiService.getLastRawResponse();
                long sampleCodeDurationMs = aiService.getLastRequestDurationMs();

                return new AllInOneResult(
                        analysisJson,
                        analysisStats,
                        analysisDurationMs,
                        testcases,
                        testcaseStats,
                        testcaseDurationMs,
                        sampleCode,
                        sampleCodeStats,
                        sampleCodeDurationMs,
                        selectedLanguage
                );
            }

            @Override
            protected void process(java.util.List<AllInOneProgress> chunks) {
                AllInOneProgress progress = chunks.get(chunks.size() - 1);
                currentTabIndex[0] = progress.tabIndex();
                currentProgressMessage[0] = progress.message();
                currentPhaseStartedAt[0] = System.currentTimeMillis();
                showElapsedProgressInTab(currentTabIndex[0], currentProgressMessage[0], currentPhaseStartedAt[0], allInOneStartedAt);
            }

            @Override
            protected void done() {
                apiTimer.stop();
                try {
                    AllInOneResult result = get();
                    applyAllInOneResult(result);
                    setAiConnected();
                    resultTabs.setSelectedIndex(2);
                } catch (Exception e) {
                    setAiFailed(e.getMessage());
                    JOptionPane.showMessageDialog(MainUI.this, "All in one thất bại: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                } finally {
                    setWorkflowBusy(false);
                }
            }
        };
        worker.execute();
    }

    private void applyAllInOneResult(AllInOneResult result) {
        lastAnalysisJson = result.analysisJson();
        lastGeneratedTestcasesRaw = result.testcases();
        setActiveSampleCode("AI_GENERATED", result.sampleCode(), result.language());

        txtAnalysisResult.setText(AnalysisFormatter.formatAnalysisResult(
                result.analysisJson(),
                getConfiguredMaxTokensForDisplay(),
                result.analysisStats(),
                result.analysisDurationMs()
        ));
        txtGeneratedTestcases.setText(AnalysisFormatter.renderMarkdownToHtml(
                result.testcases(),
                getConfiguredMaxTokensForDisplay(),
                result.testcaseStats(),
                result.testcaseDurationMs()
        ));
        txtGeneratedCode.setText(AnalysisFormatter.renderMarkdownToHtml(
                result.sampleCode(),
                getConfiguredMaxTokensForDisplay(),
                result.sampleCodeStats(),
                result.sampleCodeDurationMs()
        ));

        String title = AnalysisFormatter.extractStringSafe(result.analysisJson(), "title");
        if (!title.startsWith("[Không")) {
            txtProblemName.setText(title);
        }
        String problemCode = AnalysisFormatter.extractStringSafe(result.analysisJson(), "problem_code");
        if (!problemCode.startsWith("[Không")) {
            txtProblemCode.setText(problemCode);
        }

        txtAnalysisResult.setCaretPosition(0);
        txtGeneratedTestcases.setCaretPosition(0);
        txtGeneratedCode.setCaretPosition(0);
    }

    private void setWorkflowBusy(boolean busy) {
        btnAnalyzeProblem.setEnabled(!busy);
        btnGenerateTestcases.setEnabled(!busy && lastAnalysisJson != null && !lastAnalysisJson.isBlank());
        btnGenerateSampleCode.setEnabled(!busy && lastAnalysisJson != null && !lastAnalysisJson.isBlank());
        btnRunAllInOne.setEnabled(!busy);
        if (busy) {
            btnRunStrengthCheck.setEnabled(false);
        } else {
            updateStrengthCheckAvailability();
        }
    }

    private void updateStrengthCheckAvailability() {
        btnRunStrengthCheck.setEnabled(lastGeneratedTestcasesRaw != null && !lastGeneratedTestcasesRaw.isBlank()
                && hasAnySampleCode());
    }

    private boolean hasAnySampleCode() {
        return (lastAiGeneratedCodeRaw != null && !lastAiGeneratedCodeRaw.isBlank())
                || (lastManualCodeRaw != null && !lastManualCodeRaw.isBlank());
    }

    private CodeSourceSelection chooseCodeSourceForJudge() {
        boolean hasAiCode = lastAiGeneratedCodeRaw != null && !lastAiGeneratedCodeRaw.isBlank();
        boolean hasManualCode = lastManualCodeRaw != null && !lastManualCodeRaw.isBlank();

        if (hasAiCode && hasManualCode) {
            String[] options = {"Code nhập thủ công", "Code AI", "Hủy"};
            int choice = JOptionPane.showOptionDialog(this,
                    "Bạn muốn dùng nguồn code nào để kiểm tra độ mạnh?",
                    "Chọn nguồn code",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]);
            if (choice == JOptionPane.CLOSED_OPTION || choice == 2) {
                return null;
            }
            if (choice == 0) {
                return new CodeSourceSelection("Nhập thủ công", "MANUAL_INPUT", lastManualCodeRaw, lastManualSampleCodeLanguage);
            }
            return new CodeSourceSelection("AI", "AI_GENERATED", lastAiGeneratedCodeRaw, lastAiSampleCodeLanguage);
        }

        if (hasManualCode) {
            return new CodeSourceSelection("Nhập thủ công", "MANUAL_INPUT", lastManualCodeRaw, lastManualSampleCodeLanguage);
        }
        if (hasAiCode) {
            return new CodeSourceSelection("AI", "AI_GENERATED", lastAiGeneratedCodeRaw, lastAiSampleCodeLanguage);
        }
        return null;
    }

    private void runStrengthCheckAction() {
        String testcasesText = lastGeneratedTestcasesRaw;
        CodeSourceSelection codeSelection = chooseCodeSourceForJudge();
        if (codeSelection == null) {
            return;
        }
        String codeText = codeSelection.rawCode();

        if (testcasesText == null || testcasesText.isBlank() || codeText == null || codeText.isBlank()) {
            JOptionPane.showMessageDialog(this, "Cần sinh cả Testcase và Code mẫu trước khi chấm.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Service.LocalJudgeService judgeService = new Service.LocalJudgeService();
        java.util.List<Service.LocalJudgeService.Testcase> testcases = judgeService.parseTestcases(testcasesText);
        if (testcases.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy Testcase nào hợp lệ trong tab Testcase.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String[] options = {"Chạy Code AC", "Chạy Code TLE", "Chạy Code WA"};
        int choice = JOptionPane.showOptionDialog(this,
                "Bạn muốn chấm điểm phiên bản Code nào?",
                "Chọn Code để chấm",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (choice == JOptionPane.CLOSED_OPTION) return;

        String type = choice == 0 ? "AC" : (choice == 1 ? "TLE" : "WA");
        String code = judgeService.extractCode(codeText, type);

        if (code == null || code.isBlank()) {
            JOptionPane.showMessageDialog(this, "Không tìm thấy mã nguồn " + type + " trong tab Code mẫu.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String language = codeSelection.language();
        if (language == null) {
            language = code.contains("public class") ? "Java" : "C++";
        }
        final String selectedLanguage = language;
        final String selectedCodeSource = codeSelection.label();

        btnRunStrengthCheck.setEnabled(false);

        SwingWorker<java.util.List<Service.LocalJudgeService.JudgeResult>, Void> worker = new SwingWorker<>() {
            @Override
            protected java.util.List<Service.LocalJudgeService.JudgeResult> doInBackground() throws Exception {
                return judgeService.judgeCode(code, selectedLanguage, testcases);
            }

            @Override
            protected void done() {
                try {
                    java.util.List<Service.LocalJudgeService.JudgeResult> results = get();
                    StringBuilder sb = new StringBuilder();
                    sb.append("BÁO CÁO CHẤM BÀI (").append(type).append(" CODE - ").append(selectedLanguage)
                            .append(" - ").append(selectedCodeSource).append(")\n");
                    sb.append("========================================\n");
                    
                    int passed = 0;
                    for (Service.LocalJudgeService.JudgeResult r : results) {
                        sb.append(r.testcaseName).append(": ").append(r.status);
                        if (r.timeMs > 0) sb.append(" (").append(r.timeMs).append("ms)");
                        sb.append("\n");
                        if (!"AC".equals(r.status)) {
                            sb.append("Chi tiết: ").append(r.detail).append("\n");
                        } else {
                            passed++;
                        }
                        sb.append("----------------------------------------\n");
                    }
                    sb.append("Tổng kết: ").append(passed).append("/").append(results.size()).append(" Passed\n");
                    sb.append("\n\nCODE ĐÃ CHẠY\n");
                    sb.append("========================================\n");
                    sb.append(code);
                    sb.append("\n");
                    
                    JTextArea ta = new JTextArea(sb.toString());
                    ta.setEditable(false);
                    ta.setFont(new Font("Consolas", Font.PLAIN, 14));
                    JScrollPane scroll = new JScrollPane(ta);
                    scroll.setPreferredSize(new Dimension(600, 400));
                    
                    JOptionPane.showMessageDialog(MainUI.this, scroll, "Kết quả Local Judge", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MainUI.this, "Lỗi chạy Judge: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnRunStrengthCheck.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private AIService createConfiguredAIService() {
        String projectId = txtGoogleProjectId.getText().trim();
        String location = defaultIfBlank(txtGoogleLocation.getText().trim(), AIService.DEFAULT_LOCATION);
        String authType = String.valueOf(cboGoogleAuthType.getSelectedItem());
        String credential = txtGoogleCredential.getText().trim();
        String serviceAccountJsonPath = txtServiceAccountJsonPath.getText().trim();
        String aiModel = defaultIfBlank(txtAIModel.getText().trim(), AIService.DEFAULT_MODEL);
        String aiMaxTokens = defaultIfBlank(txtAIMaxTokens.getText().trim(), "16384");
        String aiTimeout = defaultIfBlank(txtAITimeout.getText().trim(), "90");

        if (AIService.AUTH_SERVICE_ACCOUNT_JSON.equals(authType) && credential.isBlank()) {
            credential = readServiceAccountJsonFromPath(serviceAccountJsonPath);
            txtGoogleCredential.setText(credential);
        }

        txtGoogleLocation.setText(location);
        txtAIModel.setText(aiModel);
        txtAIMaxTokens.setText(aiMaxTokens);
        txtAITimeout.setText(aiTimeout);

        return new AIService(
                projectId,
                location,
                aiModel,
                authType,
                credential,
                parsePositiveInt(aiMaxTokens, "AI Max Tokens"),
                parsePositiveInt(aiTimeout, "AI Timeout")
        );
    }

    private int getConfiguredMaxTokensForDisplay() {
        try {
            return Integer.parseInt(txtAIMaxTokens.getText().trim());
        } catch (Exception e) {
            return 16384;
        }
    }

    private void appendAnalysisLog(String message) {
        String current = txtAnalysisResult.getText();
        if (current == null) current = "";
        if (current.contains("<body>")) {
            current = current.replace("</body>", "<br/><i>" + message + "</i></body>");
            txtAnalysisResult.setText(current);
        } else {
            txtAnalysisResult.setText("<html><body><i>" + message + "</i></body></html>");
        }
    }

    private void appendTestcaseLog(String message) {
        appendLog(txtGeneratedTestcases, message);
    }

    private void appendCodeLog(String message) {
        appendLog(txtGeneratedCode, message);
    }

    private Timer startEditorElapsedTimer(JEditorPane editorPane, String message) {
        long startedAt = System.currentTimeMillis();
        Timer timer = new Timer(1000, e -> editorPane.setText(buildElapsedHtml(message, startedAt)));
        editorPane.setText(buildElapsedHtml(message, startedAt));
        timer.start();
        return timer;
    }

    private Timer startTextAreaElapsedTimer(JTextArea textArea, String message) {
        long startedAt = System.currentTimeMillis();
        Timer timer = new Timer(1000, e -> textArea.setText(buildElapsedText(message, startedAt)));
        textArea.setText(buildElapsedText(message, startedAt));
        timer.start();
        return timer;
    }

    private Timer startAiStatusElapsedTimer(String message) {
        long startedAt = System.currentTimeMillis();
        Timer timer = new Timer(1000, e -> setAiStatus(message + " - " + formatElapsedSince(startedAt), false));
        setAiStatus(message + " - " + formatElapsedSince(startedAt), false);
        timer.start();
        return timer;
    }

    private String buildElapsedHtml(String message, long startedAt) {
        return "<html><body><b>" + escapeHtml(message).replace("\n", "<br/>") + "</b><br/>"
                + "<span>Thời gian chạy API: " + formatElapsedSince(startedAt) + "</span></body></html>";
    }

    private String buildElapsedText(String message, long startedAt) {
        return message + "\nThời gian chạy API: " + formatElapsedSince(startedAt);
    }

    private void showElapsedProgressInTab(int tabIndex, String message, long phaseStartedAt, long totalStartedAt) {
        resultTabs.setSelectedIndex(tabIndex);
        String fullMessage = message + "\nTổng thời gian All in one: " + formatElapsedSince(totalStartedAt);
        String html = buildElapsedHtml(fullMessage, phaseStartedAt);
        if (tabIndex == 0) {
            txtAnalysisResult.setText(html);
        } else if (tabIndex == 1) {
            txtGeneratedTestcases.setText(html);
        } else {
            txtGeneratedCode.setText(html);
        }
    }

    private String formatElapsedSince(long startedAt) {
        long elapsedMs = Math.max(0, System.currentTimeMillis() - startedAt);
        if (elapsedMs < 1000) {
            return elapsedMs + " ms";
        }
        return String.format("%.1f giây (%d ms)", elapsedMs / 1000.0, elapsedMs);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void appendLog(JEditorPane editorPane, String message) {
        String current = editorPane.getText();
        String safeMessage = escapeHtml(message)
                .replace("\n", "<br/>");

        if (current != null && current.contains("</body>")) {
            editorPane.setText(current.replace("</body>", "<br/><i>" + safeMessage + "</i></body>"));
        } else {
            editorPane.setText("<html><body><i>" + safeMessage + "</i></body></html>");
        }
    }

    private JPanel create_Cau_hinh_Layout() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 1;

        txtDbUrl = new JTextField(DbService.DEFAULT_DB_URL);
        txtGoogleProjectId = new JTextField();
        txtGoogleLocation = new JTextField(AIService.DEFAULT_LOCATION);
        cboGoogleAuthType = new JComboBox<>(new String[]{
                AIService.AUTH_API_KEY,
                AIService.AUTH_ACCESS_TOKEN,
                AIService.AUTH_SERVICE_ACCOUNT_JSON
        });
        txtGoogleCredential = new JTextArea(5, 30);
        txtGoogleCredential.setLineWrap(true);
        txtGoogleCredential.setWrapStyleWord(true);
        txtServiceAccountJsonPath = new JTextField();
        txtAIModel = new JTextField(AIService.DEFAULT_MODEL);
        txtAIMaxTokens = new JTextField("16384");
        txtAITimeout = new JTextField("90");

        int row = 0;
        addRow(panel, gbc, row++, "SQLite URL", txtDbUrl);
        addRow(panel, gbc, row++, "Google Project ID", txtGoogleProjectId);
        addRow(panel, gbc, row++, "Google Location", txtGoogleLocation);
        addRow(panel, gbc, row++, "Google Auth Type", cboGoogleAuthType);
        addRow(panel, gbc, row++, "Google Credential", new JScrollPane(txtGoogleCredential));
        addRow(panel, gbc, row++, "Service Account JSON Path", txtServiceAccountJsonPath);
        JButton btnLoadServiceAccountJson = new JButton("Nạp service account JSON");
        btnLoadServiceAccountJson.addActionListener(e -> loadServiceAccountJson());
        gbc.gridwidth = 1;
        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.weightx = 1;
        panel.add(btnLoadServiceAccountJson, gbc);
        addRow(panel, gbc, row++, "AI Model", txtAIModel);
        addRow(panel, gbc, row++, "AI Max Tokens", txtAIMaxTokens);
        addRow(panel, gbc, row++, "AI Timeout", txtAITimeout);

        JButton btnSave = new JButton("Lưu cấu hình");
        btnSave.addActionListener(e -> saveConfiguration(btnSave));

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        panel.add(btnSave, gbc);
        gbc.gridy++;
        gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);
        return panel;
    }

    private void loadServiceAccountJson() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Chọn file service account JSON");
        int result = fileChooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File jsonFile = fileChooser.getSelectedFile();
        try {
            String json = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);
            String projectId = AIService.extractOptionalJsonString(json, "project_id", "");

            cboGoogleAuthType.setSelectedItem(AIService.AUTH_SERVICE_ACCOUNT_JSON);
            txtGoogleCredential.setText(json);
            txtServiceAccountJsonPath.setText(jsonFile.getAbsolutePath());
            if (!projectId.isBlank()) {
                txtGoogleProjectId.setText(projectId);
            }

            JOptionPane.showMessageDialog(
                    this,
                    "Đã nạp service account JSON.\nProject ID: " + (projectId.isBlank() ? "(không tìm thấy)" : projectId),
                    "Nạp cấu hình AI",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "Không đọc được file JSON:\n" + e.getMessage(),
                    "Lỗi nạp JSON",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void saveConfiguration(JButton btnSave) {
        String dbUrl = txtDbUrl.getText().trim();
        String projectId = txtGoogleProjectId.getText().trim();
        String location = txtGoogleLocation.getText().trim();
        String authType = String.valueOf(cboGoogleAuthType.getSelectedItem());
        String credential = txtGoogleCredential.getText().trim();
        String serviceAccountJsonPath = txtServiceAccountJsonPath.getText().trim();
        String aiModel = defaultIfBlank(txtAIModel.getText().trim(), AIService.DEFAULT_MODEL);
        String aiMaxTokens = defaultIfBlank(txtAIMaxTokens.getText().trim(), "4096");
        String aiTimeout = defaultIfBlank(txtAITimeout.getText().trim(), "90");

        txtAIModel.setText(aiModel);
        txtAIMaxTokens.setText(aiMaxTokens);
        txtAITimeout.setText(aiTimeout);

        int maxTokens;
        int timeoutSeconds;
        try {
            maxTokens = parsePositiveInt(aiMaxTokens, "AI Max Tokens");
            timeoutSeconds = parsePositiveInt(aiTimeout, "AI Timeout");
            if (AIService.AUTH_SERVICE_ACCOUNT_JSON.equals(authType) && credential.isBlank()) {
                credential = readServiceAccountJsonFromPath(serviceAccountJsonPath);
                txtGoogleCredential.setText(credential);
            }
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cấu hình không hợp lệ", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String finalCredential = credential;

        btnSave.setEnabled(false);
        setDbConnecting();
        Timer apiTimer = startAiStatusElapsedTimer("Đang kiểm tra Google API");

        SwingWorker<SaveConfigResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SaveConfigResult doInBackground() {
                DbService.ConnectionTestResult dbResult = new DbService(dbUrl).testConnection();
                if (!dbResult.isSuccess()) {
                    return new SaveConfigResult(dbResult, new AIService.AITestResult(false, "Chưa test AI vì DB lỗi."));
                }

                try {
                    saveAppConfig(dbUrl, projectId, location, authType, finalCredential, serviceAccountJsonPath, aiModel, aiMaxTokens, aiTimeout);
                } catch (IOException ex) {
                    return new SaveConfigResult(
                            dbResult,
                            new AIService.AITestResult(false, "Không lưu được cấu hình: " + ex.getMessage())
                    );
                }

                if (finalCredential.isBlank()) {
                    return new SaveConfigResult(
                            dbResult,
                            new AIService.AITestResult(false, "Chưa nhập Google credential.")
                    );
                }

                AIService aiService = new AIService(
                        projectId,
                        location,
                        aiModel,
                        authType,
                        finalCredential,
                        maxTokens,
                        timeoutSeconds
                );
                return new SaveConfigResult(dbResult, aiService.testConnection());
            }

            @Override
            protected void done() {
                apiTimer.stop();
                btnSave.setEnabled(true);
                try {
                    SaveConfigResult result = get();
                    updateStatusAfterSave(dbUrl, projectId, location, authType, aiModel, aiMaxTokens, aiTimeout, result);
                } catch (Exception ex) {
                    setDbFailed(ex.getMessage());
                    setAiFailed();
                }
            }
        };
        worker.execute();
    }

    private JPanel create_Lich_su_Layout() {
        historyCardLayout = new CardLayout();
        historyCards = new JPanel(historyCardLayout);
        historyCards.add(createHistoryListPanel(), "list");
        historyCards.add(createHistoryDetailPanel(), "detail");
        return historyCards;
    }

    private JPanel createHistoryListPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        historyModel = new DefaultListModel<>();
        historyList = new JList<>(historyModel);
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyList.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        historyList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DbService.SavedProblemSummary selected = historyList.getSelectedValue();
                if (selected != null) {
                    previewHistoryProblem(selected.id());
                }
            }
        });
        historyList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedHistoryProblem();
                }
            }
        });

        historyListMessageArea = new JTextArea("Bấm Làm mới để tải các đề bài đã lưu.");
        historyListMessageArea.setEditable(false);
        historyListMessageArea.setLineWrap(true);
        historyListMessageArea.setWrapStyleWord(true);
        historyListMessageArea.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton btnRefresh = new JButton("Làm mới");
        JButton btnOpen = new JButton("Mở đề bài");
        btnRefresh.addActionListener(e -> refreshHistoryList());
        btnOpen.addActionListener(e -> openSelectedHistoryProblem());
        toolbar.add(btnRefresh);
        toolbar.add(btnOpen);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(historyList),
                new JScrollPane(historyListMessageArea)
        );
        splitPane.setResizeWeight(0.34);
        splitPane.setDividerLocation(360);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createHistoryDetailPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JButton btnBack = new JButton("Quay lại lịch sử");
        btnBack.addActionListener(e -> historyCardLayout.show(historyCards, "list"));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.add(btnBack);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.add(createHistoryProblemInfoPanel(), BorderLayout.NORTH);
        content.setPreferredSize(new Dimension(1500, 1180));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);
        splitPane.setDividerLocation(540);
        splitPane.setContinuousLayout(true);
        splitPane.setOneTouchExpandable(true);
        splitPane.setTopComponent(createHistoryStatementPanel());
        splitPane.setBottomComponent(createHistoryResultTabs());
        splitPane.setMinimumSize(new Dimension(1100, 980));
        splitPane.setPreferredSize(new Dimension(1500, 1040));
        content.add(splitPane, BorderLayout.CENTER);

        JScrollPane pageScroll = new JScrollPane(content);
        pageScroll.setBorder(null);
        pageScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        pageScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        pageScroll.getVerticalScrollBar().setUnitIncrement(16);
        pageScroll.getHorizontalScrollBar().setUnitIncrement(16);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(pageScroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createHistoryProblemInfoPanel() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        historyCodeField = createReadonlyField();
        historyNameField = createReadonlyField();
        historySourceField = createReadonlyField();
        historyCreatedAtField = createReadonlyField();

        addHistoryInfoCell(formPanel, gbc, 0, 0, "Mã bài", historyCodeField, 0.22);
        addHistoryInfoCell(formPanel, gbc, 2, 0, "Tên bài", historyNameField, 0.78);
        addHistoryInfoCell(formPanel, gbc, 0, 1, "Nguồn", historySourceField, 0.22);
        addHistoryInfoCell(formPanel, gbc, 2, 1, "Ngày lưu", historyCreatedAtField, 0.78);
        return formPanel;
    }

    private void addHistoryInfoCell(
            JPanel panel,
            GridBagConstraints gbc,
            int x,
            int y,
            String label,
            JComponent field,
            double weight
    ) {
        gbc.gridwidth = 1;
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = x + 1;
        gbc.weightx = weight;
        panel.add(field, gbc);
    }

    private JTextField createReadonlyField() {
        JTextField field = new JTextField();
        field.setEditable(false);
        return field;
    }

    private JScrollPane createHistoryStatementPanel() {
        historyStatementArea = new JTextArea(22, 120);
        historyStatementArea.setEditable(false);
        historyStatementArea.setLineWrap(true);
        historyStatementArea.setWrapStyleWord(true);
        historyStatementArea.setFont(new Font(Font.DIALOG, Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(historyStatementArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Nội dung đề bài"));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setMinimumSize(new Dimension(1100, 430));
        scrollPane.setPreferredSize(new Dimension(1500, 520));
        return scrollPane;
    }

    private JTabbedPane createHistoryResultTabs() {
        JTabbedPane tabs = new JTabbedPane();

        historyAnalysisPane = new JEditorPane();
        historyAnalysisPane.setContentType("text/html");
        historyAnalysisPane.setEditable(false);
        historyAnalysisPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        historyTestcasesArea = createReadonlyTextArea();
        historySampleCodesArea = createReadonlyTextArea();
        historyArtifactsArea = createReadonlyTextArea();
        historySampleCodesArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        historyArtifactsArea.setFont(new Font("Consolas", Font.PLAIN, 13));

        tabs.addTab("Phân tích", createAlwaysScrollablePane(historyAnalysisPane));
        tabs.addTab("Testcase", createAlwaysScrollablePane(historyTestcasesArea));
        tabs.addTab("Code mẫu", createAlwaysScrollablePane(historySampleCodesArea));
        tabs.addTab("Generator/Checker", createAlwaysScrollablePane(historyArtifactsArea));
        tabs.setMinimumSize(new Dimension(1100, 430));
        tabs.setPreferredSize(new Dimension(1500, 520));
        return tabs;
    }

    private JScrollPane createAlwaysScrollablePane(Component component) {
        JScrollPane scrollPane = new JScrollPane(component);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private void refreshHistoryList() {
        if (historyModel == null || txtDbUrl == null) {
            return;
        }

        try {
            java.util.List<DbService.SavedProblemSummary> problems = new DbService(txtDbUrl.getText().trim()).listSavedProblems();
            historyModel.clear();
            for (DbService.SavedProblemSummary problem : problems) {
                historyModel.addElement(problem);
            }

            if (problems.isEmpty()) {
                historyListMessageArea.setText("Chưa có đề bài đã lưu.");
            } else {
                historyList.setSelectedIndex(0);
            }
        } catch (Exception e) {
            historyListMessageArea.setText("Không tải được lịch sử:\n" + e.getMessage());
        }
    }

    private void previewHistoryProblem(long problemId) {
        if (historyListMessageArea == null || txtDbUrl == null) {
            return;
        }

        try {
            DbService.SavedProblemDetail detail = new DbService(txtDbUrl.getText().trim()).loadProblemDetail(problemId);
            historyListMessageArea.setText(buildHistoryPreviewText(detail));
            historyListMessageArea.setCaretPosition(0);
        } catch (Exception e) {
            historyListMessageArea.setText("Không tải được preview:\n" + e.getMessage());
        }
    }

    private String buildHistoryPreviewText(DbService.SavedProblemDetail detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(detail.id()).append("\n");
        sb.append("Mã bài: ").append(defaultIfBlank(detail.code(), "-")).append("\n");
        sb.append("Tên bài: ").append(detail.name()).append("\n");
        sb.append("Nguồn: ").append(defaultIfBlank(detail.source(), "-")).append("\n");
        sb.append("Ngày lưu: ").append(detail.createdAt()).append("\n");
        sb.append("Testcase: ").append(detail.testcases().size()).append("\n");
        sb.append("Code mẫu: ").append(detail.sampleCodes().size()).append("\n");
        sb.append("Phân tích AI: ").append(detail.latestAnalysisJson() == null || detail.latestAnalysisJson().isBlank() ? "Không" : "Có").append("\n");
        sb.append("\nNỘI DUNG ĐỀ BÀI\n");
        sb.append("========================================\n");
        sb.append(limitPreviewText(detail.statementText(), 2200));
        sb.append("\n\nBấm Mở đề bài hoặc double-click để xem đầy đủ.");
        return sb.toString();
    }

    private String limitPreviewText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return defaultIfBlank(text, "");
        }
        return text.substring(0, maxLength).trim() + "\n...\n[Đã rút gọn preview]";
    }

    private void openSelectedHistoryProblem() {
        DbService.SavedProblemSummary selected = historyList.getSelectedValue();
        if (selected == null) {
            historyListMessageArea.setText("Chọn một đề bài trong danh sách trước.");
            return;
        }
        loadHistoryDetail(selected.id());
    }

    private void loadHistoryDetail(long problemId) {
        if (historyCards == null || txtDbUrl == null) {
            return;
        }

        try {
            DbService.SavedProblemDetail detail = new DbService(txtDbUrl.getText().trim()).loadProblemDetail(problemId);
            fillHistoryDetailView(detail);
            historyCardLayout.show(historyCards, "detail");
        } catch (Exception e) {
            historyListMessageArea.setText("Không tải được chi tiết đề bài:\n" + e.getMessage());
        }
    }

    private void fillHistoryDetailView(DbService.SavedProblemDetail detail) {
        historyCodeField.setText(defaultIfBlank(detail.code(), "-"));
        historyNameField.setText(detail.name());
        historySourceField.setText(defaultIfBlank(detail.source(), "-"));
        historyCreatedAtField.setText(detail.createdAt());
        historyStatementArea.setText(detail.statementText());
        historyStatementArea.setCaretPosition(0);

        if (detail.latestAnalysisJson() == null || detail.latestAnalysisJson().isBlank()) {
            historyAnalysisPane.setText("<html><body><i>Chưa lưu phân tích AI.</i></body></html>");
        } else {
            historyAnalysisPane.setText(AnalysisFormatter.formatAnalysisResult(detail.latestAnalysisJson(), getConfiguredMaxTokensForDisplay()));
        }
        historyAnalysisPane.setCaretPosition(0);

        historyTestcasesArea.setText(HistoryFormatter.formatSavedTestcases(detail.testcases()));
        historyTestcasesArea.setCaretPosition(0);
        historySampleCodesArea.setText(HistoryFormatter.formatSavedSampleCodes(detail.sampleCodes()));
        historySampleCodesArea.setCaretPosition(0);
        historyArtifactsArea.setText(HistoryFormatter.formatSavedTestcaseArtifact(detail.testcaseArtifact()));
        historyArtifactsArea.setCaretPosition(0);
    }

    private void loadServiceAccountJsonFromSavedPath(String path) {
        if (path == null || path.isBlank()) {
            txtGoogleCredential.setText("");
            return;
        }

        try {
            txtGoogleCredential.setText(readServiceAccountJsonFromPath(path));
        } catch (IllegalArgumentException e) {
            txtGoogleCredential.setText("");
            setAiFailed("Không đọc được service account JSON đã lưu");
        }
    }

    private String readServiceAccountJsonFromPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Chưa chọn đường dẫn service account JSON.");
        }

        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Không đọc được service account JSON: " + e.getMessage());
        }
    }

    private void updateStatusAfterSave(
            String dbUrl,
            String projectId,
            String location,
            String authType,
            String aiModel,
            String aiMaxTokens,
            String aiTimeout,
            SaveConfigResult result
    ) {
        if (result.dbResult().isSuccess()) {
            setDbConnected();
        } else {
            setDbFailed(result.dbResult().getMessage());
            showDbErrorDialog(result.dbResult().getMessage());
            return;
        }

        if (result.aiResult().isSuccess()) {
            setAiConnected();
        } else {
            setAiFailed(result.aiResult().getMessage());
        }

        JOptionPane.showMessageDialog(
                this,
                "Đã kiểm tra DB và lưu cấu hình thành công.\n" +
                        "SQLite URL: " + dbUrl + "\n" +
                        "Google Project ID: " + projectId + "\n" +
                        "Google Location: " + location + "\n" +
                        "Google Auth Type: " + authType + "\n" +
                        "AI Model: " + aiModel + "\n" +
                        "AI Max Tokens: " + aiMaxTokens + "\n" +
                        "AI Timeout: " + aiTimeout + "\n\n" +
                        "Trạng thái AI: " + result.aiResult().getMessage(),
                "Thông tin cấu hình",
                result.aiResult().isSuccess() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE
        );
    }

    private int parsePositiveInt(String value, String fieldName) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException("Value must be positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " phải là số nguyên dương.");
        }
    }

    private void loadAppConfig() {
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
            properties.load(inputStream);
        } catch (IOException e) {
            setDbFailed("Không đọc được cấu hình đã lưu");
            return;
        }

        txtDbUrl.setText(properties.getProperty(CONFIG_DB_URL, DbService.DEFAULT_DB_URL));
        txtGoogleProjectId.setText(properties.getProperty(CONFIG_GOOGLE_PROJECT_ID, ""));
        txtGoogleLocation.setText(defaultIfBlank(
                properties.getProperty(CONFIG_GOOGLE_LOCATION, AIService.DEFAULT_LOCATION),
                AIService.DEFAULT_LOCATION
        ));
        cboGoogleAuthType.setSelectedItem(properties.getProperty(CONFIG_GOOGLE_AUTH_TYPE, AIService.AUTH_API_KEY));
        txtServiceAccountJsonPath.setText(properties.getProperty(CONFIG_GOOGLE_SERVICE_ACCOUNT_JSON_PATH, ""));
        if (AIService.AUTH_SERVICE_ACCOUNT_JSON.equals(cboGoogleAuthType.getSelectedItem())) {
            loadServiceAccountJsonFromSavedPath(txtServiceAccountJsonPath.getText().trim());
        } else {
            txtGoogleCredential.setText(properties.getProperty(CONFIG_GOOGLE_CREDENTIAL, ""));
        }
        txtAIModel.setText(defaultIfBlank(properties.getProperty(CONFIG_AI_MODEL, AIService.DEFAULT_MODEL), AIService.DEFAULT_MODEL));
        txtAIMaxTokens.setText(defaultIfBlank(properties.getProperty(CONFIG_AI_MAX_TOKENS, "16384"), "16384"));
        txtAITimeout.setText(defaultIfBlank(properties.getProperty(CONFIG_AI_TIMEOUT, txtAITimeout.getText()), "90"));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void saveAppConfig(
            String dbUrl,
            String projectId,
            String location,
            String authType,
            String credential,
            String serviceAccountJsonPath,
            String aiModel,
            String aiMaxTokens,
            String aiTimeout
    ) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(CONFIG_DB_URL, dbUrl);
        properties.setProperty(CONFIG_GOOGLE_PROJECT_ID, projectId);
        properties.setProperty(CONFIG_GOOGLE_LOCATION, location);
        properties.setProperty(CONFIG_GOOGLE_AUTH_TYPE, authType);
        if (AIService.AUTH_SERVICE_ACCOUNT_JSON.equals(authType)) {
            properties.setProperty(CONFIG_GOOGLE_SERVICE_ACCOUNT_JSON_PATH, serviceAccountJsonPath);
            properties.remove(CONFIG_GOOGLE_CREDENTIAL);
        } else {
            properties.setProperty(CONFIG_GOOGLE_CREDENTIAL, credential);
            properties.remove(CONFIG_GOOGLE_SERVICE_ACCOUNT_JSON_PATH);
        }
        properties.setProperty(CONFIG_AI_MODEL, aiModel);
        properties.setProperty(CONFIG_AI_MAX_TOKENS, aiMaxTokens);
        properties.setProperty(CONFIG_AI_TIMEOUT, aiTimeout);

        Path parent = CONFIG_PATH.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "JavaICPC app configuration");
        }
    }

    private void testConfiguredConnectionsOnStartup() {
        String dbUrl = txtDbUrl.getText().trim();
        String projectId = txtGoogleProjectId.getText().trim();
        String location = defaultIfBlank(txtGoogleLocation.getText().trim(), AIService.DEFAULT_LOCATION);
        String authType = String.valueOf(cboGoogleAuthType.getSelectedItem());
        String credential = txtGoogleCredential.getText().trim();
        String serviceAccountJsonPath = txtServiceAccountJsonPath.getText().trim();
        String aiModel = defaultIfBlank(txtAIModel.getText().trim(), AIService.DEFAULT_MODEL);
        String aiMaxTokens = defaultIfBlank(txtAIMaxTokens.getText().trim(), "16384");
        String aiTimeout = defaultIfBlank(txtAITimeout.getText().trim(), "90");

        txtGoogleLocation.setText(location);
        txtAIModel.setText(aiModel);
        txtAIMaxTokens.setText(aiMaxTokens);
        txtAITimeout.setText(aiTimeout);
        setDbConnecting();
        Timer apiTimer = startAiStatusElapsedTimer("Đang kiểm tra Google API");

        SwingWorker<SaveConfigResult, Void> worker = new SwingWorker<>() {
            @Override
            protected SaveConfigResult doInBackground() {
                DbService.ConnectionTestResult dbResult = dbUrl.isEmpty()
                        ? new DbService.ConnectionTestResult(false, "Chưa cấu hình SQLite URL")
                        : new DbService(dbUrl).testConnection();

                AIService.AITestResult aiResult;
                try {
                    int maxTokens = parsePositiveInt(aiMaxTokens, "AI Max Tokens");
                    int timeoutSeconds = parsePositiveInt(aiTimeout, "AI Timeout");
                    String finalCredential = credential;
                    if (AIService.AUTH_SERVICE_ACCOUNT_JSON.equals(authType) && finalCredential.isBlank()) {
                        finalCredential = readServiceAccountJsonFromPath(serviceAccountJsonPath);
                    }

                    if (finalCredential.isBlank()) {
                        aiResult = new AIService.AITestResult(false, "Chưa nhập Google credential.");
                    } else {
                        AIService aiService = new AIService(
                                projectId,
                                location,
                                aiModel,
                                authType,
                                finalCredential,
                                maxTokens,
                                timeoutSeconds
                        );
                        aiResult = aiService.testConnection();
                    }
                } catch (IllegalArgumentException e) {
                    aiResult = new AIService.AITestResult(false, e.getMessage());
                }

                return new SaveConfigResult(dbResult, aiResult);
            }

            @Override
            protected void done() {
                apiTimer.stop();
                try {
                    SaveConfigResult result = get();
                    if (result.dbResult().isSuccess()) {
                        setDbConnected();
                    } else {
                        setDbFailed(result.dbResult().getMessage());
                    }

                    if (result.aiResult().isSuccess()) {
                        setAiConnected();
                    } else {
                        setAiFailed(result.aiResult().getMessage());
                    }
                } catch (Exception e) {
                    setDbFailed(e.getMessage());
                    setAiFailed(e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int y, String label, JComponent field) {
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = y;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    public void setDbStatus(String message, boolean connected) {
        statusBarPanel.setDbStatus("DB: " + message, connected);
    }

    public void setAiStatus(String message, boolean connected) {
        statusBarPanel.setAiStatus("AI: " + message, connected);
    }

    public void setDbConnecting() {
        setDbStatus("Đang kết nối...", false);
    }

    public void setDbConnected() {
        setDbStatus("Đã kết nối", true);
    }

    public void setDbFailed() {
        setDbStatus("Kết nối thất bại", false);
    }

    public void setAiConnecting() {
        setAiStatus("Đang kết nối...", false);
    }

    public void setAiConnected() {
        setAiStatus("Đã kết nối", true);
    }

    public void setAiFailed() {
        setAiStatus("Kết nối thất bại", false);
    }

    public void setDbFailed(String message) {
        setDbStatus(message, false);
    }

    public void setAiFailed(String message) {
        setAiStatus(message, false);
    }

    private void showDbErrorDialog(String message) {
        JOptionPane.showMessageDialog(
                this,
                message,
                "Chi tiết lỗi DB",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private record AllInOneProgress(
            int tabIndex,
            String message
    ) {
    }

    private record AllInOneResult(
            String analysisJson,
            String analysisStats,
            long analysisDurationMs,
            String testcases,
            String testcaseStats,
            long testcaseDurationMs,
            String sampleCode,
            String sampleCodeStats,
            long sampleCodeDurationMs,
            String language
    ) {
    }

    private record CodeSourceSelection(
            String label,
            String source,
            String rawCode,
            String language
    ) {
    }

    private record SaveConfigResult(
            DbService.ConnectionTestResult dbResult,
            AIService.AITestResult aiResult
    ) {
    }
}

