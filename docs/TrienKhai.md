Viewed PROMPT_DO_AN_ATTT_SpringBoot.md:2-26

Dưới đây là hướng dẫn chi tiết từng bước để bạn tiến hành mô phỏng 2 kịch bản tấn công bảo mật trước giảng viên:

---

### ⚔️ KỊCH BẢN 1: Tấn công JWT Algorithm Confusion (CVE-2015-9235)

Mục tiêu kịch bản này là leo thang đặc quyền từ một tài khoản người dùng bình thường (`alice` với quyền `user`) lên quyền quản trị viên (`admin`) và lấy Flag bí mật mà không cần biết khóa bí mật RSA (Private Key) của hệ thống.

* **Bước 1: Đăng nhập bằng tài khoản thường**
  1. Mở trình duyệt truy cập Client App tại: **`http://localhost:3000`**
  2. Chọn kết nối tới server **Vulnerable (Cổng 4002)**.
  3. Đăng nhập với tài khoản: **`alice`** / mật khẩu: **`alice123`**
  4. Sau khi đăng nhập thành công, bạn sẽ thấy giao diện **Profile** hiển thị:
     * **JWT Token** (đang được ký bằng thuật toán `RS256`).
     * **RSA Public Key** dạng PEM.
     * Quyền hiện tại là **`user`** (màu xanh).
  5. Hãy nhấn nút **`Test Admin Panel (/api/admin)`** ở phía dưới $\rightarrow$ Kết quả sẽ trả về **`403 Forbidden`** (Bị chặn vì Alice không có quyền Admin).
  6. Nhấn nút **Copy** để sao chép chuỗi **`JWT Token`** của Alice.

* **Bước 2: Sử dụng Hacker Tool để giả mạo Token**
  1. Mở tab trình duyệt mới truy cập Hacker Tool tại: **`http://localhost:3001`** (mặc định mở trang *JWT Attack*).
  2. **Bước 1 trên Hacker Tool**: Nhập URL Target là `http://localhost:4002` (Server có lỗ hổng) $\rightarrow$ Click **`Fetch Public Key`** $\rightarrow$ Hệ thống sẽ tải về Public Key RSA công khai của server.
  3. **Bước 2 trên Hacker Tool**: Dán chuỗi JWT Token của Alice đã copy ở Bước 1 vào ô nhập liệu. Hệ thống sẽ tự động giải mã cấu trúc token.
  4. **Bước 3 trên Hacker Tool**: Nhấn nút **`Forge HS256 Token`** $\rightarrow$ Hacker Tool sẽ thực hiện:
     * Đổi thuật toán trên Header từ `RS256` thành `HS256`.
     * Thay đổi trường quyền lợi trên Payload thành `role: "admin"`.
     * Sử dụng **chính Public Key vừa tải về làm khóa bí mật đối xứng HMAC** để ký và tạo ra một JWT giả mạo mới.

* **Bước 3: Thực hiện tấn công**
  1. **Bước 4 trên Hacker Tool**: Nhấn nút **`Attack Vulnerable (4002)`**:
     * Giao diện log sẽ báo thành công: **`200 OK`** kèm Flag: `FLAG{jwt_alg_confusion_cve_2015_9235_pwned}`.
     * Giải thích: Server dính lỗi đọc thuật toán `alg` từ Client gửi lên. Khi thấy `HS256`, nó đã chuyển sang dùng chuỗi Public Key PEM (mà nó lưu công khai) để xác thực chữ ký HMAC $\rightarrow$ Chữ ký khớp hoàn toàn!
  2. Nhấn nút **`Test Secure (4001)`**:
     * Kết quả trả về **`401 Unauthorized`** kèm chi tiết: *Algorithm not allowed | Expected: RS256 only*.
     * Giải thích: Server an toàn đã giải mã Header thủ công trước và chặn đứng ngay khi thấy thuật toán không phải `RS256`, bảo vệ hệ thống trước CVE-2015-9235.

---

### 🔗 KỊCH BẢN 2: Tấn công OAuth2 Authorization Code Interception

Mục tiêu kịch bản này là chứng minh sự nguy hiểm khi OAuth2 Server không cấu hình kiểm duyệt danh sách trắng (Whitelist) các URL phản hồi (`redirect_uri`), dẫn tới việc Hacker có thể đánh cắp mã ủy quyền (Authorization Code) của nạn nhân.

* **Bước 1: Chuẩn bị tham số trên Hacker Tool**
  1. Trên Hacker Tool (`http://localhost:3001`), chuyển sang tab **`OAuth2 Attack`** ở menu bên trái.
  2. Các thông tin cấu hình mặc định đã được điền sẵn:
     * **OAuth2 Server URL**: `http://localhost:4003`
     * **Client ID**: `react-client-app` (App thật)
     * **Redirect URI**: `http://localhost:3001/callback` (URL thuộc tầm kiểm soát của Hacker)
  3. Nhấn nút **`Bắt đầu Authorization (Mở Consent Page)`** $\rightarrow$ Một cửa sổ Popup mới sẽ mở ra dẫn tới trang cấp quyền của OAuth Server.

* **Bước 2: Nạn nhân chấp nhận cấp quyền**
  1. Trên cửa sổ Popup cấp quyền (Port 4003), bạn sẽ thấy thông báo: *"App react-client-app đang yêu cầu quyền truy cập..."*.
  2. Cửa sổ này có dòng cảnh báo màu vàng: *redirect_uri không được validate*.
  3. Bạn nhấn nút **`Allow`** (Đồng ý cấp quyền).

* **Bước 3: Hacker chiếm đoạt mã ủy quyền & thông tin**
  1. Sau khi nhấn Allow, OAuth Server sẽ chuyển hướng trình duyệt về `http://localhost:3001/callback?code=...` (trang Callback của Hacker Tool).
  2. Trang callback này ngay lập tức bắt được mã **`code`** nhạy cảm và tự động gửi ngược về giao diện điều khiển chính của Hacker Tool thông qua cơ chế `postMessage`, sau đó popup sẽ hướng dẫn bạn đóng cửa sổ.
  3. Quay lại trang Hacker Tool, bạn sẽ thấy ô **`Captured Auth Code`** tại Bước 2 đã tự động điền mã code vừa chiếm đoạt được.
  4. **Bước 3**: Nhấn nút **`Exchange for Access Token`**:
     * Hệ thống gửi yêu cầu đổi code lấy Access Token và nhận về thành công chuỗi Access Token `at_xxxxxx`.
  5. **Bước 4**: Nhấn nút **`Get User Info`**:
     * Hacker Tool sử dụng Access Token để truy xuất dữ liệu cá nhân của người dùng từ OAuth Server thành công và in ra thông tin tài khoản (Tên, Email...) bị rò rỉ.