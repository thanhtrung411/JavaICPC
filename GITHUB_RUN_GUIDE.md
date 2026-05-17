# Hướng Dẫn Chạy JavaICPC

## Yêu cầu

- Cài JDK 17 hoặc mới hơn.
- Nếu muốn chấm code C++, cài `g++` và thêm vào `PATH`.
- Không cần cài thêm thư viện Java bên ngoài vì các file `.jar` cần thiết đã nằm trong thư mục `lib/`.

## Chạy nhanh trên Windows

Mở PowerShell hoặc Command Prompt tại thư mục dự án rồi chạy:

```bat
RUN_APP.bat
```

Script sẽ tự biên dịch source trong `src/` vào `out/run/` rồi mở chương trình.

## Chạy bằng PowerShell

```powershell
.\run_app.ps1
```

## Chạy thủ công

Biên dịch:

```powershell
javac -encoding UTF-8 -cp "lib/*" -d out\run src\Main.java src\UI\Main.java src\UI\MainUI.java src\UI\AnalysisFormatter.java src\UI\HistoryFormatter.java src\UI\ManualSampleCodeDialog.java src\UI\StatementTextFilter.java src\UI\StatusBarPanel.java src\Service\DbService.java src\Service\AIService.java src\Service\LocalJudgeService.java
```

Chạy:

```powershell
java -cp "out\run;lib/*" UI.Main
```

## Cấu hình lần đầu

1. Mở chương trình.
2. Vào tab `Cấu hình`.
3. Nhập SQLite URL, ví dụ:

```text
jdbc:sqlite:data/contest_judge_ai.db
```

4. Nhập thông tin Google Vertex AI/Gemini:
   - Google Project ID
   - Google Location, ví dụ `global`
   - Google Auth Type
   - Google Credential hoặc Service Account JSON
   - AI Model, ví dụ `gemini-2.5-flash`
5. Bấm `Lưu cấu hình`.

## Lưu ý khi đưa lên GitHub

- Không commit `data/app.properties` nếu trong đó có API key, access token hoặc đường dẫn service account riêng.
- Không commit file service account JSON thật.
- Không commit thư mục `out/` vì đây là thư mục build tự sinh.
