# NGHIÊN CỨU VÀ TRIỂN KHAI PHÒNG CHỐNG LỖ HỔNG BOLA VÀ BROKEN AUTHENTICATION TRÊN RESTful API

## 1. Giới thiệu đề tài

Dự án thuộc đề tài **“Nghiên cứu, khai thác và phòng chống các lỗ hổng kiểm soát truy cập và xác thực trên hệ thống API RESTful”**.

Mục tiêu là nghiên cứu, xây dựng môi trường thực nghiệm, phân tích các lỗ hổng bảo mật và triển khai biện pháp phòng chống dựa trên hệ thống OWASP crAPI (Completely Ridiculous API).

Hai nhóm lỗ hổng được tập trung triển khai phòng thủ gồm:

- **Broken Object Level Authorization (BOLA):** Lỗ hổng kiểm soát quyền truy cập ở cấp đối tượng.
- **Broken Authentication:** Lỗ hổng liên quan đến cơ chế xác thực người dùng, đặc biệt là JWT và OTP.

Dự án kết hợp phân tích mã nguồn, triển khai bản vá, kiểm thử bằng Postman, kiểm thử đơn vị và đối chiếu kết quả trước – sau khi áp dụng biện pháp phòng thủ.

Kết quả chỉ phản ánh phạm vi API và các tình huống đã được kiểm thử, không đồng nghĩa với việc toàn bộ hệ thống crAPI đã được bảo mật hoàn toàn.

## 2. Thành viên và phân công nhiệm vụ

**Nhóm B11**

| Thành viên | Nhiệm vụ |
|---|---|
| Nguyễn Thanh Thuận | Phòng chống BOLA, kiểm tra quyền sở hữu đối tượng, ghi nhật ký sự kiện bảo mật |
| Phước Toàn | Kiểm tra tính hợp lệ JWT, phòng chống tấn công brute-force OTP |

## 3. Môi trường và công cụ triển khai

| Công cụ | Mục đích |
|---|---|
| OWASP crAPI | Hệ thống API có chủ đích chứa lỗ hổng để nghiên cứu |
| Java và Spring Boot | Phân tích, chỉnh sửa và kiểm thử mã nguồn dịch vụ Identity |
| Docker Desktop và Docker Compose | Triển khai các dịch vụ và môi trường thực nghiệm |
| PostgreSQL và MongoDB | Lưu trữ dữ liệu phục vụ hệ thống crAPI |
| Postman | Gửi yêu cầu HTTP và kiểm tra kết quả API |
| Gradle Wrapper | Biên dịch và chạy kiểm thử đơn vị |
| Git và GitHub | Quản lý phiên bản, lưu trữ mã nguồn và tài liệu |
| PowerShell và VS Code | Thực hiện lệnh và chỉnh sửa mã nguồn, tài liệu |

Các thư mục làm việc trong môi trường thực nghiệm:

```text
D:\api-security-lab
D:\api-security-defense
```

Kho lưu trữ môi trường lab:

https://github.com/thuan5587/api-security-lab

Kho lưu trữ mã nguồn phòng thủ:

https://github.com/thuan5587/api-security-defense

## 4. Các cơ chế phòng thủ đã triển khai

### 4.1. Phòng chống BOLA

BOLA xảy ra khi API không xác minh đầy đủ quyền truy cập của người dùng đối với một đối tượng cụ thể.

Ví dụ: người dùng Alice có thể thay đổi mã định danh phương tiện trong yêu cầu API để truy cập thông tin vị trí phương tiện thuộc về Bob.

API được kiểm thử:

`GET /identity/api/v2/vehicle/{carId}/location`

**Biện pháp phòng thủ:**

1. Xác thực người dùng gửi yêu cầu.
2. Xác định phương tiện được yêu cầu.
3. Kiểm tra quan hệ sở hữu giữa người dùng và phương tiện.
4. Chỉ cho phép truy cập khi người dùng có quyền hợp lệ.
5. Từ chối truy cập trái phép và ghi nhận sự kiện bảo mật.

**Kết quả thực nghiệm:**

| Tình huống | Trước bản vá | Sau bản vá |
|---|---|---|
| Alice truy cập vị trí xe của Bob | HTTP 200 | HTTP 404 |
| Chủ sở hữu truy cập xe của mình | HTTP 200 | HTTP 200 |
| Yêu cầu không có JWT | Chưa đối chiếu trong bảng này | HTTP 401 |
| Mã UUID không hợp lệ | Chưa đối chiếu trong bảng này | HTTP 400 |

Kết quả đã ghi nhận:

- 6/6 trường hợp kiểm thử thủ công BOLA đạt yêu cầu.
- 16/16 kiểm thử đơn vị `VehicleServiceImplTest` thành công.
- Yêu cầu truy cập trái phép được ghi nhận thông qua sự kiện `BOLA_ACCESS_DENIED`.

### 4.2. Phòng chống Broken Authentication – JWT

JWT (JSON Web Token) được sử dụng để xác thực yêu cầu truy cập các API cần đăng nhập.

Các thành phần liên quan:

- `JwtProvider.java`
- `JwtAuthTokenFilter.java`
- `WebSecurityConfig.java`

**Biện pháp phòng thủ:**

1. Kiểm tra sự tồn tại và định dạng JWT.
2. Xác minh chữ ký bằng thuật toán RS256 và khóa công khai RSA.
3. Từ chối token có chữ ký không hợp lệ hoặc sử dụng thuật toán không được chấp nhận.
4. Kiểm tra các điều kiện thời gian của token khi áp dụng.
5. Từ chối yêu cầu không đáp ứng điều kiện xác thực.
6. Ghi nhật ký các sự kiện xác thực thất bại.

**Các tình huống kiểm thử đã ghi nhận:**

- Không gửi JWT.
- JWT có chữ ký không hợp lệ.
- JWT đã hết hạn.
- JWT hợp lệ.

Các kết quả chi tiết được lưu trong thư mục `backend/identity/docs/evidence/`.

**Lưu ý kỹ thuật:** Access token có thời hạn sử dụng, nhưng cơ chế hiện tại cũng hỗ trợ một số API key không chứa trường `exp`. Vì vậy, không khẳng định rằng tất cả token được chấp nhận đều bắt buộc có thời hạn hết hạn.

### 4.3. Phòng chống brute-force OTP

OTP (One-Time Password) là mã xác thực sử dụng một lần.

Nếu hệ thống không giới hạn số lần thử, kẻ tấn công có thể gửi liên tục các mã OTP khác nhau để tìm mã hợp lệ.

Thành phần triển khai chính:

`OtpServiceImpl.java`

**Cấu hình phòng thủ:**

| Cơ chế | Giá trị |
|---|---|
| Số lần nhập OTP sai tối đa | 5 lần |
| Thời gian hiệu lực OTP | 5 phút |
| Thời gian khóa khi vượt giới hạn | 10 phút |
| Ngăn tái sử dụng OTP | Chuyển OTP sang trạng thái không hoạt động sau khi xác thực thành công |
| Kiểm soát yêu cầu đồng thời | Transaction và khóa dữ liệu tại repository |

**Kết quả kiểm thử:**

| Tình huống | Kết quả |
|---|---|
| Nhập OTP sai lần 1–4 | HTTP 401 |
| Nhập OTP sai lần thứ 5 | HTTP 429 |
| Kiểm tra dữ liệu sau khi vượt giới hạn | Ghi nhận 5 lần thất bại, OTP không hoạt động và trạng thái khóa |
| Hai yêu cầu đồng thời sử dụng cùng OTP | Một yêu cầu HTTP 200, một yêu cầu HTTP 401 |
| Kiểm thử đơn vị `OtpServiceImplTest` | 19/19 thành công |

### 4.4. Ghi nhật ký sự kiện bảo mật

Hệ thống được bổ sung cơ chế ghi nhận các sự kiện liên quan đến truy cập trái phép và xác thực thất bại.

| Sự kiện | Ý nghĩa |
|---|---|
| `BOLA_ACCESS_DENIED` | Từ chối truy cập đối tượng không thuộc quyền sở hữu |
| `JWT_INVALID` | JWT không hợp lệ |
| `JWT_EXPIRED` | JWT đã hết hạn |
| `OTP_INVALID` | OTP không hợp lệ |
| `OTP_RATE_LIMITED` | Vượt giới hạn số lần thử OTP |

Nhật ký giúp hỗ trợ việc xác minh kết quả kiểm thử, điều tra các yêu cầu bất thường và đánh giá hiệu quả của cơ chế phòng thủ.

## 5. Cấu trúc thư mục dự án

```text
api-security-defense/
├── README.md
├── docker-compose.bola.yml
├── docker-compose.broken-auth.yml
├── backend/
│   └── identity/
│       ├── Dockerfile
│       ├── build-image.sh
│       ├── gradlew.bat
│       ├── src/
│       └── docs/
│           └── evidence/
└── docs/
    └── defense/
        ├── 01-baseline.md
        ├── 02-bola-design.md
        ├── 03-auth-design.md
        ├── 04-test-results.md
        └── 05-before-after.md
```

Các tệp chứa thông tin nhạy cảm như mật khẩu, khóa bí mật và cấu hình riêng của máy không được công khai trong tài liệu bàn giao.

## 6. Triển khai môi trường Docker Compose

### 6.1. Cấu hình Docker Compose gốc

File Compose gốc của hệ thống crAPI:

```text
D:\api-security-lab\crapi\deploy\docker\docker-compose.yml
```

Hai file Compose override trong dự án phòng thủ:

- `docker-compose.bola.yml`: sử dụng image `crapi-identity-bola-defense:v2`.
- `docker-compose.broken-auth.yml`: sử dụng image `crapi-identity-broken-auth-defense:v1`.

Hai file override chỉ thay đổi image của dịch vụ `crapi-identity`, không thay thế toàn bộ cấu hình triển khai crAPI.

### 6.2. Kiểm tra cấu hình BOLA

```powershell
docker compose --project-directory "D:\api-security-lab\crapi\deploy\docker" -f "D:\api-security-lab\crapi\deploy\docker\docker-compose.yml" -f "D:\api-security-defense\docker-compose.bola.yml" config --quiet
```

### 6.3. Kiểm tra cấu hình Broken Authentication

```powershell
docker compose --project-directory "D:\api-security-lab\crapi\deploy\docker" -f "D:\api-security-lab\crapi\deploy\docker\docker-compose.yml" -f "D:\api-security-defense\docker-compose.broken-auth.yml" config --quiet
```

**Kết quả xác minh:** Cả hai lệnh kiểm tra cấu hình đã thực hiện thành công và không trả về thông báo lỗi.

### 6.4. Khởi chạy bản vá BOLA

Điều kiện: image Docker tương ứng đã được build và các thành phần phụ thuộc đã được chuẩn bị.

```powershell
docker compose --project-directory "D:\api-security-lab\crapi\deploy\docker" -f "D:\api-security-lab\crapi\deploy\docker\docker-compose.yml" -f "D:\api-security-defense\docker-compose.bola.yml" up -d
```

### 6.5. Khởi chạy bản vá Broken Authentication

```powershell
docker compose --project-directory "D:\api-security-lab\crapi\deploy\docker" -f "D:\api-security-lab\crapi\deploy\docker\docker-compose.yml" -f "D:\api-security-defense\docker-compose.broken-auth.yml" up -d
```

Không chạy đồng thời hai cấu hình trên trong cùng một Compose project. Khi chuyển đổi, cần kiểm tra lại image và trạng thái của dịch vụ Identity.

**Lưu ý:** Hai cấu hình đã vượt qua bước kiểm tra `config --quiet`, nhưng việc khởi động container thực tế chưa được xác minh lại trong giai đoạn cập nhật README.

### 6.6. Các địa chỉ truy cập

| Dịch vụ | Địa chỉ |
|---|---|
| crAPI HTTP | http://localhost:8888 |
| crAPI HTTPS | https://localhost:8443 |
| MailHog | http://localhost:8025 |

Các địa chỉ chỉ hoạt động khi dịch vụ tương ứng đã khởi chạy thành công.

## 7. Kiểm thử đơn vị

Mở PowerShell tại:

```text
D:\api-security-defense\backend\identity
```

Chạy kiểm thử:

```powershell
.\gradlew.bat test
```

**Kết quả đã ghi nhận:**

| Bộ kiểm thử | Tổng số | Thành công | Thất bại |
|---|---:|---:|---:|
| `VehicleServiceImplTest` | 16 | 16 | 0 |
| `OtpServiceImplTest` | 19 | 19 | 0 |

Đây là kết quả của hai bộ kiểm thử được ghi nhận, không phải tổng số kiểm thử của toàn bộ dự án.

## 8. Kiểm thử API bằng Postman

### 8.1. Kiểm thử BOLA

API:

`GET /identity/api/v2/vehicle/{carId}/location`

Các tình huống trọng tâm:

- Người dùng truy cập phương tiện thuộc quyền sở hữu của mình.
- Người dùng cố truy cập phương tiện của người khác.
- Yêu cầu thiếu JWT.
- Yêu cầu chứa mã đối tượng không hợp lệ.

Đối chiếu kết quả trước và sau khi triển khai bản vá để xác nhận hiệu quả kiểm soát quyền sở hữu.

### 8.2. Kiểm thử JWT

Các tình huống:

- Không có token.
- Token giả mạo hoặc không hợp lệ.
- Token hết hạn.
- Token hợp lệ.

Dữ liệu kiểm thử và phản hồi được lưu trong các tệp minh chứng D3.

### 8.3. Kiểm thử OTP

API:

`POST /identity/api/auth/v3/check-otp`

Kiểm tra số lần nhập sai, giới hạn tốc độ, trạng thái khóa, khả năng tái sử dụng OTP và xử lý yêu cầu đồng thời.

Các kết quả kiểm thử chi tiết được lưu trong thư mục minh chứng.

## 9. Kiểm tra nhật ký bảo mật

Khi container Identity đang hoạt động, có thể xem 200 dòng log gần nhất bằng lệnh:

```powershell
docker logs crapi-identity --tail 200
```

Tìm các sự kiện:

```text
BOLA_ACCESS_DENIED
JWT_INVALID
JWT_EXPIRED
OTP_INVALID
OTP_RATE_LIMITED
```

Sự kiện chỉ xuất hiện nếu tình huống tương ứng đã được kích hoạt và phiên bản đang chạy có triển khai cơ chế ghi log đó.

## 10. Hướng dẫn khôi phục phiên bản gốc (Rollback)

Khi cần khôi phục dịch vụ Identity về image gốc của OWASP crAPI, sử dụng cấu hình Compose gốc mà không kèm file override.

Image gốc:

`crapi/crapi-identity:${VERSION:-latest}`

Lệnh khôi phục:

```powershell
docker compose --project-directory "D:\api-security-lab\crapi\deploy\docker" -f "D:\api-security-lab\crapi\deploy\docker\docker-compose.yml" up -d --force-recreate crapi-identity
```

Sau khi khôi phục, cần kiểm tra:

- Image đang được sử dụng bởi container Identity.
- Trạng thái hoạt động của dịch vụ.
- Khả năng truy cập API.
- Các cấu hình và dữ liệu cần thiết.

**Cảnh báo:** Rollback thay đổi dịch vụ đang chạy. Quy trình này chưa được thực hiện lại trong giai đoạn cập nhật tài liệu. Không sử dụng `docker compose down -v` vì có thể xóa dữ liệu lưu trong Docker volumes.

## 11. Tài liệu nghiên cứu và minh chứng

| Tệp | Nội dung |
|---|---|
| `docs/defense/01-baseline.md` | Khảo sát và ghi nhận trạng thái bảo mật ban đầu |
| `docs/defense/02-bola-design.md` | Phân tích và thiết kế giải pháp phòng chống BOLA |
| `docs/defense/03-auth-design.md` | Phân tích và thiết kế cơ chế phòng chống Broken Authentication |
| `docs/defense/04-test-results.md` | Kết quả kiểm thử BOLA |
| `docs/defense/05-before-after.md` | So sánh hành vi API trước và sau khi triển khai phòng thủ |

Các tệp minh chứng JWT và OTP được lưu tại:

`backend/identity/docs/evidence/`

Một số minh chứng có thể chỉ được lưu trong môi trường thực nghiệm nội bộ và không được công khai nếu chứa thông tin nhạy cảm.

## 12. Giới hạn và hướng phát triển

Mặc dù các tình huống kiểm thử trọng tâm đã đạt kết quả mong đợi, hệ thống vẫn có một số giới hạn:

- Phòng chống BOLA mới được xác minh trong phạm vi endpoint phương tiện đã lựa chọn.
- Kiểm thử JWT chưa bao phủ toàn bộ các tình huống tấn công có thể xảy ra.
- Chính sách hết hạn JWT có sự khác biệt giữa access token và API key.
- Một số luồng xác thực OTP cũ cần được rà soát bổ sung để bảo đảm chính sách phòng thủ được áp dụng nhất quán.
- Cần tiếp tục đánh giá khả năng tiết lộ sự tồn tại của tài khoản qua phản hồi OTP.
- Chưa xác nhận đầy đủ cơ chế kiểm tra các claim như `iss` và `aud`.
- Chưa thực hiện đánh giá tải lớn hoặc mô phỏng tấn công phân tán trên môi trường production.

Hướng phát triển gồm mở rộng kiểm soát quyền sở hữu sang các API khác, tăng cường chính sách JWT, thống nhất cơ chế OTP, chuẩn hóa nhật ký bảo mật và tự động hóa kiểm thử hồi quy.

## 13. Kết luận và trạng thái dự án

Trong phạm vi nghiên cứu đã xác định, nhóm đã triển khai và kiểm thử các cơ chế phòng thủ trọng tâm đối với BOLA và Broken Authentication trên hệ thống OWASP crAPI.

Kết quả nổi bật:

- Ngăn chặn truy cập trái phép vào thông tin vị trí phương tiện trong tình huống BOLA đã kiểm thử.
- Kiểm tra chữ ký và tính hợp lệ của JWT.
- Giới hạn số lần thử OTP và ngăn tái sử dụng OTP trong các tình huống đã xác minh.
- Bổ sung nhật ký sự kiện bảo mật.
- Hoàn thành các bộ kiểm thử BOLA và OTP đã ghi nhận.
- Xây dựng tài liệu thiết kế, kiểm thử và so sánh trước – sau.

**Trạng thái:** Đã hoàn thành triển khai và xác minh các cơ chế phòng thủ trong phạm vi thực nghiệm. Đang hoàn thiện rà soát tài liệu và đồng bộ lên GitHub.