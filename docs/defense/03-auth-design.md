# 03 – THIẾT KẾ PHÒNG THỦ BROKEN AUTHENTICATION (JWT & OTP)

## 1. Giới thiệu

Tài liệu mô tả cơ chế phòng thủ Broken Authentication được triển khai trên hệ thống OWASP crAPI, tập trung vào hai thành phần: xác thực JSON Web Token (JWT) và bảo vệ mã xác thực một lần (OTP) trong quy trình khôi phục mật khẩu.

Mục tiêu là phân tích các biện pháp phòng thủ đã triển khai, xác định các lớp xử lý liên quan và tổng hợp bằng chứng kiểm thử trong môi trường lab.

Phạm vi tài liệu chỉ giới hạn ở các thành phần JWT và OTP đã khảo sát, không khẳng định toàn bộ hệ thống crAPI không còn lỗ hổng xác thực.

## 2. Môi trường và phạm vi triển khai

| Thành phần | Thông tin |
|---|---|
| Hệ thống | OWASP crAPI |
| Dịch vụ | crapi-identity |
| Ngôn ngữ | Java |
| Framework | Spring Boot, Spring Security |
| Công cụ kiểm thử | Postman, Gradle |
| Môi trường | Docker Compose |
| Repository | api-security-defense |
| Nhánh triển khai | feature/broken-auth-defense |

Các thành phần mã nguồn chính:

- `JwtProvider.java`: tạo và xác thực JWT.
- `JwtAuthTokenFilter.java`: trích xuất token và thiết lập ngữ cảnh xác thực.
- `WebSecurityConfig.java`: cấu hình chính sách bảo vệ endpoint.
- `OtpServiceImpl.java`: cấp phát, xác thực và vô hiệu hóa OTP.
- `OtpRepository.java`: truy xuất bản ghi OTP phục vụ kiểm tra trạng thái và xử lý đồng thời.

## 3. Thiết kế phòng thủ JWT

### 3.1. Nguy cơ bảo mật

Broken Authentication liên quan đến JWT có thể xuất hiện khi hệ thống không kiểm tra chữ ký, chấp nhận thuật toán không an toàn, xử lý sai thời hạn token hoặc cho phép truy cập tài nguyên mà không xác thực.

Trong phạm vi nghiên cứu, cơ chế phòng thủ tập trung vào xác thực chữ ký JWT và từ chối token không hợp lệ.

### 3.2. Cơ chế triển khai

Lớp `JwtProvider.java` sử dụng khóa RSA được cấu hình thông qua JWKS.

JWT được tạo bằng thuật toán RS256, bao gồm các thông tin như định danh người dùng (`sub`), thời điểm phát hành (`iat`), thời điểm hết hạn (`exp`) và vai trò.

Khi nhận token, hệ thống thực hiện:

1. Phân tích cấu trúc JWT.
2. Kiểm tra thuật toán ký phải là RS256.
3. Xác thực chữ ký bằng RSA Public Key.
4. Kiểm tra thời hạn `exp` nếu claim này hiện diện.
5. Kiểm tra `nbf` nếu được khai báo.
6. Từ chối token không đáp ứng điều kiện xác thực.

Cần lưu ý: mã nguồn hiện tại cho phép một số token không có `exp`, đồng thời có cơ chế tạo API Key không chứa `exp`. Vì vậy, không mô tả hệ thống là bắt buộc mọi token đều phải có thời hạn hết hạn.

### 3.3. Luồng xác thực JWT

**Bước 1:** Client gửi HTTP request đến endpoint được bảo vệ với header Authorization chứa Bearer Token.

**Bước 2:** `JwtAuthTokenFilter` lấy token từ request.

**Bước 3:** `JwtProvider.validateJwtToken()` kiểm tra thuật toán, chữ ký và các điều kiện thời gian được hỗ trợ.

**Bước 4:** Nếu token hợp lệ, hệ thống lấy định danh người dùng, tải thông tin tài khoản và thiết lập `SecurityContext`.

**Bước 5:** Spring Security tiếp tục kiểm tra chính sách truy cập endpoint.

**Bước 6:** Nếu xác thực không hợp lệ, yêu cầu không được cấp quyền truy cập theo chính sách bảo mật.

### 3.4. Cấu hình bảo vệ endpoint

`WebSecurityConfig.java` áp dụng chính sách xác thực cho các endpoint yêu cầu đăng nhập, trong đó có:

`/identity/api/v2/user/dashboard`

Một số endpoint phục vụ đăng nhập hoặc kiểm tra sức khỏe hệ thống được cấu hình cho phép truy cập không cần xác thực.

Ứng dụng sử dụng cơ chế phiên không trạng thái (stateless).

### 3.5. Security Logging

Các sự kiện JWT được ghi nhận trong mã nguồn gồm:

- `JWT_INVALID`: token không hợp lệ hoặc không xác thực được.
- `JWT_EXPIRED`: token hết hạn.

Log được sử dụng để hỗ trợ kiểm tra và phân tích các yêu cầu xác thực thất bại.

## 4. Thiết kế phòng thủ OTP

### 4.1. Nguy cơ bảo mật

OTP có thể bị tấn công bằng cách thử nhiều mã liên tiếp, gửi yêu cầu xác thực đồng thời hoặc sử dụng lại mã đã được chấp nhận.

Cơ chế phòng thủ được triển khai tại `OtpServiceImpl.java` nhằm hạn chế các tình huống này.

### 4.2. Tham số bảo vệ

| Tham số | Giá trị |
|---|---|
| Số lần nhập sai tối đa | 5 |
| Thời hạn OTP | 5 phút |
| Thời gian khóa sau khi vượt giới hạn | 10 phút |
| Trạng thái OTP hợp lệ | ACTIVE |
| Trạng thái OTP sau sử dụng hoặc vô hiệu hóa | INACTIVE |

Các giá trị được khai báo bằng hằng số trong `OtpServiceImpl.java`.

### 4.3. Quy trình cấp OTP

Khi người dùng yêu cầu OTP:

1. Hệ thống tìm tài khoản theo email.
2. Truy xuất bản ghi OTP hiện tại.
3. Kiểm tra trạng thái khóa tài khoản.
4. Từ chối tạo mới nếu OTP hiện tại còn hiệu lực.
5. Sinh OTP mới và thiết lập thời hạn 5 phút.
6. Cập nhật trạng thái `ACTIVE`.
7. Lưu dữ liệu OTP và gửi mã qua email.

Nếu tài khoản đang bị khóa do vượt số lần thử, hệ thống trả về phản hồi có mã trạng thái 429.

### 4.4. Quy trình xác thực OTP

Khi người dùng gửi OTP đến endpoint:

`POST /identity/api/auth/v3/check-otp`

Hệ thống thực hiện các bước:

1. Tìm người dùng tương ứng với email.
2. Truy xuất OTP bằng phương thức `findByUserForUpdate()`.
3. Kiểm tra trạng thái khóa.
4. Kiểm tra OTP còn hoạt động và chưa hết hạn.
5. Kiểm tra số lần xác thực thất bại.
6. So sánh mã OTP được gửi với mã đã lưu.
7. Nếu sai, tăng bộ đếm thất bại và ghi log.
8. Nếu đạt 5 lần sai, vô hiệu hóa OTP và khóa trong 10 phút.
9. Nếu đúng, chuyển OTP sang `INACTIVE` trước khi hoàn tất cập nhật mật khẩu.

### 4.5. Cơ chế giới hạn số lần thử

Bốn lần nhập OTP sai đầu tiên trong kịch bản đã kiểm thử nhận phản hồi HTTP 401.

Đến lần sai thứ năm, hệ thống:

- Tăng bộ đếm thất bại lên 5.
- Thiết lập thời gian khóa.
- Chuyển OTP sang trạng thái `INACTIVE`.
- Ghi sự kiện `OTP_RATE_LIMITED`.
- Trả về HTTP 429.

Cơ chế này giúp giới hạn số lần thử mã OTP trong phạm vi chính sách đã cấu hình.

### 4.6. Chống sử dụng lại và xác thực đồng thời

Phương thức xác thực OTP được triển khai trong giao dịch có `@Transactional`.

Repository cung cấp `findByUserForUpdate()` để khóa bản ghi trong quá trình xử lý.

Khi OTP hợp lệ được chấp nhận, hệ thống chuyển trạng thái thành `INACTIVE`.

Trong kịch bản kiểm thử hai yêu cầu đồng thời sử dụng cùng một OTP, chỉ một yêu cầu thành công; yêu cầu còn lại bị từ chối.

Kết quả này xác nhận cơ chế chống sử dụng lại hoạt động trong kịch bản đồng thời đã khảo sát.

### 4.7. Security Logging

Các sự kiện được ghi nhận gồm:

- `OTP_INVALID`: xác thực OTP không hợp lệ.
- `OTP_RATE_LIMITED`: vượt giới hạn hoặc đang trong thời gian khóa.

Log có thể chứa mã định danh người dùng, mã lý do, trạng thái HTTP và số lần thất bại. Các thông điệp bảo mật đã xem không ghi trực tiếp giá trị OTP.

## 5. Kết quả kiểm thử D3

### 5.1. OTP Rate Limiting

| Lần thử | HTTP Status |
|---|---|
| 1 | 401 |
| 2 | 401 |
| 3 | 401 |
| 4 | 401 |
| 5 | 429 |

Kết quả bổ sung:

- Failed Attempts trong database: 5.
- Trạng thái OTP: `INACTIVE`.
- Có ghi nhận khóa: `True`.
- Đang trong thời gian khóa tại thời điểm kiểm thử: `True`.

**Đánh giá: PASS.**

Minh chứng: `backend/identity/docs/evidence/d3-otp-rate-limit.txt`.

### 5.2. Kiểm thử xác thực OTP đồng thời

| Yêu cầu | Kết quả |
|---|---|
| Concurrent Request 1 | HTTP 200 |
| Concurrent Request 2 | HTTP 401 |
| Số yêu cầu thành công | 1 |
| Số yêu cầu bị từ chối | 1 |

**Đánh giá: PASS.**

Minh chứng: `backend/identity/docs/evidence/d3-otp-concurrent.txt`.

### 5.3. Unit Test OTP

| Chỉ số | Kết quả |
|---|---|
| Test Suite | `OtpServiceImplTest` |
| Tổng số test | 19 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Thời gian thực thi | 1,942 giây |
| Đánh giá | PASS |

Minh chứng: `backend/identity/docs/evidence/d3-otp-unit-tests.txt`.

### 5.4. Minh chứng JWT

Các file kết quả kiểm thử JWT đã được lưu:

- `d3-missing-jwt.txt`
- `d3-invalid-jwt.txt`
- `d3-expired-jwt.txt`
- `d3-valid-jwt.txt`

Các file này phục vụ đối chiếu kịch bản thiếu token, token không hợp lệ, token hết hạn và token hợp lệ.

Kết quả HTTP chi tiết của từng trường hợp cần được trích dẫn theo nội dung các file tương ứng, không suy đoán từ tên file.

## 6. Những điểm cần tiếp tục đánh giá

Mặc dù các bài kiểm thử đã ghi nhận PASS, vẫn còn những nội dung cần đánh giá bổ sung:

1. JWT: chính sách yêu cầu `exp` cần được phân biệt rõ giữa access token và API Key.
2. JWT: chưa xác nhận đầy đủ việc kiểm tra `iss`, `aud` và các trường hợp biên của HTTP Authorization Header.
3. OTP: cần xác minh các vị trí gọi phương thức `validateOTPAndEmail()` để bảo đảm không có luồng xác thực bỏ qua cơ chế giới hạn.
4. OTP: cần đánh giá nguy cơ dò tìm email qua phản hồi của `generateOtp()`.
5. OTP: cần kiểm tra chất lượng sinh mã của `OTPGenerator.generateRandom(4)`.
6. OTP: chưa đánh giá đầy đủ khả năng chống tấn công phân tán trên nhiều tài khoản hoặc địa chỉ IP.

Những nội dung này là giới hạn đánh giá và hướng cải thiện, không đồng nghĩa các bài kiểm thử D3 đã thực hiện thất bại.

## 7. Kết luận

Trong phạm vi nghiên cứu, hệ thống đã triển khai các cơ chế phòng thủ Broken Authentication gồm xác thực chữ ký JWT bằng RS256, kiểm tra điều kiện thời gian của token, giới hạn số lần nhập OTP, khóa tạm thời, vô hiệu hóa OTP sau sử dụng và xử lý xác thực đồng thời.

Kết quả kiểm thử OTP cho thấy cơ chế giới hạn số lần thử hoạt động đúng trong kịch bản đã khảo sát, với HTTP 429 tại lần nhập sai thứ năm. Kiểm thử đồng thời ghi nhận một yêu cầu thành công và một yêu cầu bị từ chối. Bộ 19 Unit Test OTP không ghi nhận lỗi.

Các kết quả trên hỗ trợ đánh giá hiệu quả của những biện pháp phòng thủ đã triển khai, đồng thời xác định các giới hạn cần nghiên cứu thêm để nâng cao độ an toàn của hệ thống xác thực.
