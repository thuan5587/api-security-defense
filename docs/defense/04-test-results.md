# 04 – BÁO CÁO KẾT QUẢ KIỂM THỬ PHÒNG CHỐNG BOLA

## 1. Mục tiêu kiểm thử

Mục tiêu của quá trình kiểm thử là đánh giá hiệu quả của bản vá Broken Object Level Authorization (BOLA) trên hệ thống OWASP crAPI.

Quá trình kiểm thử tập trung xác minh rằng:

- Người dùng chỉ được truy cập thông tin vị trí phương tiện thuộc quyền sở hữu của mình.
- Người dùng không thể truy cập vị trí phương tiện của người khác dù có JWT hợp lệ.
- Yêu cầu không có thông tin xác thực bị từ chối.
- API xử lý phù hợp với mã định danh phương tiện không hợp lệ hoặc không tồn tại.
- Backend ghi nhận sự kiện khi phát hiện truy cập sai quyền sở hữu.

## 2. Môi trường kiểm thử

| Thành phần | Thông tin |
|---|---|
| Hệ thống | OWASP crAPI |
| Backend | crapi-identity |
| Ngôn ngữ | Java |
| Framework | Spring Boot |
| Công cụ API | Postman |
| Môi trường triển khai | Docker Compose |
| Địa chỉ API | http://localhost:8888 |
| Docker image bản vá | crapi-identity-bola-defense:v2 |
| Công cụ Unit Test | Gradle |

Endpoint được kiểm thử:

`GET /identity/api/v2/vehicle/{carId}/location`

Phạm vi đánh giá chỉ bao gồm endpoint trên và các thành phần liên quan đến bản vá.

## 3. Dữ liệu và tài khoản kiểm thử

Hai tài khoản trong môi trường lab:

| Tài khoản | Vai trò |
|---|---|
| Alice | Người dùng thực hiện yêu cầu |
| Bob | Chủ sở hữu phương tiện mục tiêu |

Mã định danh phương tiện sử dụng trong kiểm thử:

- Xe Alice: `b97ffaf0-3d64-4c8f-be30-bf7bddd18484`
- Xe Bob: `b6ab7600-594f-4104-9ce8-d370a46e8ecc`

JWT của Alice và Bob được sử dụng riêng biệt nhằm đánh giá cơ chế phân quyền theo chủ sở hữu.

Các giá trị này thuộc môi trường lab và không được xem là dữ liệu cố định cho các lần triển khai khác.

## 4. Phương pháp kiểm thử

Quá trình kiểm thử được thực hiện theo các bước:

1. Triển khai hệ thống crAPI bằng Docker.
2. Sử dụng Postman để gửi yêu cầu đến API lấy vị trí phương tiện.
3. Kiểm tra phản hồi trước khi áp dụng bản vá.
4. Triển khai backend đã bổ sung kiểm tra quyền sở hữu.
5. Gửi lại yêu cầu với cùng kịch bản kiểm thử.
6. Ghi nhận HTTP Status và kiểm tra nội dung phản hồi.
7. Kiểm tra log từ container `crapi-identity`.
8. Chạy Unit Test để xác minh logic xử lý ở tầng Service.

## 5. Danh sách Test Case

| Mã | Kịch bản | Kết quả mong đợi | Kết quả thực tế | Đánh giá |
|---|---|---|---|---|
| BOLA-TC01 | Alice truy cập xe Bob | Từ chối truy cập | 404 Not Found | PASS |
| BOLA-TC02 | Alice truy cập xe Alice | Cho phép truy cập | 200 OK | PASS |
| BOLA-TC03 | Bob truy cập xe Bob | Cho phép truy cập | 200 OK | PASS |
| BOLA-TC04 | Không gửi Authorization | Từ chối yêu cầu | 401 Unauthorized | PASS |
| BOLA-TC05 | UUID hợp lệ nhưng không tồn tại | Không trả dữ liệu phương tiện | 404 Not Found | PASS |
| BOLA-TC06 | Mã phương tiện sai định dạng | Từ chối dữ liệu đầu vào | 400 Bad Request | PASS |

Các kết quả trên được ghi nhận trong quá trình kiểm thử thủ công trên môi trường lab.

## 6. Kiểm thử BOLA-TC01 – Alice truy cập xe Bob

### 6.1. Mục tiêu

Xác minh người dùng Alice không thể truy cập dữ liệu vị trí phương tiện thuộc sở hữu của Bob.

### 6.2. Yêu cầu

```http
GET /identity/api/v2/vehicle/b6ab7600-594f-4104-9ce8-d370a46e8ecc/location
Authorization: Bearer <JWT_ALICE>
```

### 6.3. Kết quả trước khi vá

- HTTP Status: `200 OK`.
- API trả về thông tin liên quan đến phương tiện của Bob.
- Người dùng Alice truy cập được dữ liệu không thuộc quyền sở hữu.

Đánh giá: **FAIL về bảo mật – tồn tại BOLA.**

### 6.4. Kết quả sau khi vá

- HTTP Status: `404 Not Found`.
- Không trả về dữ liệu vị trí phương tiện.
- Backend ghi nhận sự kiện từ chối quyền sở hữu.

Đánh giá: **PASS – kịch bản truy cập trái phép đã bị chặn.**

## 7. Kiểm thử các trường hợp hợp lệ và không hợp lệ

### BOLA-TC02 – Alice truy cập xe Alice

Sử dụng JWT của Alice và mã phương tiện thuộc sở hữu Alice.

Kết quả: `200 OK`.

Nhận xét: Bản vá vẫn cho phép chủ sở hữu truy cập dữ liệu hợp lệ.

### BOLA-TC03 – Bob truy cập xe Bob

Sử dụng JWT của Bob và mã phương tiện thuộc sở hữu Bob.

Kết quả: `200 OK`.

Nhận xét: Quyền truy cập hợp lệ của Bob không bị ảnh hưởng.

### BOLA-TC04 – Không gửi Authorization

Gửi yêu cầu đến API nhưng không cung cấp Bearer Token.

Kết quả: `401 Unauthorized`.

Nhận xét: Hệ thống từ chối yêu cầu chưa xác thực.

### BOLA-TC05 – UUID không tồn tại

Gửi yêu cầu với UUID có định dạng hợp lệ nhưng không tương ứng với phương tiện tồn tại trong dữ liệu kiểm thử.

Kết quả: `404 Not Found`.

Nhận xét: API không trả về dữ liệu phương tiện không tồn tại.

### BOLA-TC06 – Mã phương tiện sai định dạng

Gửi yêu cầu với giá trị `carId` không đúng định dạng UUID.

Kết quả: `400 Bad Request`.

Nhận xét: API từ chối yêu cầu có mã định danh sai định dạng.

## 8. Kiểm thử ghi log bảo mật

Sau khi gửi yêu cầu Alice truy cập xe Bob, log container `crapi-identity` ghi nhận sự kiện:

```text
BOLA_ACCESS_DENIED event=OBJECT_OWNERSHIP_DENIED method=GET resourceId=<vehicle_uuid> principalId=<user_id> reason=NOT_OWNER
```

Lệnh kiểm tra:

```powershell
docker logs crapi-identity --tail 200 2>&1 | Select-String "BOLA_ACCESS_DENIED"
```

Kết quả cho thấy cơ chế ghi log được kích hoạt khi kiểm tra quyền sở hữu không thành công.

File minh chứng:

`evidence/bola/BOLA-Access-Denied-Log.txt`

Log phục vụ phân tích bảo mật và không chứa JWT hoặc thông tin vị trí trong thông điệp sự kiện BOLA này.

## 9. Kiểm thử đơn vị – Unit Test

Các bài kiểm thử liên quan đến xử lý vị trí phương tiện được cập nhật trong:

`VehicleServiceImplTest.java`

Nội dung kiểm thử bao gồm xác minh logic xử lý quyền sở hữu, đặc biệt trường hợp người dùng không phải chủ sở hữu.

Lệnh thực hiện trong thư mục backend identity:

```powershell
.\gradlew.bat test
```

Kết quả: Quá trình chạy Unit Test hoàn thành thành công trong môi trường sử dụng JDK 17.

Đánh giá: **PASS** đối với bộ kiểm thử đã chạy.

## 10. Tổng hợp kết quả

| Nhóm kiểm thử | Kết quả |
|---|---|
| Kiểm thử API thủ công | 6/6 trường hợp PASS |
| Truy cập trái phép sau vá | Đã bị chặn |
| Truy cập hợp lệ | Vẫn hoạt động |
| Log từ chối truy cập | Có ghi nhận |
| Unit Test | Chạy thành công |
| Docker container | Healthy |

Kết quả kiểm thử hỗ trợ kết luận rằng bản vá đã khắc phục kịch bản BOLA được khảo sát trên endpoint lấy vị trí phương tiện.

## 11. Minh chứng kiểm thử

Các minh chứng được lưu trong:

`evidence/bola/`

Danh sách:

- `BOLA-02-Before-Patch.png`
- `BOLA-02-After-Patch.png`
- `BOLA-Access-Denied-Log.txt`

Ảnh Before Patch ghi nhận phản hồi HTTP 200 và dữ liệu được trả về trái phép.

Ảnh After Patch ghi nhận phản hồi HTTP 404 khi thực hiện lại yêu cầu với JWT của Alice và mã phương tiện của Bob.

Các file gốc được giữ cục bộ nhằm tránh công khai thông tin nhạy cảm.

## 12. Hạn chế kiểm thử

Phạm vi kiểm thử hiện tại còn một số hạn chế:

- Chỉ tập trung vào một endpoint liên quan đến vị trí phương tiện.
- Chưa đánh giá toàn bộ các endpoint có nguy cơ BOLA trong crAPI.
- Chưa thực hiện kiểm thử tải hoặc kiểm thử đồng thời.
- Chưa đo lường ảnh hưởng hiệu năng của bước kiểm tra quyền sở hữu.
- Chưa đánh giá đầy đủ các tình huống phân quyền phức tạp hơn.

Vì vậy, kết quả PASS không đồng nghĩa toàn bộ hệ thống crAPI không còn lỗ hổng BOLA.

## 13. Kết luận

Quá trình kiểm thử cho thấy bản vá BOLA hoạt động đúng với các kịch bản đã khảo sát.

Trước khi vá, Alice sử dụng JWT hợp lệ để truy cập vị trí phương tiện của Bob và nhận phản hồi HTTP 200.

Sau khi vá, cùng kịch bản bị từ chối với HTTP 404, không trả dữ liệu vị trí và tạo log bảo mật.

Các trường hợp truy cập hợp lệ tiếp tục hoạt động, trong khi yêu cầu không xác thực hoặc có mã định danh không hợp lệ được xử lý bằng mã trạng thái HTTP tương ứng.

Kết quả này chứng minh hiệu quả của việc kết hợp xác thực người dùng với kiểm tra quyền sở hữu đối tượng tại tầng nghiệp vụ trong phạm vi endpoint được kiểm thử.