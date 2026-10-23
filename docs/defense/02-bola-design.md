# 02 – THIẾT KẾ VÀ TRIỂN KHAI PHÒNG CHỐNG BOLA

## 1. Mục tiêu

Tài liệu trình bày giải pháp phòng chống lỗ hổng Broken Object Level Authorization (BOLA) trên endpoint lấy vị trí phương tiện của hệ thống OWASP crAPI.

Giải pháp tập trung vào việc xác minh quyền sở hữu đối tượng ở tầng nghiệp vụ trước khi cung cấp dữ liệu, thay vì chỉ dựa vào trạng thái đăng nhập hoặc JWT hợp lệ.

Phạm vi bản vá:

`GET /identity/api/v2/vehicle/{carId}/location`

Bản vá này không được xem là giải pháp khắc phục toàn bộ các endpoint có nguy cơ BOLA trong hệ thống crAPI.

## 2. Phân tích nguyên nhân

Trước khi vá, endpoint cho phép người dùng đã xác thực truy vấn thông tin vị trí phương tiện thông qua mã định danh `carId`.

Tuy nhiên, quá trình xử lý chưa xác minh đầy đủ liệu người dùng đang gửi yêu cầu có phải chủ sở hữu phương tiện được truy vấn hay không.

Do đó, Alice có thể sử dụng JWT hợp lệ của mình nhưng thay `carId` bằng mã phương tiện của Bob để nhận dữ liệu không thuộc quyền truy cập.

Đây là sự khác biệt giữa:

- **Authentication (xác thực):** Xác định người gửi yêu cầu là ai.
- **Authorization (phân quyền):** Xác định người đó có được phép truy cập đối tượng cụ thể hay không.

## 3. Nguyên tắc thiết kế

Giải pháp áp dụng các nguyên tắc:

1. Không mặc định JWT hợp lệ đồng nghĩa với quyền truy cập mọi đối tượng.
2. Xác định người dùng hiện tại từ thông tin xác thực của yêu cầu.
3. Tìm phương tiện theo `carId`.
4. Kiểm tra quyền sở hữu phương tiện ở tầng Service.
5. Chỉ trả dữ liệu vị trí nếu người dùng hiện tại là chủ sở hữu.
6. Từ chối yêu cầu không đủ quyền mà không tiết lộ dữ liệu vị trí.
7. Ghi log phục vụ kiểm tra và giám sát các lần truy cập bị từ chối.

## 4. Kiến trúc xử lý

Các thành phần liên quan:

| Thành phần | Vai trò |
|---|---|
| Postman | Gửi yêu cầu kiểm thử với Bearer Token |
| API Gateway | Tiếp nhận và chuyển tiếp yêu cầu |
| VehicleController | Xử lý HTTP request và response |
| VehicleServiceImpl | Kiểm tra quyền sở hữu trước khi trả dữ liệu |
| UserService | Xác định người dùng từ token |
| Vehicle Repository | Truy xuất thông tin phương tiện |
| Docker | Triển khai backend chứa bản vá |

Luồng xử lý sau khi vá:

1. Client gửi yêu cầu GET cùng Bearer Token.
2. Hệ thống xử lý xác thực JWT.
3. Controller chuyển `carId` và `HttpServletRequest` đến Service.
4. Service xác định người dùng hiện tại.
5. Service truy xuất phương tiện theo `carId`.
6. Service so sánh ID người dùng hiện tại với ID chủ sở hữu phương tiện.
7. Nếu không trùng khớp, hệ thống ghi log và từ chối truy cập.
8. Nếu trùng khớp, hệ thống cho phép trả dữ liệu vị trí.

## 5. Triển khai tại tầng Controller

File liên quan:

`backend/identity/src/main/java/com/crapi/controller/VehicleController.java`

Controller chuyển thêm đối tượng `HttpServletRequest` xuống Service để hỗ trợ xác định người dùng hiện tại.

Service trả về kết quả hợp lệ khi người dùng được phép truy cập. Nếu không tìm thấy dữ liệu được phép trả về, Controller sử dụng phản hồi HTTP 404.

Việc không trả thông tin vị trí khi quyền sở hữu không hợp lệ giúp ngăn rò rỉ dữ liệu qua endpoint được kiểm thử.

## 6. Triển khai kiểm tra quyền sở hữu tại tầng Service

File liên quan:

`backend/identity/src/main/java/com/crapi/service/Impl/VehicleServiceImpl.java`

Phương thức được điều chỉnh:

`getVehicleLocation(UUID carId, HttpServletRequest request)`

Logic chính của bản vá:

```java
if (!java.util.Objects.equals(
        currentUser.getId(),
        vehicleDetails.getOwner().getId())) {
    log.warn(
        "BOLA_ACCESS_DENIED event=OBJECT_OWNERSHIP_DENIED method={} resourceId={} principalId={} reason=NOT_OWNER",
        request.getMethod(),
        carId,
        currentUser.getId()
    );
    return null;
}
```

Trong đó:

- `currentUser.getId()`: ID của người dùng đang thực hiện yêu cầu.
- `vehicleDetails.getOwner().getId()`: ID của chủ sở hữu phương tiện.
- `Objects.equals(...)`: So sánh hai ID để xác định quyền sở hữu.
- `log.warn(...)`: Ghi nhận sự kiện truy cập bị từ chối.
- `return null`: Không cung cấp dữ liệu vị trí cho người dùng không có quyền; Controller xử lý kết quả này thành phản hồi 404.

Điều kiện kiểm tra được thực hiện trước khi trả thông tin vị trí phương tiện.

## 7. Cơ chế ghi log bảo mật

Khi người dùng truy cập phương tiện không thuộc quyền sở hữu, backend ghi sự kiện:

`BOLA_ACCESS_DENIED`

Ví dụ cấu trúc log:

```text
BOLA_ACCESS_DENIED event=OBJECT_OWNERSHIP_DENIED method=GET resourceId=<vehicle_uuid> principalId=<user_id> reason=NOT_OWNER
```

Các trường dữ liệu:

| Trường | Ý nghĩa |
|---|---|
| `event` | Loại sự kiện kiểm soát truy cập |
| `method` | Phương thức HTTP |
| `resourceId` | Mã phương tiện được yêu cầu |
| `principalId` | ID người dùng thực hiện yêu cầu |
| `reason` | Lý do từ chối |

Log hỗ trợ việc kiểm tra các trường hợp truy cập không được phép. Một sự kiện bị từ chối không tự động chứng minh có hành vi tấn công thực tế; cần đối chiếu với ngữ cảnh kiểm thử hoặc hoạt động hệ thống.

Không ghi JWT hoặc dữ liệu vị trí vào thông điệp log này.

## 8. Triển khai bằng Docker

Bản vá được đóng gói thành Docker image:

`crapi-identity-bola-defense:v2`

File cấu hình ghi đè:

`docker-compose.bola.yml`

Nội dung:

```yaml
services:
  crapi-identity:
    image: crapi-identity-bola-defense:v2
```

File Compose ghi đè được sử dụng cùng cấu hình Docker Compose gốc để triển khai riêng dịch vụ `crapi-identity` đã sửa.

Cách triển khai này cho phép giữ lại phiên bản gốc nhằm phục vụ đối chiếu trước và sau khi vá.

## 9. Kiểm thử sau khi triển khai

Các trường hợp đã kiểm thử:

| Trường hợp | Kết quả |
|---|---|
| Alice truy cập xe Bob | 404 Not Found |
| Alice truy cập xe Alice | 200 OK |
| Bob truy cập xe Bob | 200 OK |
| Không gửi Authorization | 401 Unauthorized |
| UUID hợp lệ nhưng không tồn tại | 404 Not Found |
| Mã phương tiện sai định dạng | 400 Bad Request |

Kết quả cho thấy endpoint từ chối truy cập chéo giữa hai người dùng trong kịch bản kiểm thử, đồng thời vẫn cho phép chủ sở hữu truy cập phương tiện của mình.

Các bài kiểm thử đơn vị liên quan đến phương thức xử lý vị trí phương tiện cũng đã được chạy thành công.

## 10. So sánh trước và sau khi vá

| Tiêu chí | Trước khi vá | Sau khi vá |
|---|---|---|
| JWT của Alice | Hợp lệ | Hợp lệ |
| Phương tiện mục tiêu | Thuộc Bob | Thuộc Bob |
| Kiểm tra quyền sở hữu | Không đầy đủ | Có |
| HTTP Status | 200 OK | 404 Not Found |
| Dữ liệu vị trí bị tiết lộ | Có | Không |
| Log từ chối quyền sở hữu | Chưa có log mới | Có |
| Đánh giá | Tồn tại BOLA | Đã chặn kịch bản BOLA được kiểm thử |

## 11. Minh chứng

Các minh chứng được lưu cục bộ trong thư mục:

`evidence/bola/`

Bao gồm:

- `BOLA-02-Before-Patch.png`: Phản hồi HTTP 200 trước khi vá.
- `BOLA-02-After-Patch.png`: Phản hồi HTTP 404 sau khi vá.
- `BOLA-Access-Denied-Log.txt`: Log từ chối truy cập không đúng chủ sở hữu.

Thư mục minh chứng được loại trừ khỏi Git để tránh công khai dữ liệu có thể nhạy cảm.

## 12. Hạn chế và hướng phát triển

Giải pháp hiện tại chỉ được xác minh cho endpoint lấy vị trí phương tiện.

Các công việc cần tiếp tục gồm:

- Rà soát các endpoint khác có sử dụng ID đối tượng.
- Bổ sung kiểm thử tự động cho nhiều trường hợp phân quyền.
- Chuẩn hóa cơ chế xử lý lỗi và phản hồi từ chối truy cập.
- Đánh giá cơ chế giám sát và cảnh báo dựa trên log bảo mật.
- Kiểm tra khả năng áp dụng chính sách phân quyền nhất quán ở các dịch vụ khác.

## 13. Kết luận

Bản vá BOLA đã bổ sung bước kiểm tra quyền sở hữu phương tiện ở tầng nghiệp vụ, ngăn tài khoản Alice truy cập trái phép vị trí phương tiện của Bob trong kịch bản kiểm thử.

Kết quả kiểm thử cho thấy API chuyển từ phản hồi HTTP 200 có dữ liệu trước khi vá sang HTTP 404 không tiết lộ vị trí sau khi vá.

Giải pháp minh họa nguyên tắc quan trọng trong bảo mật REST API: xác thực người dùng phải đi kèm kiểm tra quyền truy cập trên từng đối tượng được yêu cầu.