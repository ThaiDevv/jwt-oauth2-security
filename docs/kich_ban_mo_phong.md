# 🎬 Kịch Bản Mô Phỏng — Tấn Công & Phòng Thủ API

> [!NOTE]
> Mỗi kịch bản gồm 2 phần: **BEFORE** (server yếu, bị hack) → **AFTER** (server đã bảo mật, chặn được).
> Khi demo, chạy BEFORE trước cho giảng viên thấy bị hack, rồi bật Secure mode chạy lại → bị chặn.

---

## 🔑 KỊCH BẢN 1: JWT — Algorithm Confusion Attack

### Bối cảnh

> Server dùng **RS256** (asymmetric) để sign JWT. Public key được công khai tại `/api/public-key`.
> Hacker lợi dụng việc server **không kiểm tra algorithm** → đổi sang HS256 và dùng public key làm secret để giả mạo token admin.

---

### 🔴 BEFORE — Server Vulnerable (Bị tấn công thành công)

```mermaid
sequenceDiagram
    participant H as 🏴‍☠️ Hacker
    participant S as 🖥️ Server (Vulnerable)
    participant DB as 🗄️ Database

    Note over H,S: Bước 1 — Hacker thu thập thông tin
    H->>S: GET /api/public-key
    S-->>H: 📄 RSA Public Key (công khai)

    Note over H: Bước 2 — Hacker tạo token giả
    Note over H: Lấy public key vừa nhận
    Note over H: Tạo payload: {"role": "admin"}
    Note over H: Đổi header: {"alg": "HS256"} (thay vì RS256)
    Note over H: Sign token bằng HS256 + public key làm secret

    Note over H,S: Bước 3 — Hacker gửi token giả
    H->>S: GET /api/admin/users<br/>Authorization: Bearer [FORGED TOKEN]

    Note over S: Server nhận token
    Note over S: Đọc header → thấy "alg": "HS256"
    Note over S: Dùng public key verify HS256 → ✅ MATCH!
    Note over S: ❌ KHÔNG kiểm tra algorithm có hợp lệ không

    S->>DB: SELECT * FROM users
    DB-->>S: Toàn bộ data users
    S-->>H: ✅ 200 OK — Trả về danh sách tất cả users!

    Note over H: 🏴‍☠️ THÀNH CÔNG — Hacker có quyền admin
```

**Quy trình thực hiện demo:**

```
Bước 1: Hacker chạy script lấy public key
        → Terminal hiện: "Fetched public key from server"

Bước 2: Script tự động tạo forged token
        → Terminal hiện: "Forged token: eyJhbGciOiJIUzI1NiJ9..."

Bước 3: Script gửi request với forged token
        → Terminal hiện: "✅ 200 OK — Got admin data!"
        → Hiển thị danh sách users (tên, email, role...)

👉 Kết luận: Server bị hack vì KHÔNG validate algorithm
```

---

### 🟢 AFTER — Server Secure (Chặn được tấn công)

```mermaid
sequenceDiagram
    participant H as 🏴‍☠️ Hacker
    participant S as 🛡️ Server (Secure)

    Note over H,S: Hacker thử tấn công y hệt
    H->>S: GET /api/public-key
    S-->>H: 📄 RSA Public Key

    Note over H: Tạo forged token y hệt (HS256 + public key)

    H->>S: GET /api/admin/users<br/>Authorization: Bearer [FORGED TOKEN]

    Note over S: 🛡️ Middleware kiểm tra
    Note over S: 1. Đọc header → "alg": "HS256"
    Note over S: 2. Check whitelist → HS256 ∉ [RS256]
    Note over S: 3. ❌ REJECT — Algorithm không được phép!

    S-->>H: ❌ 401 Unauthorized<br/>{"error": "Algorithm HS256 is not allowed. Only RS256 accepted."}

    Note over H: 🚫 THẤT BẠI — Tấn công bị chặn
```

**Quy trình thực hiện demo:**

```
Bước 1-2: Giống hệt phần BEFORE

Bước 3: Script gửi request với forged token
        → Terminal hiện: "❌ 401 Unauthorized"
        → Server log hiện: "⚠️ BLOCKED: Algorithm confusion attack detected from IP 192.168.1.x"

Bước 4: Show Dashboard
        → Dashboard hiện alert đỏ: "JWT Algorithm Attack Detected"
        → Log ghi lại: thời gian, IP, token bị reject

👉 Kết luận: Chỉ cần WHITELIST algorithm → chặn hoàn toàn
```

---

## 🔑 KỊCH BẢN 2: JWT — `alg: none` Attack

### Bối cảnh

> Hacker tạo token với `"alg": "none"` và bỏ phần signature. Server cấu hình sai → chấp nhận token không có chữ ký.

---

### 🔴 BEFORE — Bị tấn công

```mermaid
sequenceDiagram
    participant H as 🏴‍☠️ Hacker
    participant S as 🖥️ Server (Vulnerable)

    Note over H: Tạo JWT thủ công
    Note over H: Header: {"alg": "none", "typ": "JWT"}
    Note over H: Payload: {"sub": "admin", "role": "superadmin"}
    Note over H: Signature: (bỏ trống)
    Note over H: Token = base64(header).base64(payload).

    H->>S: GET /api/admin/dashboard<br/>Authorization: Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJhZG1pbiJ9.

    Note over S: Đọc alg = "none"
    Note over S: Bỏ qua verify signature
    Note over S: Chấp nhận payload → role = superadmin

    S-->>H: ✅ 200 OK — Admin Dashboard Data

    Note over H: 🏴‍☠️ Truy cập admin KHÔNG cần biết secret key!
```

### 🟢 AFTER — Chặn được

```
Hacker gửi token "alg: none"
→ Server: ❌ 401 — "Algorithm 'none' is not permitted"
→ Dashboard log: "alg:none attack attempt blocked"
```

---

## 🔐 KỊCH BẢN 3: OAuth2 — Authorization Code Interception

### Bối cảnh

> Hệ thống dùng OAuth2 Authorization Code Flow. Server **không validate `redirect_uri`** chặt chẽ → Hacker đổi redirect_uri sang server của mình để đánh cắp authorization code.

---

### 🔴 BEFORE — Server Vulnerable

```mermaid
sequenceDiagram
    participant V as 👤 Victim (User)
    participant H as 🏴‍☠️ Hacker
    participant AS as 🔑 Auth Server (Vulnerable)
    participant HS as 💀 Hacker's Server

    Note over H: Bước 1 — Hacker tạo link phishing
    Note over H: Đổi redirect_uri → hacker's server

    H->>V: 📧 "Click để nhận ưu đãi!"<br/>Link: auth-server.com/authorize?<br/>client_id=legit_app&<br/>redirect_uri=https://hacker.com/callback&<br/>response_type=code

    Note over V: Bước 2 — Victim click link
    V->>AS: GET /authorize?redirect_uri=https://hacker.com/callback

    Note over AS: ❌ KHÔNG validate redirect_uri
    Note over AS: Tạo authorization code

    AS->>V: 302 Redirect → https://hacker.com/callback?code=AUTH_CODE_ABC123
    V->>HS: GET /callback?code=AUTH_CODE_ABC123

    Note over HS: Bước 3 — Hacker nhận được auth code!

    HS->>AS: POST /token<br/>code=AUTH_CODE_ABC123&<br/>client_id=legit_app&<br/>client_secret=xxx
    AS-->>HS: ✅ {"access_token": "victim_token_xyz"}

    Note over H: Bước 4 — Hacker có access token của victim!
    HS->>AS: GET /api/profile<br/>Authorization: Bearer victim_token_xyz
    AS-->>HS: 📋 Victim's profile data (name, email, ...)

    Note over H: 🏴‍☠️ TOÀN QUYỀN truy cập tài khoản victim
```

**Quy trình thực hiện demo:**

```
Bước 1: Mở browser → show URL phishing mà hacker tạo
        → Chỉ ra redirect_uri đã bị đổi sang hacker.com

Bước 2: Click link (giả lập victim)
        → Auth Server hiện trang "Cho phép ứng dụng truy cập?"
        → User click "Đồng ý"

Bước 3: Browser redirect tới hacker's server
        → Terminal hacker hiện: "🎣 Caught auth code: AUTH_CODE_ABC123"

Bước 4: Hacker script tự động đổi code → access token
        → Terminal hiện: "✅ Got victim's access token!"
        → Hiển thị thông tin victim

👉 Kết luận: redirect_uri không được validate → bị đánh cắp code
```

---

### 🟢 AFTER — Server Secure

```mermaid
sequenceDiagram
    participant V as 👤 Victim
    participant H as 🏴‍☠️ Hacker
    participant AS as 🛡️ Auth Server (Secure)

    H->>V: 📧 Link phishing (redirect_uri = hacker.com)
    V->>AS: GET /authorize?redirect_uri=https://hacker.com/callback

    Note over AS: 🛡️ Validate redirect_uri
    Note over AS: Registered URI: https://legit-app.com/callback
    Note over AS: Request URI: https://hacker.com/callback
    Note over AS: ❌ KHÔNG MATCH → REJECT!

    AS-->>V: ❌ 400 Bad Request<br/>{"error": "invalid_redirect_uri",<br/>"message": "redirect_uri does not match registered URI"}

    Note over H: 🚫 THẤT BẠI — Không lấy được auth code
```

**Quy trình thực hiện demo:**

```
Bước 1-2: Giống BEFORE, click link phishing

Bước 3: Auth Server CHẶN ngay
        → Browser hiện: "Error: Invalid redirect_uri"
        → Dashboard log: "⚠️ Suspicious redirect_uri blocked: hacker.com"

👉 Kết luận: Exact-match redirect_uri → chặn hoàn toàn
```

---

## 🔐 KỊCH BẢN 4: OAuth2 — CSRF Attack (Thiếu State Parameter)

### Bối cảnh

> OAuth2 flow **không dùng `state` parameter** → Hacker có thể trick victim link tài khoản với OAuth account của hacker.

---

### 🔴 BEFORE — Bị tấn công

```mermaid
sequenceDiagram
    participant H as 🏴‍☠️ Hacker
    participant AS as 🔑 Auth Server
    participant V as 👤 Victim
    participant App as 🖥️ Target App (Vulnerable)

    Note over H: Bước 1 — Hacker tự khởi tạo OAuth flow
    H->>AS: GET /authorize?client_id=app&redirect_uri=app.com/callback
    AS-->>H: Redirect → app.com/callback?code=HACKER_CODE_XYZ

    Note over H: Bước 2 — Hacker KHÔNG dùng code này
    Note over H: Mà gửi link chứa code cho victim

    H->>V: 📧 "Xem hình cute!"<br/>Trang web chứa:<br/>&lt;img src="app.com/callback?code=HACKER_CODE_XYZ"&gt;

    Note over V: Bước 3 — Victim mở trang web
    Note over V: Browser tự động load img src

    V->>App: GET /callback?code=HACKER_CODE_XYZ

    Note over App: ❌ Không có state → không phân biệt
    Note over App: Đổi HACKER's code → access token
    Note over App: Link HACKER's OAuth account vào VICTIM's session

    App-->>V: ✅ "Đã liên kết tài khoản thành công!"

    Note over H: Bước 4 — Hacker login bằng OAuth
    Note over H: Vào được tài khoản Victim!
    H->>App: Login with OAuth (hacker's account)
    App-->>H: ✅ Welcome! (vào account Victim)

    Note over H: 🏴‍☠️ Chiếm được tài khoản victim
```

**Quy trình thực hiện demo:**

```
Bước 1: Show hacker tạo CSRF page (HTML có <img> tag ẩn)
Bước 2: Victim mở trang web đó (mở browser)
Bước 3: Show trong app → account victim đã bị link với OAuth hacker
Bước 4: Hacker login OAuth → vào được account victim

👉 Kết luận: Không có state parameter → bị CSRF
```

---

### 🟢 AFTER — Chặn được

```mermaid
sequenceDiagram
    participant V as 👤 Victim
    participant App as 🛡️ Target App (Secure)

    Note over App: Khi bắt đầu OAuth flow
    Note over App: Generate state = crypto_random()
    Note over App: Lưu state vào session

    V->>App: GET /callback?code=HACKER_CODE_XYZ
    Note over App: ❌ Không có state parameter!
    Note over App: Hoặc state không match session
    App-->>V: ❌ 403 Forbidden<br/>{"error": "CSRF detected — invalid state"}

    Note over V: 🛡️ Tài khoản AN TOÀN
```

---

## 🎯 Tổng Hợp Workflow Demo

```mermaid
flowchart TD
    Start([🎬 Bắt đầu Demo]) --> JWT_Section

    subgraph JWT_Section["🔑 Phần 1: JWT Security"]
        J1[Giới thiệu JWT là gì] --> J2[Demo Attack 1: Algorithm Confusion]
        J2 --> J2B["🔴 BEFORE: Hack thành công"]
        J2B --> J2A["🟢 AFTER: Bị chặn"]
        J2A --> J3[Demo Attack 2: alg none]
        J3 --> J3B["🔴 BEFORE: Hack thành công"]
        J3B --> J3A["🟢 AFTER: Bị chặn"]
    end

    JWT_Section --> OAuth_Section

    subgraph OAuth_Section["🔐 Phần 2: OAuth2 Security"]
        O1[Giới thiệu OAuth2 là gì] --> O2[Demo Attack 3: Code Interception]
        O2 --> O2B["🔴 BEFORE: Đánh cắp code"]
        O2B --> O2A["🟢 AFTER: redirect_uri blocked"]
        O2A --> O3[Demo Attack 4: CSRF]
        O3 --> O3B["🔴 BEFORE: Chiếm account"]
        O3B --> O3A["🟢 AFTER: state validation"]
    end

    OAuth_Section --> Dashboard[📊 Show Security Dashboard]
    Dashboard --> QA[❓ 03 Câu hỏi tự luận cho lớp]
    QA --> End([🎬 Kết thúc])
```

---

## ⏱️ Phân Bổ Thời Gian Trình Bày (Ước lượng ~30 phút)

| Thời gian | Nội dung | Người trình bày |
|-----------|----------|-----------------|
| 3 phút | Giới thiệu đề tài, mục tiêu, kiến trúc hệ thống | 👑 Lead |
| 5 phút | Lý thuyết JWT + Demo Attack 1 (Alg Confusion) Before/After | 🔑 Member 2 |
| 4 phút | Demo Attack 2 (alg:none) Before/After | 🔑 Member 2 |
| 5 phút | Lý thuyết OAuth2 + Demo Attack 3 (Code Interception) Before/After | 🔐 Member 3 |
| 4 phút | Demo Attack 4 (CSRF) Before/After | 🔐 Member 3 |
| 3 phút | Show Security Dashboard tổng hợp | 👑 Lead |
| 3 phút | Kết luận + Biện pháp phòng chống | 👑 Lead |
| 3 phút | 03 câu hỏi tự luận + tương tác lớp | Cả nhóm |
