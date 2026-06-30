# JWT and OAuth2 Security Demo

Dự án mô phỏng hai lỗ hổng bảo mật thực tế:

1. **OAuth2 Login CSRF** — Missing State Parameter Validation
2. **JWT Algorithm Confusion** — CVE-2015-9235

Mỗi mô phỏng đều có server vulnerable và server secure để so sánh trực tiếp.

---

## Yêu Cầu Hệ Thống

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| Java JDK | 21 trở lên | Đã test với JDK 21 và 25 |
| Apache Maven | 3.8 trở lên | Hoặc dùng IntelliJ built-in |
| Node.js | 18 trở lên | Để chạy client và hacker-tool |
| Burp Suite | Community hoặc Pro | Để thực hiện demo tấn công |
| Trình duyệt | Chrome hoặc Firefox | Cần hỗ trợ mở tab Incognito |

---

## Cấu Hình Google OAuth2 (Bắt Buộc)

Cả hai mô phỏng đều dùng Google làm Authorization Server thực tế.

**Bước 1 — Tạo OAuth2 Client trên Google Cloud:**

1. Mở https://console.cloud.google.com/
2. Chọn project hoặc tạo project mới
3. Vào mục "APIs & Services" → "Credentials"
4. Nhấn "Create Credentials" → "OAuth 2.0 Client ID"
5. Application type: chọn "Web application"
6. Đặt tên tùy ý, ví dụ: "Security Demo"

**Bước 2 — Thêm Authorized Redirect URIs:**

Thêm đủ tất cả các URI sau vào mục "Authorized redirect URIs":

```
http://localhost:4001/oauth2/callback
http://localhost:4002/oauth2/callback
http://localhost:3000/callback
```

**Bước 3 — Nhập trực tiếp thông tin vào dự án (Không dùng file env):**

Mở các file cấu hình sau và dán trực tiếp Client ID và Client Secret đã lấy được:

1. **Vulnerable Server**: Mở file `server-vulnerable/src/main/resources/application.properties` (dòng 31-32):
   ```properties
   google.client-id=YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com
   google.client-secret=YOUR_GOOGLE_CLIENT_SECRET
   ```

2. **Secure Server**: Mở file `server-secure/src/main/resources/application.yml` (dòng 51-52):
   ```yaml
   google:
     client-id: YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com
     client-secret: YOUR_GOOGLE_CLIENT_SECRET
   ```

---

## Cấu Trúc Dự Án

```
jwt-attack-demo/
  server-vulnerable/    Spring Boot, port 4002 — chứa cả hai lỗ hổng
  server-secure/        Spring Boot, port 4001 — phiên bản đã vá
  client/               React app, port 3000 — giao diện demo JWT
  hacker-tool/          React app, port 3001 — công cụ tấn công JWT
  docker-compose.yml    Khởi động cả hai backend cùng lúc
  README.md             Tài liệu này
```

---

## Khởi Động Dự Án

### Cách 1 — Chạy Thủ Công (Khuyên Dùng Khi Demo)

Mở 4 terminal riêng biệt, chạy theo thứ tự sau:

**Terminal 1 — Vulnerable Server (port 4002)**

```powershell
cd server-vulnerable
mvn spring-boot:run
```

Chờ đến khi log hiển thị:
```
Started VulnerableApplication in X seconds
```

**Terminal 2 — Secure Server (port 4001)**

```powershell
cd server-secure
mvn spring-boot:run
```

Chờ đến khi log hiển thị:
```
Started SecureApplication in X seconds
```

**Terminal 3 — Client App (port 3000)**

```bash
cd client
npm install
npm run dev
```

**Terminal 4 — Hacker Tool (port 3001)**

```bash
cd hacker-tool
npm install
npm run dev
```

### Cách 2 — Chạy Bằng Docker

```powershell
docker-compose up --build
```

Sau đó khởi động client và hacker-tool thủ công như Terminal 3 và 4 ở trên.

### Xác Nhận Khởi Động Thành Công

Mở browser, kiểm tra từng URL:

| URL | Kết quả mong đợi |
|---|---|
| http://localhost:4002/health | `{"status":"UP","server":"vulnerable"}` |
| http://localhost:4001/health | `{"status":"UP","server":"secure"}` |
| http://localhost:3000 | Giao diện đăng nhập |
| http://localhost:3001 | Giao diện hacker tool |

---

## Tài Khoản Test

Cả hai server tự động tạo dữ liệu mẫu khi khởi động:

| Username | Password | Role |
|---|---|---|
| admin | admin123 | ADMIN |
| alice | alice123 | USER |
| bob | bob123 | MODERATOR |

---

## Danh Sách Các URL Quan Trọng

| Mô tả | URL |
|---|---|
| Client app chính | http://localhost:3000 |
| Hacker tool | http://localhost:3001 |
| Vulnerable — OAuth2 login page | http://localhost:4002/oauth2/login |
| Secure — OAuth2 login page | http://localhost:4001/oauth2/login |
| Vulnerable — Health check | http://localhost:4002/health |
| Secure — Health check | http://localhost:4001/health |
| Vulnerable — RSA public key | http://localhost:4002/api/public-key |
| Secure — RSA public key | http://localhost:4001/api/public-key |

---

# MÔ PHỎNG 1 — OAuth2 Login CSRF (Missing State Validation)

## Tổng Quan Lý Thuyết

OAuth2 Authorization Code Flow yêu cầu tham số `state` để chống tấn công CSRF.
Mục đích của `state`:

- Client sinh ra một giá trị ngẫu nhiên trước khi chuyển hướng đến Google.
- Giá trị đó được lưu trữ phía máy chủ (server-side, ví dụ trong HttpSession).
- Khi Google chuyển hướng về callback, giá trị `state` phải khớp với giá trị đã lưu trong session.
- Nếu không khớp, server phải từ chối xử lý — đây chính là cơ chế bảo vệ CSRF.

**Lưu đồ trong OAuth2 Authorization Code Flow (RFC 6749, Section 4.1):**

```
Bước 1: Client -> Google
  GET /o/oauth2/v2/auth
    ?client_id=...
    &redirect_uri=http://localhost:4001/oauth2/callback
    &response_type=code
    &scope=openid profile email
    &state=ABC123XYZ          <- state ngẫu nhiên, lưu vào session

Bước 2: User đăng nhập tại Google

Bước 3: Google -> Client (redirect về callback)
  GET /oauth2/callback
    ?code=AUTHORIZATION_CODE
    &state=ABC123XYZ          <- phải khớp với state trong session

Bước 4 (Quan trọng): Server kiểm tra
  session["oauth_state"] == request.param["state"] ?
    -> Khớp: tiếp tục đổi code lấy token
    -> Không khớp: trả về HTTP 400, từ chối

Bước 5: Đổi authorization_code lấy access_token (server-to-server)
  POST https://oauth2.googleapis.com/token
    code=AUTHORIZATION_CODE
    client_id=...
    client_secret=...

Bước 6: Dùng access_token lấy thông tin user
  GET https://www.googleapis.com/oauth2/v3/userinfo
    Authorization: Bearer ACCESS_TOKEN
```

## Kịch Bản Tấn Công — Account Binding CSRF

Kịch bản: Kẻ tấn công (Attacker) muốn buộc nạn nhân (Victim) đăng nhập vào hệ thống bằng tài khoản Google của Attacker.
Kết quả: Victim tin rằng họ đang sử dụng tài khoản của mình, nhưng thực ra đang thao tác trong phiên làm việc (session) của Attacker.

```
Attacker                     Victim                    Google
   |                            |                         |
   |-- Mở OAuth2 login page --> |                         |
   |                            |                         |
   |-- Click đăng nhập Google --|------------------------>|
   |                            |                         |
   |<-- Google redirect về -----|-------------------------|
   |    callback?code=CODE_A    |                         |
   |                            |                         |
   |-- Chặn callback, lấy CODE_A và URL đầy đủ             |
   |   Không forward, tắt Burp intercept                  |
   |                            |                         |
   |-- Gửi link callback cho Victim (qua email, chat)      |
   |   http://localhost:4002/oauth2/callback?code=CODE_A  |
   |                            |                         |
   |                            |-- Victim click link ---->
   |                            |                         |
   |                            |-- Server nhận CODE_A ---|
   |                            |   đổi lấy token         |
   |                            |<-- Thông tin Google     |
   |                            |   của Attacker!         |
   |                            |                         |
   |                            |-- Victim bị đăng nhập --|
   |                               bằng account Attacker  |
```

**Hậu quả thực tế:** Victim thao tác trên hệ thống nhưng mọi hành động được ghi nhận vào tài khoản của Attacker. Nếu hệ thống có tính năng "liên kết tài khoản mạng xã hội", tài khoản Google của Attacker sẽ bị gắn trực tiếp vào hồ sơ (profile) của Victim.

---

## Bước 1 — Chuẩn Bị Môi Trường Demo

**1. Kiểm tra cả hai server đã chạy:**

```
http://localhost:4002/health   -> {"status":"UP","server":"vulnerable"}
http://localhost:4001/health   -> {"status":"UP","server":"secure"}
```

**2. Cấu hình Burp Suite:**

- Mở Burp Suite.
- Vào tab "Proxy" -> "Options".
- Kiểm tra proxy đang lắng nghe tại `127.0.0.1:8080`.
- Vào tab "Proxy" -> "Intercept".
- Đảm bảo nút "Intercept is on" đang bật.

**3. Cấu hình trình duyệt (browser):**

- Vào cài đặt proxy của trình duyệt (hoặc dùng tiện ích mở rộng FoxyProxy).
- Đặt HTTP proxy: `127.0.0.1`, port: `8080`.
- Tắt tùy chọn "Bypass proxy for localhost" nếu có.

**4. Cài chứng chỉ Burp Suite vào trình duyệt** (để tránh bị lỗi bảo mật SSL):

- Mở địa chỉ http://burpsuite trên trình duyệt (khi proxy đang bật).
- Tải CA Certificate và tiến hành cài đặt vào mục Certificate Authority của trình duyệt.

**5. Mở hai cửa sổ trình duyệt riêng biệt:**

- Cửa sổ 1 (Trình duyệt thường): Đóng vai Attacker.
- Cửa sổ 2 (Trình duyệt ẩn danh - Incognito/Private): Đóng vai Victim.

---

## Demo 1A — Vulnerable Server (Port 4002): Tấn Công CSRF Thành Công

### Pha 1 — Attacker Lấy Authorization Code

**Bước 1:** Trên cửa sổ trình duyệt của Attacker, truy cập:
```
http://localhost:4002/oauth2/login
```

**Bước 2:** Trang đăng nhập hiển thị. Nhấn nút "Đăng nhập bằng Google".

**Bước 3:** Burp Suite sẽ bắt được request HTTP. Quan sát nội dung trong tab Request của Burp:

```http
GET /oauth2/login HTTP/1.1
Host: localhost:4002
```

Nhấn nút "Forward" trong Burp Suite để cho request đi qua.

**Bước 4:** Burp Suite bắt tiếp request chuyển hướng sang Google. Quan sát URL đích:

```
https://accounts.google.com/o/oauth2/v2/auth
  ?client_id=YOUR_CLIENT_ID
  &redirect_uri=http%3A%2F%2Flocalhost%3A4002%2Foauth2%2Fcallback
  &response_type=code
  &scope=openid+profile+email
  &access_type=offline
```

Lưu ý: URL này **không hề có tham số state**. Đây là lỗ hổng của server-vulnerable.

Nhấn nút "Forward" để gửi yêu cầu đi. Sau đó tạm thời tắt "Intercept" (click chọn "Intercept is off") để trình duyệt có thể tải trang đăng nhập của Google.

**Bước 5:** Trên cửa sổ trình duyệt của Attacker, thực hiện đăng nhập vào tài khoản Google cá nhân của Attacker (ví dụ tài khoản A).

**Bước 6:** Bật lại tính năng "Intercept is on" trong Burp Suite ngay sau khi đăng nhập xong trên Google.

**Bước 7:** Google hoàn tất xác thực và chuyển hướng trình duyệt về callback URL của server. Burp Suite sẽ bắt lại request redirect này:

```http
GET /oauth2/callback?code=4%2F0XXXXXXXXXXXXX&state= HTTP/1.1
Host: localhost:4002
```

Đây chính là Authorization Code của tài khoản Google của Attacker. **Tuyệt đối không nhấn Forward.**

**Bước 8:** Sao chép (Copy) toàn bộ địa chỉ URL của request này trong Burp Suite:

```
http://localhost:4002/oauth2/callback?code=4/0XXXXXXXXXXXXX&state=
```

**Bước 9:** Nhấn nút "Drop" trong Burp Suite để hủy bỏ request này. Việc này nhằm ngăn chặn Attacker tự hoàn tất đăng nhập trên trình duyệt của chính mình.

Tắt "Intercept" (chuyển sang "Intercept is off").

### Pha 2 — Attacker Gửi Link Cho Victim

Attacker gửi URL callback vừa sao chép ở bước 8 cho Victim (thực hiện gửi qua email, tin nhắn...). Trong quá trình demo, bạn hãy dán URL đó vào thanh địa chỉ của cửa sổ trình duyệt ẩn danh (đóng vai Victim).

**Lưu ý quan trọng:** Authorization Code của Google chỉ có hiệu lực sử dụng **một lần duy nhất** và tự động hết hạn sau khoảng **10 phút**. Vì vậy, bạn cần thực hiện thao tác gửi/mở link này nhanh chóng.

### Pha 3 — Victim Click Link Và Kết Quả

**Bước 1:** Trên cửa sổ ẩn danh (Victim), dán URL callback của Attacker vào thanh địa chỉ và nhấn Enter.

**Bước 2:** Server nhận request và xử lý. Quan sát log hiển thị trong Terminal 1 (server-vulnerable):

```
[VULNERABLE] BƯỚC 2: Callback nhận code
[VULNERABLE] code    = 4/0XXXXXXXXXXXXX
[VULNERABLE] state   = '' (KHÔNG VALIDATE — LỖ HỔNG!)
[VULNERABLE] STATE VALIDATION: BỎ QUA HOÀN TOÀN!
[VULNERABLE] BƯỚC 3: Đổi code lấy access token...
[VULNERABLE] Access token nhận được: ya29.XXXXX...
[VULNERABLE] BƯỚC 4: Lấy user info từ Google...
[VULNERABLE] User Info: email=attacker@gmail.com, name=Attacker Name
[VULNERABLE] BƯỚC 5: ĐĂNG NHẬP THÀNH CÔNG!
[VULNERABLE] Nếu đây là CSRF: Victim vừa đăng nhập bằng account của ATTACKER!
```

**Bước 3:** Trình duyệt ẩn danh của Victim tự động chuyển hướng và hiển thị trang thông tin cá nhân (Profile) với:

- Tên hiển thị: Tên tài khoản Google của Attacker.
- Email: Email Google của Attacker.
- Ảnh đại diện: Ảnh đại diện của Attacker.
- Google ID: ID Google của Attacker.

**Kết luận:** Cuộc tấn công CSRF thành công. Victim đã bị liên kết và đăng nhập nhầm vào hệ thống dưới danh nghĩa tài khoản Google của Attacker mà không hề hay biết hay phải nhập thông tin đăng nhập của mình.

---

## Demo 1B — Secure Server (Port 4001): CSRF Bị Chặn Đứng

### Phương án 1 — Sử Dụng Link Callback Của Attacker

**Bước 1:** Victim mở trang đăng nhập của Secure Server:
```
http://localhost:4001/oauth2/login
```

**Bước 2:** Quan sát log hiển thị trong Terminal 2 (server-secure):
```
[SECURE] BƯỚC 1: Authorization Request
[SECURE] Generated state = 'k3mN7pQ1xR8vL2jH...'
[SECURE] Saved to session[oauth_state] = 'k3mN7pQ1xR8vL2jH...'
```

**Bước 3:** Trên cửa sổ trình duyệt của Attacker, thực hiện quy trình đăng nhập lấy callback URL giống như Demo 1A nhưng chạy trên secure server (port 4001). Bạn sẽ thu được URL callback có dạng:

```
http://localhost:4001/oauth2/callback?code=ATTACKER_CODE&state=
```

**Bước 4:** Gửi URL này cho Victim (dán vào cửa sổ trình duyệt ẩn danh của Victim).

**Bước 5:** Quan sát log trên server-secure ngay khi link được mở:

```
[SECURE] BƯỚC 2: Callback nhận được
[SECURE] callback state = '' (trống)
[SECURE] session  state = 'k3mN7pQ1xR8vL2jH...'
[SECURE] STATE VALIDATION FAILED: Callback không có state!
[SECURE] CSRF ATTACK DETECTED! Từ chối yêu cầu.
```

**Bước 6:** Trình duyệt ẩn danh của Victim lập tức hiển thị trang thông báo lỗi bảo mật:

```
CSRF Attack Detected!
Lý do: Invalid OAuth State — Callback thiếu tham số state.

Callback state : (trống)
Session  state : k3mN7pQ1xR8vL2jH...
```

### Phương án 2 — Chỉnh Sửa State Trong Burp Suite

**Bước 1:** Victim bắt đầu nhấn đăng nhập bình thường tại địa chỉ http://localhost:4001/oauth2/login

**Bước 2:** Bật tính năng "Intercept is on" trong Burp Suite.

**Bước 3:** Victim tiến hành đăng nhập tài khoản Google của mình. Khi Google redirect về, Burp Suite sẽ bắt được request:

```http
GET /oauth2/callback?code=VICTIM_CODE&state=k3mN7pQ1xR8vL2jH HTTP/1.1
Host: localhost:4001
Cookie: JSESSIONID=xxxxxxxxxxxxx
```

**Bước 4:** Tại giao diện của Burp Suite, chỉnh sửa tham số `state` trong request thành một giá trị ngẫu nhiên khác:

```
state=FAKE_STATE_INJECTED_BY_ATTACKER
```

**Bước 5:** Nhấn nút "Forward" để gửi request đã sửa lên server.

**Bước 6:** Secure Server tiến hành so sánh đối chiếu:
```
Callback state : FAKE_STATE_INJECTED_BY_ATTACKER
Session  state : k3mN7pQ1xR8vL2jH
Kết quả        : KHÔNG KHỚP -> TỪ CHỐI
```

**Bước 7:** Trình duyệt hiển thị thông báo lỗi "CSRF Attack Detected! State không khớp."

### Giải Thích Tại Sao CSRF Bị Chặn Đứng

Khi Victim truy cập trang đăng nhập của Secure Server, hệ thống sinh ra một chuỗi state ngẫu nhiên bằng bộ sinh số ngẫu nhiên an toàn (`SecureRandom`) và lưu trữ nó trực tiếp trên server-side (liên kết với Session ID lưu trong cookie `JSESSIONID` của Victim).

Khi Attacker gửi link callback của mình cho Victim, link đó hoặc là không đi kèm tham số `state` (do vulnerable server trước đó không sinh), hoặc đi kèm một giá trị `state` không khớp với giá trị đang được lưu trữ trong session của Victim.

Do sự bất nhất này, Secure Server phát hiện bất thường và từ chối xử lý Authorization Code, bảo vệ Victim khỏi việc bị đăng nhập nhầm vào tài khoản của Attacker.

---

---

# MÔ PHỎNG 2 — JWT Algorithm Confusion (CVE-2015-9235)

## Tổng Quan Lý Thuyết

Một token JWT (JSON Web Token) tiêu chuẩn gồm 3 phần phân tách bằng dấu chấm: Header, Payload, và Signature.
Phần Header chứa trường `alg` dùng để khai báo thuật toán ký và kiểm tra chữ ký.

Lỗ hổng: Các thư viện xử lý JWT cũ bị lỗi khi tin tưởng hoàn toàn vào giá trị trường `alg` do phía client gửi lên để quyết định thuật toán giải mã và kiểm thử chữ ký. Attacker lợi dụng điểm này để thay đổi thuật toán kiểm thử nhằm đánh lừa server dùng sai khóa xác minh.

**Kịch bản tấn công đổi từ thuật toán bất đối xứng RS256 sang thuật toán đối xứng HS256:**

```
Server ký token bằng: RS256 (Dùng khóa bí mật RSA private key để ký)
Server kiểm thử bằng: RS256 (Dùng khóa công khai RSA public key để xác minh) — Quy trình chuẩn

Kẻ tấn công thực hiện:
1. Lấy khóa công khai RSA public key từ endpoint công cộng (ví dụ: `/api/public-key`)
2. Tạo một token giả mạo, đổi giá trị trường "alg" trong phần header từ "RS256" thành "HS256"
3. Thay đổi thông tin phân quyền trong payload (ví dụ chỉnh role từ "user" thành "admin")
4. Ký token giả mạo này bằng thuật toán HMAC-SHA256 (HS256) với khóa ký chính là... chuỗi public key lấy ở bước 1
5. Thư viện JWT bị lỗi ở server đọc header thấy alg=HS256 -> tự động dùng khóa public key (vốn là chuỗi chữ thường công khai) làm HMAC secret đối xứng để verify chữ ký
6. Chữ ký khớp hoàn toàn và server chấp nhận quyền admin giả mạo!
```

**Kịch bản tấn công thuật toán alg=none:**

```
Attacker tạo token giả mạo và đổi trường "alg" trong header thành "none"
Thư viện JWT bị lỗi đọc thấy alg=none -> tự động bỏ qua bước kiểm tra chữ ký
Token giả mạo được chấp nhận mặc dù không hề có chữ ký hợp lệ
```

---

## Demo 2A — Vulnerable Server: JWT Algorithm Confusion

### Chuẩn Bị

**Bước 1:** Mở trình duyệt và truy cập trang web chính tại địa chỉ http://localhost:3000.

**Bước 2:** Chọn mục kết nối đến server "Vulnerable (4002)" bằng nút toggle ở góc trên bên phải giao diện.

**Bước 3:** Tiến hành đăng nhập bằng tài khoản người dùng thông thường (ví dụ: `alice` mật khẩu `alice123`).

**Bước 4:** Truy cập vào trang thông tin cá nhân (Profile). Tại đây giao diện sẽ hiển thị:
- Chuỗi JWT token hiện tại của bạn.
- Khóa RSA Public Key công khai của server.
- Phần Header và Payload đã được giải mã của JWT hiện tại.

**Bước 5:** Mở địa chỉ http://localhost:3001 (Hacker Tool) ở một tab trình duyệt khác.

### Các Bước Thực Hiện Tấn Công

**Bước 1:** Trên giao diện Hacker Tool, hãy dán JWT token hiện tại và chuỗi khóa RSA Public Key vừa lấy được vào các ô nhập liệu tương ứng.

**Bước 2:** Lựa chọn phương thức tấn công:

Lựa chọn A — Algorithm Confusion (RS256 -> HS256):
- Chọn tab "HS256 Attack".
- Công cụ tấn công sẽ tự động thực hiện:
  - Thay thế `alg` trong Header từ `RS256` thành `HS256`.
  - Thay đổi thông tin phân quyền trong Payload (chỉnh sửa `role` từ `user` thành `admin`).
  - Ký lại token bằng thuật toán HMAC-SHA256 với khóa ký đối xứng chính là chuỗi RSA Public Key.
- Nhấn nút "Generate Token".

Lựa chọn B — None Algorithm Attack:
- Chọn tab "None Attack".
- Công cụ sẽ:
  - Chỉnh sửa `alg` trong Header thành `none`.
  - Chỉnh sửa quyền `role` trong Payload thành `admin`.
  - Xóa bỏ hoặc để trống phần chữ ký (signature) phía sau dấu chấm thứ 2 của JWT.
- Nhấn nút "Generate Token".

**Bước 3:** Sao chép (Copy) chuỗi token giả mạo vừa được tạo ra.

**Bước 4:** Quay lại trình duyệt tab chứa trang Profile của ứng dụng chính (http://localhost:3000).

**Bước 5:** Mở Developer Tools của trình duyệt (F12), chọn tab Console và thực thi dòng lệnh sau để ghi đè token cũ bằng token giả mạo:

```javascript
localStorage.setItem('jwt_token', 'CHUOI_TOKEN_GIA_MAO_CUA_BAN')
```

**Bước 6:** Tải lại trang (nhấn F5).

**Bước 7:** Nhấn nút "Test /api/admin". Nếu cuộc tấn công thành công, server sẽ phản hồi HTTP 200 và trả về nội dung của trang quản trị admin dù tài khoản đăng nhập ban đầu (`alice`) không hề có quyền hạn này.

**Log ghi nhận trong Terminal 1 (server-vulnerable) sẽ hiển thị:**
```
[JWT-LIB] Auto-detected algorithm from token header: HS256
[JWT-LIB] HMAC algorithm detected -> using provided key as HMAC secret
[VULNERABLE] Token verified: sub=alice role=admin
```

### Giải Thích Tại Sao Tấn Công Thành Công

Server Vulnerable sử dụng hàm kiểm tra xác thực JWT bị lỗi logic: thư viện đọc trực tiếp trường `alg` từ Header do người dùng gửi lên để tự động cấu hình thuật toán giải mã chữ ký. Khi thấy Header khai báo `HS256`, thư viện chuyển sang kiểm tra theo kiểu khóa đối xứng HMAC và dùng tệp khóa công khai RSA Public Key (dưới dạng mảng byte) để làm khóa mật mã kiểm tra. Vì Attacker đã dùng chính khóa công khai đó để ký token bằng HS256, chữ ký được xác nhận hợp lệ và token giả mạo được chấp nhận.

---

## Demo 2B — Secure Server: JWT Algorithm Confusion Bị Chặn Đứng

### Các Bước Thực Hiện

**Bước 1:** Chuyển kết nối ứng dụng sang server "Secure (4001)" bằng nút toggle tại địa chỉ http://localhost:3000.

**Bước 2:** Đăng nhập bằng tài khoản người dùng bình thường: `alice` / `alice123`.

**Bước 3:** Thực hiện tương tự quy trình ở Demo 2A để tạo một token giả mạo bằng Hacker Tool (sử dụng thuật toán `HS256` hoặc `none`).

**Bước 4:** Thay thế token giả mạo vào `localStorage` của trình duyệt và nhấn F5 để tải lại trang.

**Bước 5:** Nhấn nút "Test /api/admin". Server lúc này sẽ trả về lỗi từ chối truy cập HTTP 401 với nội dung chi tiết:

```json
{
  "success": false,
  "error": "Algorithm not allowed",
  "detail": "Received: HS256 | Expected: RS256 only | Protected against CVE-2015-9235",
  "serverType": "secure"
}
```

**Log ghi nhận trong Terminal 2 (server-secure) sẽ hiển thị:**
```
[SECURE] JWT header alg = HS256
[SECURE] Algorithm 'HS256' BỊ TỪ CHỐI — chỉ chấp nhận RS256!
```

### Giải Thích Tại Sao Tấn Công Thất Bại

Server Secure được lập trình để tự phân tích (parse) thủ công phần JWT Header trước khi gọi thư viện kiểm tra chữ ký. Server kiểm tra giá trị khai báo của trường `alg` và chỉ cho phép duy nhất thuật toán bất đối xứng `RS256`. Mọi token sử dụng thuật toán khác (bao gồm cả `HS256`, `HS512` hay `none`) đều bị hệ thống phát hiện, từ chối và chặn đứng ngay lập tức mà không cần tiến hành các bước xác minh chữ ký tiếp theo.

---

---

# So Sánh Hai Server

## Phần 1: OAuth2 State Validation (Mô Phỏng 1)

| Tiêu chí | Vulnerable Server (4002) | Secure Server (4001) |
|---|---|---|
| Sinh tham số state | Không sinh, gửi URL với state trống | Sinh ngẫu nhiên bằng `SecureRandom` 128-bit, mã hóa Base64URL |
| Lưu trữ tham số state | Không lưu trữ | Lưu trữ an toàn phía server-side trong `HttpSession` |
| Kiểm tra state tại Callback | Bỏ qua hoàn toàn, chỉ cần code hợp lệ | So sánh bắt buộc state nhận về với state lưu trong session |
| Xử lý khi state không khớp | Cho phép đăng nhập bình thường | Từ chối yêu cầu, trả về mã lỗi HTTP 400 |
| Vòng đời của state | Không áp dụng | Chỉ sử dụng một lần (xóa ngay khỏi session sau khi so khớp) |
| Nhật ký hệ thống (Log) | Log cảnh báo bỏ qua kiểm tra state | Log chi tiết kết quả so khớp state (Pass/Fail) |

## Phần 2: JWT Algorithm Verification (Mô Phỏng 2)

| Tiêu chí | Vulnerable Server (4002) | Secure Server (4001) |
|---|---|---|
| Cách đọc thuật toán xác minh | Tin cậy và đọc trực tiếp từ trường `alg` trong Header của token | Tự giải mã thủ công Header và đối chiếu với danh sách cho phép |
| Danh sách thuật toán được phép | Không giới hạn (chấp nhận HS256, none...) | Chỉ cho phép duy nhất thuật toán bất đối xứng `RS256` |
| Xử lý token thuật toán HS256 | Dùng khóa công khai RSA làm khóa đối xứng HMAC để kiểm thử chữ ký | Từ chối xử lý ngay lập tức, trả về lỗi HTTP 401 |
| Xử lý token thuật toán alg=none | Chấp nhận token và bỏ qua bước kiểm tra chữ ký | Từ chối xử lý ngay lập tức, trả về lỗi HTTP 401 |
| Công khai Public Key | Có, tại đường dẫn công cộng `/api/public-key` | Có, tại đường dẫn công cộng `/api/public-key` |

---

# Các Request/Response Quan Trọng

## OAuth2 Authorization Request

Vulnerable server — không có state:
```
GET /oauth2/login HTTP/1.1
Host: localhost:4002

HTTP/1.1 302 Found
Location: https://accounts.google.com/o/oauth2/v2/auth
  ?client_id=YOUR_CLIENT_ID
  &redirect_uri=http%3A%2F%2Flocalhost%3A4002%2Foauth2%2Fcallback
  &response_type=code
  &scope=openid+profile+email
  &access_type=offline
```

Secure server — có state:
```
GET /oauth2/login HTTP/1.1
Host: localhost:4001

HTTP/1.1 302 Found
Location: https://accounts.google.com/o/oauth2/v2/auth
  ?client_id=YOUR_CLIENT_ID
  &redirect_uri=http%3A%2F%2Flocalhost%3A4001%2Foauth2%2Fcallback
  &response_type=code
  &scope=openid+profile+email
  &access_type=offline
  &state=k3mN7pQ1xR8vL2jHq9nEoA
```

## OAuth2 Callback

Yêu cầu chuyển hướng từ Google về máy chủ:
```
GET /oauth2/callback?code=4%2F0XXXXXXX&state=k3mN7pQ1xR8vL2jH HTTP/1.1
Host: localhost:4001
Cookie: JSESSIONID=ABCDEF123456
```

Phản hồi từ Secure Server khi state hợp lệ:
```
HTTP/1.1 302 Found
Location: /oauth2/profile
```

Phản hồi từ Secure Server khi phát hiện state giả mạo hoặc sai lệch:
```
HTTP/1.1 200 OK
Content-Type: text/html

[Hiển thị giao diện trang lỗi: "CSRF Attack Detected! State không khớp."]
```

## Token Exchange (Server-to-Server)

Yêu cầu đổi mã code lấy access token (gửi trực tiếp từ backend lên Google API):
```
POST https://oauth2.googleapis.com/token HTTP/1.1
Content-Type: application/x-www-form-urlencoded

code=4/0XXXXXXX
&client_id=YOUR_CLIENT_ID
&client_secret=YOUR_CLIENT_SECRET
&redirect_uri=http%3A%2F%2Flocalhost%3A4001%2Foauth2%2Fcallback
&grant_type=authorization_code
```

Phản hồi từ Google chứa access_token:
```json
{
  "access_token": "ya29.A0ARrdaM...",
  "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
  "token_type": "Bearer",
  "expires_in": 3599,
  "scope": "openid https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email"
}
```

## UserInfo Request

Yêu cầu truy vấn thông tin tài khoản Google của người dùng:
```
GET https://www.googleapis.com/oauth2/v3/userinfo HTTP/1.1
Authorization: Bearer ya29.A0ARrdaM...
```

Phản hồi từ Google chứa thông tin cá nhân:
```json
{
  "sub": "116821942871234567890",
  "name": "Nguyen Van A",
  "email": "nguyenvana@gmail.com",
  "picture": "https://lh3.googleusercontent.com/a/...",
  "email_verified": true,
  "locale": "vi"
}
```

---

# Xử Lý Sự Cố Thường Gặp

**Lỗi: "Google Client ID chưa được cấu hình"**

Hãy kiểm tra lại kỹ xem bạn đã nhập đúng mã Google Credentials vào các file cấu hình dưới đây chưa:
- Vulnerable server: [application.properties](file:///d:/Document/Security/jwt-attack-demo/server-vulnerable/src/main/resources/application.properties) (trường `google.client-id` và `google.client-secret`)
- Secure server: [application.yml](file:///d:/Document/Security/jwt-attack-demo/server-secure/src/main/resources/application.yml) (trường `google: client-id` và `client-secret`)

*Đảm bảo bạn đã thay thế các giá trị mẫu `YOUR_GOOGLE_CLIENT_ID` và `YOUR_GOOGLE_CLIENT_SECRET` bằng thông tin thật được cung cấp từ Google Cloud Console.*

**Lỗi: "redirect_uri_mismatch" báo về từ Google**

Hãy truy cập Google Cloud Console và kiểm tra xem danh sách "Authorized redirect URIs" đã khai báo đầy đủ cả 3 địa chỉ sau chưa:
```
http://localhost:4001/oauth2/callback
http://localhost:4002/oauth2/callback
http://localhost:3000/callback
```

**Lỗi: "invalid_grant" khi đổi mã code**

Authorization Code do Google cung cấp chỉ được sử dụng tối đa một lần và sẽ tự động hết hiệu lực sau 10 phút. Nếu gặp lỗi này, bạn vui lòng tắt các trình duyệt, truy cập lại trang đăng nhập từ đầu để nhận mã code mới.

**Lỗi: "release version 21 not supported"**

Bộ biên dịch Maven hiện tại trên máy của bạn đang sử dụng phiên bản Java JDK cũ hơn JDK 21. Vui lòng thiết lập lại đường dẫn `JAVA_HOME` của hệ thống trỏ về thư mục cài đặt JDK 21 hoặc cao hơn:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.x.x-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

**Lỗi: Cổng kết nối (Port) đã bị chiếm dụng**

Tìm và tắt tiến trình (process) đang chạy ẩn và chiếm dụng cổng kết nối 4001 hoặc 4002 trên Windows:
```powershell
netstat -ano | findstr :4001
netstat -ano | findstr :4002
# Xác định cột PID (mã số tiến trình) ở cuối dòng kết quả, sau đó chạy lệnh để tắt:
taskkill /PID <PID_VUA_TIM_DUOC> /F
```

---

# Tài Liệu Tham Khảo

- RFC 6749 — The OAuth 2.0 Authorization Framework: https://datatracker.ietf.org/doc/html/rfc6749
- RFC 6749 Section 10.12 — Cross-Site Request Forgery: https://datatracker.ietf.org/doc/html/rfc6749#section-10.12
- OAuth 2.0 Security Best Current Practice: https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics
- Critical vulnerabilities in JSON Web Token libraries (CVE-2015-9235): https://auth0.com/blog/critical-vulnerabilities-in-json-web-token-libraries/
- OWASP OAuth Cheat Sheet: https://cheatsheetseries.owasp.org/cheatsheets/OAuth2_Cheat_Sheet.html

