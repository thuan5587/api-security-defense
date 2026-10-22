# 05 – SO SÁNH TRƯỚC VÀ SAU KHI PHÒNG CHỐNG BOLA

## 1. Mục tiêu

Tài liệu này trình bày kết quả so sánh trước và sau khi áp dụng bản vá Broken Object Level Authorization (BOLA) trên hệ thống OWASP crAPI.

Mục tiêu là chứng minh hiệu quả của cơ chế kiểm tra quyền sở hữu đối tượng bằng cách sử dụng cùng một kịch bản kiểm thử trước và sau khi triển khai bản vá.

Endpoint được khảo sát:

`GET /identity/api/v2/vehicle/{carId}/location`

Phạm vi kết luận chỉ áp dụng cho endpoint và các kịch bản đã kiểm thử.

## 2. Môi trường và điều kiện so sánh

| Thành phần | Thông tin |
|---|---|
| Hệ thống | OWASP crAPI |
| API Gateway | http://localhost:8888 |
| Backend | crapi-identity |
| Công cụ kiểm thử | Postman |
| Môi trường triển khai | Docker Compose |
| Cơ chế xác thực | JWT Bearer Token |
| Phiên bản trước vá | Backend crAPI gốc |
| Phiên bản sau vá | crapi-identity-bola-defense:v2 |

Hai tài khoản kiểm thử:

- Alice: Người gửi yêu cầu truy cập.
- Bob: Chủ sở hữu phương tiện mục tiêu.

Phương tiện của Bob:

`b6ab7600-594f-4104-9ce8-d370a46e8ecc`

## 3. Kịch bản kiểm thử chung

Để đánh giá sự khác biệt, cùng một tình huống truy cập được thực hiện trên hai phiên bản backend.

Các bước:

1. Đăng nhập tài khoản Alice và lấy JWT hợp lệ.
2. Xác định mã phương tiện thuộc sở hữu Bob.
3. Gửi yêu cầu GET lấy vị trí phương tiện Bob.
4. Sử dụng JWT của Alice trong phần Authorization.
5. Ghi nhận HTTP Status và dữ liệu phản hồi.
6. So sánh kết quả giữa phiên bản trước và sau khi vá.

Yêu cầu:

```http
GET /identity/api/v2/vehicle/b6ab7600-594f-4104-9ce8-d370a46e8ecc/location
Authorization: Bearer <JWT_ALICE>
```

## 4. Kết quả trước khi vá (Before Patch)

Khi sử dụng backend crAPI gốc, yêu cầu của Alice đến phương tiện Bob trả về:

**HTTP Status: 200 OK**

API cung cấp thông tin liên quan đến vị trí phương tiện và dữ liệu nhận dạng mặc dù Alice không phải chủ sở hữu.

Điều này chứng minh rằng cơ chế xác thực JWT đã được sử dụng nhưng kiểm tra quyền sở hữu đối tượng tại endpoint chưa đầy đủ.

### 4.1. Phân tích bảo mật

- Alice có JWT hợp lệ.
- Phương tiện được yêu cầu thuộc Bob.
- Backend vẫn trả về dữ liệu của phương tiện Bob.
- Quyền truy cập cấp đối tượng chưa được thực thi đầy đủ.

**Đánh giá: Không đạt yêu cầu bảo mật do tồn tại BOLA.**

### 4.2. Minh chứng

Ảnh chụp Postman:

`evidence/bola/BOLA-02-Before-Patch.png`

Ảnh thể hiện yêu cầu truy cập phương tiện Bob và phản hồi HTTP 200.

## 5. Giải pháp được triển khai

Bản vá được thực hiện tại tầng nghiệp vụ của dịch vụ `crapi-identity`.

Các thành phần được điều chỉnh:

- `VehicleController.java`
- `VehicleServiceImpl.java`
- Các bài kiểm thử liên quan trong `VehicleServiceImplTest.java`

### 5.1. Xác định người dùng hiện tại

Backend sử dụng thông tin xác thực trong yêu cầu để xác định người dùng hiện tại thông qua `userService.getUserFromToken(request)`.

### 5.2. Kiểm tra quyền sở hữu phương tiện

Sau khi truy xuất phương tiện theo `carId`, backend so sánh ID người dùng hiện tại với ID chủ sở hữu phương tiện.

Đoạn mã kiểm tra:

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

Nếu không phải chủ sở hữu, Service không cung cấp dữ liệu vị trí; Controller trả về HTTP 404.

### 5.3. Ghi log bảo mật

Khi phát hiện truy cập sai quyền sở hữu, hệ thống ghi sự kiện:

`BOLA_ACCESS_DENIED`

Thông điệp chứa loại sự kiện, phương thức HTTP, mã phương tiện, ID người gửi yêu cầu và lý do từ chối.

## 6. Kết quả sau khi vá (After Patch)

Sau khi triển khai Docker image:

`crapi-identity-bola-defense:v2`

Yêu cầu Alice truy cập phương tiện Bob trả về:

**HTTP Status: 404 Not Found**

API không trả về thông tin vị trí phương tiện của Bob.

Backend đồng thời ghi log từ chối quyền sở hữu.

### 6.1. Phân tích bảo mật

- Alice vẫn sử dụng JWT hợp lệ.
- Phương tiện mục tiêu vẫn thuộc Bob.
- Backend kiểm tra quyền sở hữu trước khi trả dữ liệu.
- Quyền sở hữu không hợp lệ dẫn đến từ chối truy cập.
- Không có dữ liệu vị trí bị tiết lộ trong phản hồi kiểm thử.

**Đánh giá: Đạt yêu cầu bảo mật đối với kịch bản BOLA đã khảo sát.**

### 6.2. Minh chứng

Ảnh chụp Postman:

`evidence/bola/BOLA-02-After-Patch.png`

Log từ chối:

`evidence/bola/BOLA-Access-Denied-Log.txt`

## 7. Bảng so sánh Before/After

| Tiêu chí | Before Patch | After Patch |
|---|---|---|
| Tài khoản gửi yêu cầu | Alice | Alice |
| JWT | Hợp lệ | Hợp lệ |
| Chủ sở hữu phương tiện | Bob | Bob |
| Endpoint | Vehicle Location | Vehicle Location |
| Kiểm tra quyền sở hữu | Không đầy đủ | Có |
| HTTP Status | 200 OK | 404 Not Found |
| Trả dữ liệu vị trí Bob | Có | Không |
| Log từ chối quyền sở hữu | Chưa có cơ chế log mới | Có |
| Đánh giá | Tồn tại BOLA | Đã chặn kịch bản kiểm thử |

## 8. So sánh luồng xử lý

### 8.1. Trước khi vá

1. Alice gửi yêu cầu kèm JWT hợp lệ.
2. Backend xác định người dùng đã đăng nhập.
3. Backend tìm phương tiện Bob theo `carId`.
4. Backend trả dữ liệu vị trí mà không kiểm tra đầy đủ quyền sở hữu.
5. Alice nhận HTTP 200 cùng dữ liệu không thuộc quyền truy cập.

### 8.2. Sau khi vá

1. Alice gửi yêu cầu kèm JWT hợp lệ.
2. Backend xác định danh tính Alice.
3. Backend tìm phương tiện Bob theo `carId`.
4. Backend so sánh ID Alice với ID chủ sở hữu Bob.
5. Kết quả không trùng khớp.
6. Backend ghi log `BOLA_ACCESS_DENIED`.
7. API trả HTTP 404, không cung cấp vị trí phương tiện.

## 9. Kiểm tra ảnh hưởng đến chức năng hợp lệ

Ngoài kịch bản truy cập trái phép, các trường hợp hợp lệ cũng đã được kiểm thử:

| Trường hợp | Kết quả sau vá |
|---|---|
| Alice truy cập xe Alice | 200 OK |
| Bob truy cập xe Bob | 200 OK |
| Alice truy cập xe Bob | 404 Not Found |

Kết quả cho thấy bản vá ngăn truy cập sai quyền sở hữu trong khi vẫn duy trì chức năng truy cập của chủ sở hữu phương tiện.

## 10. Ý nghĩa của kết quả

Sự thay đổi từ HTTP 200 sang HTTP 404 không đơn thuần là thay đổi mã trạng thái.

Điểm quan trọng là backend đã bổ sung bước xác minh quyền sở hữu đối tượng trước khi trả dữ liệu.

Khi kiểm tra không thành công, hệ thống không cung cấp dữ liệu vị trí cho người gửi yêu cầu.

Kết quả minh họa rằng xác thực JWT và phân quyền truy cập đối tượng là hai yêu cầu bảo mật khác nhau, cần được thực hiện đồng thời.

## 11. Hạn chế và lưu ý

- Bản vá mới được kiểm chứng trên endpoint lấy vị trí phương tiện.
- Chưa có cơ sở khẳng định toàn bộ hệ thống crAPI không còn BOLA.
- HTTP 404 là cách xử lý của bản vá hiện tại; các hệ thống khác có thể lựa chọn phản hồi phù hợp với chính sách API.
- Các minh chứng Postman và log cần được bảo vệ trước khi chia sẻ công khai.
- Kết quả kiểm thử phản ánh môi trường lab tại thời điểm thực hiện.

## 12. Kết luận

Quá trình so sánh Before/After cho thấy bản vá đã giải quyết kịch bản BOLA được phát hiện tại endpoint lấy vị trí phương tiện.

Trước khi vá, Alice sử dụng JWT hợp lệ để truy cập phương tiện Bob và nhận HTTP 200 cùng dữ liệu không thuộc quyền truy cập.

Sau khi vá, yêu cầu tương ứng bị từ chối bằng HTTP 404, không tiết lộ vị trí phương tiện và tạo log bảo mật.

Kết quả này chứng minh hiệu quả của việc kiểm tra quyền sở hữu đối tượng tại tầng nghiệp vụ trong phạm vi được kiểm thử.