# JavaICPC

JavaICPC là chương trình Java có giao diện, kết nối CSDL SQLite và tích hợp Google Vertex AI/Gemini để hỗ trợ nhập đề ICPC/IOI, phân tích đề, sinh testcase/checker, sinh hoặc nhập code mẫu AC/WA/TLE và kiểm tra độ mạnh testcase bằng Local Judge.

## Link public

Link public chứa mã nguồn, dữ liệu và tài nguyên của dự án:

- Mã nguồn: https://github.com/thanhtrung411/JavaICPC.git
- Báo cáo Word: https://dutudn-my.sharepoint.com/:w:/g/personal/102240063_sv1_dut_udn_vn/IQDzerPpNIYxQoT9Q9LYlshqAZjUhR2HMwEQpA0ofZhNCKI

## Ghi chú

Báo cáo Word trình bày chi tiết các nội dung:

- Phân công công việc.
- Hướng dẫn cài đặt.
- Hướng dẫn sử dụng.
- Kết quả chạy thử nghiệm.
- Hình ảnh minh chứng quá trình chạy chương trình.


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
