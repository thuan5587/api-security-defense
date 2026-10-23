# 01 – BASELINE: KHẢO SÁT LỖ HỔNG BOLA TRƯỚC KHI VÁ

## 1. Mục tiêu

Tài liệu này ghi nhận trạng thái bảo mật ban đầu của hệ thống crAPI trước khi triển khai biện pháp phòng chống lỗ hổng Broken Object Level Authorization (BOLA).

Mục tiêu kiểm thử là xác định liệu một người dùng đã đăng nhập có thể truy cập dữ liệu thuộc quyền sở hữu của người dùng khác bằng cách thay đổi mã định danh đối tượng trong yêu cầu API hay không.

## 2. Môi trường kiểm thử

| Thành phần | Thông tin |
|---|---|
| Hệ thống | OWASP crAPI |
| Môi trường | Docker Compose |
| API Gateway | http://localhost:8888 |
| Công cụ kiểm thử | Postman |
| Backend liên quan | crapi-identity |
| Phương thức HTTP | GET |
| Cơ chế xác thực | Bearer Token (JWT) |
| Đối tượng kiểm thử | Thông tin vị trí phương tiện |

Endpoint được khảo sát:

`GET /identity/api/v2/vehicle/{carId}/location`

## 3. Mô tả lỗ hổng

BOLA là lỗ hổng kiểm soát truy cập xảy ra khi API không xác minh đầy đủ quyền của người dùng đối với đối tượng được yêu cầu.

Trong kịch bản kiểm thử, hệ thống sử dụng JWT để xác thực người dùng. Tuy nhiên, ở phiên bản chưa vá, endpoint lấy vị trí phương tiện không kiểm tra đầy đủ liệu phương tiện được yêu cầu có thuộc sở hữu của người dùng hiện tại hay không.

Điều này cho phép một tài khoản hợp lệ truy cập thông tin phương tiện của tài khoản khác khi biết mã định danh phương tiện.

## 4. Kịch bản kiểm thử

Hai tài khoản được sử dụng:

- **Alice:** Người dùng thực hiện yêu cầu kiểm thử.
- **Bob:** Người dùng sở hữu phương tiện mục tiêu.

Các bước thực hiện:

1. Đăng nhập tài khoản Alice để lấy JWT hợp lệ.
2. Xác định mã định danh phương tiện thuộc tài khoản Bob trong môi trường lab.
3. Tạo yêu cầu GET đến endpoint vị trí phương tiện.
4. Sử dụng JWT của Alice trong phần Authorization.
5. Thay `{carId}` bằng mã phương tiện của Bob.
6. Gửi yêu cầu và ghi nhận phản hồi từ API.

Yêu cầu kiểm thử:

```http
GET /identity/api/v2/vehicle/b6ab7600-594f-4104-9ce8-d370a46e8ecc/location
Authorization: Bearer <JWT_ALICE>
```

## 5. Kết quả trước khi vá

Kết quả kiểm thử trên phiên bản chưa áp dụng bản vá:

| Tiêu chí | Kết quả |
|---|---|
| Người gửi yêu cầu | Alice |
| Chủ sở hữu phương tiện | Bob |
| JWT | Hợp lệ, thuộc Alice |
| HTTP Status | 200 OK |
| API trả dữ liệu vị trí | Có |
| Kiểm tra quyền sở hữu đối tượng | Không đầy đủ |
| Đánh giá | Tồn tại lỗ hổng BOLA |

Mặc dù Alice không phải chủ sở hữu phương tiện, API vẫn trả về dữ liệu liên quan đến phương tiện của Bob, bao gồm thông tin vị trí và một số thông tin nhận dạng.

Điều này chứng minh rằng việc xác thực JWT thành công chưa đủ để bảo đảm người dùng được phép truy cập mọi đối tượng trong hệ thống.

## 6. Phân tích nguyên nhân

Nguyên nhân chính là thiếu bước kiểm tra quyền sở hữu đối tượng tại tầng xử lý nghiệp vụ trước khi trả dữ liệu.

Luồng xử lý chưa an toàn:

1. API nhận yêu cầu cùng JWT.
2. Hệ thống xác thực người dùng.
3. Backend tìm phương tiện theo `carId`.
4. Backend trả thông tin vị trí mà không xác minh đầy đủ quyền sở hữu của người gửi yêu cầu.

Việc sử dụng mã định danh UUID không thể thay thế cho cơ chế phân quyền.

## 7. Tác động bảo mật

Lỗ hổng có thể dẫn đến:

- Truy cập trái phép dữ liệu của người dùng khác.
- Rò rỉ thông tin vị trí phương tiện.
- Tiết lộ thông tin nhận dạng liên quan đến chủ sở hữu.
- Vi phạm nguyên tắc kiểm soát truy cập ở cấp đối tượng.

## 8. Minh chứng kiểm thử

Ảnh chụp Postman trước khi vá được lưu tại:

`evidence/bola/BOLA-02-Before-Patch.png`

Ảnh ghi nhận phản hồi HTTP 200 khi sử dụng JWT của Alice để truy cập phương tiện của Bob.

Minh chứng gốc được lưu cục bộ, không đưa lên GitHub công khai vì có thể chứa dữ liệu cá nhân và thông tin nội bộ của môi trường kiểm thử.

## 9. Kết luận

Kết quả khảo sát xác nhận endpoint lấy vị trí phương tiện của crAPI tồn tại lỗ hổng BOLA trong phiên bản được kiểm thử trước khi vá.

Để khắc phục, backend cần xác định danh tính người dùng từ JWT và so sánh với chủ sở hữu của phương tiện trước khi cho phép truy cập dữ liệu.

Phần thiết kế và triển khai biện pháp phòng chống sẽ được trình bày trong tài liệu `02-bola-design.md`.