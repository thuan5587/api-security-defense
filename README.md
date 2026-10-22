# API Security Defense – Nhóm B11

## 1. Giới thiệu dự án

Dự án thuộc đề tài:

**Nghiên cứu, khai thác và phòng chống các lỗ hổng kiểm soát truy cập và xác thực trên hệ thống API RESTful.**

Nhóm sử dụng hệ thống OWASP crAPI làm môi trường thực hành nhằm khảo sát lỗ hổng bảo mật, triển khai biện pháp phòng chống và đánh giá hiệu quả thông qua kiểm thử trước và sau khi vá.

Repository môi trường lab ban đầu:

https://github.com/thuan5587/api-security-lab

Repository triển khai phòng chống:

https://github.com/thuan5587/api-security-defense

## 2. Phân công thành viên

| Thành viên | Nội dung phụ trách |
|---|---|
| Nguyễn Thanh Thuận | BOLA, kiểm tra quyền sở hữu đối tượng, ghi log bảo mật, kiểm thử |
| Phước Toàn | Broken Authentication, xác thực JWT, phòng chống OTP brute-force |

Hai phần được triển khai và đánh giá theo phạm vi công việc của từng thành viên.

## 3. Trạng thái thực hiện

### 3.1. Broken Object Level Authorization – BOLA

**Trạng thái: Đã triển khai và kiểm thử trong phạm vi endpoint được lựa chọn.**

Các công việc đã thực hiện:

- Phân tích lỗ hổng BOLA trên API lấy vị trí phương tiện.
- Xây dựng kịch bản Alice truy cập phương tiện của Bob.
- Ghi nhận phản hồi HTTP 200 và dữ liệu bị tiết lộ trước khi vá.
- Bổ sung kiểm tra quyền sở hữu đối tượng trong Java backend.
- Từ chối yêu cầu truy cập sai quyền sở hữu.
- Bổ sung log `BOLA_ACCESS_DENIED`.
- Triển khai Docker image `crapi-identity-bola-defense:v2`.
- Kiểm thử API bằng Postman.
- Chạy Unit Test với Gradle và JDK 17.
- Ghi nhận kết quả Before/After.

Endpoint đã được vá:

`GET /identity/api/v2/vehicle/{carId}/location`

Lưu ý: Chưa khẳng định toàn bộ các endpoint trong crAPI đã được khắc phục BOLA.

### 3.2. Broken Authentication

**Trạng thái: Chưa xác nhận kết quả triển khai trong repository này.**

Phần việc do Phước Toàn phụ trách, gồm:

- Kiểm tra chữ ký và các claims của JWT.
- Đánh giá cơ chế xác thực.
- Phòng chống thử OTP nhiều lần.
- Giới hạn số lần thử và cơ chế khóa tạm thời.
- Kiểm thử trước và sau khi áp dụng biện pháp phòng chống.

Trạng thái sẽ được cập nhật khi có mã nguồn và kết quả kiểm thử tương ứng.

## 4. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Hệ thống thực hành | OWASP crAPI |
| Backend | Java, Spring Boot |
| Build và Unit Test | Gradle, JDK 17 |
| Triển khai | Docker, Docker Compose |
| Kiểm thử API | Postman |
| Quản lý mã nguồn | Git, GitHub |
| Hệ điều hành thực hành | Windows, PowerShell |

## 5. Cấu trúc repository

```text
api-security-defense/
├── backend/
│   └── identity/
│       └── src/
├── docs/
│   └── defense/
│       ├── 01-baseline.md
│       ├── 02-bola-design.md
│       ├── 04-test-results.md
│       └── 05-before-after.md
├── docker-compose.bola.yml
├── .gitignore
└── README.md
```

Thư mục `evidence/bola/` được lưu cục bộ và không đưa lên GitHub công khai vì có thể chứa thông tin nhạy cảm.

## 6. Triển khai bản vá BOLA

### 6.1. Điều kiện chuẩn bị

- Docker Desktop đang hoạt động.
- Môi trường crAPI gốc đã được thiết lập.
- Có mã nguồn backend identity trong repository phòng chống.
- Sử dụng JDK 17 khi chạy Gradle trong môi trường thực hành.

Các lệnh dưới đây sử dụng đường dẫn của môi trường lab trên máy thực hiện. Khi triển khai ở máy khác, cần điều chỉnh đường dẫn tương ứng.

### 6.2. Build Docker image

Mở PowerShell:

```powershell
cd D:\api-security-defense\backend\identity

docker build -t crapi-identity-bola-defense:v2 .
```

### 6.3. Triển khai bản vá

```powershell
docker compose -f "D:\api-security-lab\crapi\deploy\docker\docker-compose.yml" -f "D:\api-security-defense\docker-compose.bola.yml" up -d --no-deps --no-build crapi-identity
```

Kiểm tra trạng thái:

```powershell
docker ps
```

Container `crapi-identity` cần ở trạng thái hoạt động và vượt qua health check.

## 7. Kiểm thử BOLA bằng Postman

API Gateway:

`http://localhost:8888`

Endpoint:

`GET /identity/api/v2/vehicle/{carId}/location`

Trong Postman, sử dụng Bearer Token của tài khoản đang kiểm thử.

Các kịch bản đã thực hiện:

| Kịch bản | Kết quả sau vá |
|---|---|
| Alice truy cập xe Bob | 404 Not Found |
| Alice truy cập xe Alice | 200 OK |
| Bob truy cập xe Bob | 200 OK |
| Không gửi Authorization | 401 Unauthorized |
| UUID hợp lệ nhưng không tồn tại | 404 Not Found |
| Mã phương tiện sai định dạng | 400 Bad Request |

Kết quả được ghi nhận trên môi trường lab đã triển khai bản vá.

## 8. Chạy Unit Test

Sử dụng PowerShell:

```powershell
cd D:\api-security-defense\backend\identity

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"

.\gradlew.bat test
```

Kết quả kiểm thử đã ghi nhận: Gradle hoàn thành quá trình chạy test thành công với JDK 17.

Đường dẫn JDK cần điều chỉnh nếu máy triển khai sử dụng vị trí cài đặt khác.

## 9. Kiểm tra log bảo mật

Khi phát hiện yêu cầu truy cập phương tiện không thuộc quyền sở hữu, backend ghi sự kiện:

`BOLA_ACCESS_DENIED`

Lệnh kiểm tra:

```powershell
docker logs crapi-identity --tail 200 2>&1 | Select-String "BOLA_ACCESS_DENIED"
```

Log bao gồm phương thức HTTP, mã đối tượng, ID người dùng và lý do từ chối.

Không công khai log chứa thông tin nội bộ nếu chưa được kiểm tra và xử lý phù hợp.

## 10. Rollback về backend crAPI gốc

Nếu cần khôi phục dịch vụ identity về image được khai báo trong cấu hình Compose gốc, chạy:

```powershell
docker compose -f "D:\api-security-lab\crapi\deploy\docker\docker-compose.yml" up -d --no-deps --no-build --force-recreate crapi-identity
```

Sau đó kiểm tra:

```powershell
docker ps
```

Lệnh này tái tạo dịch vụ `crapi-identity` theo cấu hình gốc mà không áp dụng file ghi đè BOLA.

**Lưu ý:** Cấu hình gốc là phiên bản có lỗ hổng được sử dụng trong môi trường lab. Chỉ rollback trong môi trường thực hành được kiểm soát, không sử dụng làm cấu hình vận hành an toàn.

## 11. Tài liệu báo cáo

Các tài liệu nằm trong `docs/defense/`:

- `01-baseline.md`: Khảo sát lỗ hổng BOLA trước khi vá.
- `02-bola-design.md`: Thiết kế và triển khai bản vá.
- `04-test-results.md`: Kết quả kiểm thử.
- `05-before-after.md`: So sánh trước và sau khi vá.

Tài liệu `03-auth-design.md` thuộc phạm vi Broken Authentication và sẽ được bổ sung khi thành viên phụ trách hoàn thành.

## 12. Minh chứng

Các file minh chứng BOLA được lưu cục bộ:

- `evidence/bola/BOLA-02-Before-Patch.png`
- `evidence/bola/BOLA-02-After-Patch.png`
- `evidence/bola/BOLA-Access-Denied-Log.txt`

Minh chứng gốc không được đưa lên repository công khai để tránh tiết lộ dữ liệu cá nhân hoặc thông tin nội bộ.

Khi nộp bài, các ảnh hoặc log cần được kiểm tra và che thông tin nhạy cảm nếu có.

## 13. Giới hạn và hướng phát triển

Phần BOLA hiện chỉ được kiểm chứng trên endpoint lấy vị trí phương tiện.

Các hướng phát triển gồm:

- Rà soát thêm các endpoint có tham số định danh đối tượng.
- Tăng độ bao phủ của kiểm thử tự động.
- Chuẩn hóa cơ chế phân quyền.
- Hoàn thiện tài liệu và kết quả Broken Authentication.
- Tích hợp Postman Collection và Environment mẫu không chứa thông tin bí mật.
- Bổ sung cơ chế giám sát và cảnh báo bảo mật.

## 14. Kết luận

Bản vá BOLA đã được triển khai và kiểm thử thành công đối với kịch bản Alice truy cập trái phép vị trí phương tiện của Bob.

Trước khi vá, API trả HTTP 200 và tiết lộ dữ liệu. Sau khi vá, API trả HTTP 404, không cung cấp dữ liệu vị trí và ghi log từ chối truy cập.

Kết quả minh họa vai trò của kiểm tra quyền sở hữu đối tượng trong việc phòng chống lỗ hổng BOLA trên REST API.