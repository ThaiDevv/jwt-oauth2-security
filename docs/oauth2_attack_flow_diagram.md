# Sơ Đồ Luồng Hoạt Động — Tấn Công OAuth2 Authorization Code Interception

---

## 1. Kiến Trúc Tổng Quan Hệ Thống

```mermaid
graph TB
    subgraph "Frontend Layer"
        CLIENT["Client App<br/>(React - Port 3000)<br/>Ứng dụng hợp lệ"]
        HACKER["Hacker Tool<br/>(React - Port 3001)<br/>Ứng dụng kẻ tấn công"]
    end

    subgraph "Backend Layer"
        VULN["Server Vulnerable<br/>(Spring Boot - Port 4002)"]
        SECURE["Server Secure<br/>(Spring Boot - Port 4001)"]
    end

    subgraph "OAuth2 Layer"
        OAUTH["OAuth2 Authorization Server<br/>(Spring Boot - Port 4003)<br/>KHÔNG validate redirect_uri"]
    end

    CLIENT -->|"1. Login via OAuth2"| OAUTH
    OAUTH -->|"2. Auth Code → redirect_uri"| CLIENT
    HACKER -->|"1'. Giả mạo redirect_uri"| OAUTH
    OAUTH -->|"2'. Auth Code → HACKER!"| HACKER
    HACKER -->|"3'. Exchange code → token"| OAUTH
    HACKER -->|"4'. Dùng token truy cập"| OAUTH

    style VULN fill:#7f1d1d,stroke:#ef4444,color:#fca5a5
    style SECURE fill:#14532d,stroke:#22c55e,color:#86efac
    style HACKER fill:#78350f,stroke:#f59e0b,color:#fde68a
    style CLIENT fill:#1e3a5f,stroke:#3b82f6,color:#93c5fd
    style OAUTH fill:#3b0764,stroke:#a855f7,color:#d8b4fe
```

---

## 2. Luồng OAuth2 Authorization Code Flow Hợp Lệ

> [!NOTE]
> Đây là luồng bình thường theo chuẩn RFC 6749. Client hợp lệ đăng ký trước `redirect_uri` với OAuth Server, và server chỉ chấp nhận redirect về đúng URL đã đăng ký.

```mermaid
sequenceDiagram
    actor User as Nạn nhân (Browser)
    participant Client as Client App<br/>(Port 3000)
    participant OAuth as OAuth2 Server<br/>(Port 4003)
    participant Resource as Resource Server

    Note over User, Resource: PHASE 1: Authorization Request

    User->>Client: Click "Đăng nhập bằng OAuth2"
    Client->>User: Redirect tới OAuth Server
    
    Note over User, OAuth: GET /authorize?<br/>client_id=react-client-app<br/>&redirect_uri=http://localhost:3000/callback<br/>&scope=openid profile email<br/>&state=abc123

    User->>OAuth: Mở trang Consent (đồng ý cấp quyền)
    
    Note over OAuth: Hiển thị:<br/>- Application: react-client-app<br/>- Permissions: openid, profile, email<br/>- Redirect URI: http://localhost:3000/callback

    User->>OAuth: Click "Allow" (đồng ý)

    Note over User, Resource: PHASE 2: Authorization Code Grant

    OAuth->>OAuth: generateCode(clientId)<br/>code = UUID ngẫu nhiên (12 ký tự)
    OAuth->>User: 302 Redirect<br/>http://localhost:3000/callback?code=abc123def456&state=abc123

    Note over User, Resource: PHASE 3: Token Exchange

    User->>Client: Browser redirect về callback
    Client->>OAuth: POST /token<br/>{code, client_id, grant_type, redirect_uri}
    OAuth->>OAuth: exchangeCode(code)<br/>Tạo access_token = "at_" + UUID
    OAuth-->>Client: 200 OK { access_token, token_type: "Bearer" }

    Note over User, Resource: PHASE 4: Access Resource

    Client->>Resource: GET /userinfo<br/>Authorization: Bearer at_xxxxx
    Resource-->>Client: 200 OK { sub, name, email }
    Client->>User: Hiển thị thông tin profile
```

---

## 3. Bản Chất Lỗ Hổng — Thiếu Kiểm Tra redirect_uri

> [!CAUTION]
> Lỗ hổng xảy ra khi OAuth2 Authorization Server **KHÔNG validate** tham số `redirect_uri` trong request. Server chấp nhận **bất kỳ URL nào** — kể cả URL do attacker kiểm soát. Đây là vi phạm nghiêm trọng RFC 6749 Section 3.1.2.

### 3.1 Code Bị Lỗi Trong OAuth Server

```java
// Trong AuthorizeController.java — GET /authorize:
// Server KHÔNG có bất kỳ kiểm tra nào:
@GetMapping("/authorize")
public String authorize(
    @RequestParam String client_id,
    @RequestParam String redirect_uri,   // CHẤP NHẬN BẤT KỲ URL NÀO!
    @RequestParam String scope,
    @RequestParam String state,
    Model model
) {
    // THIẾU kiểm tra quan trọng:
    //   if (!ALLOWED_REDIRECT_URIS.contains(redirect_uri)) {
    //       return "error";  // Từ chối!
    //   }
    
    model.addAttribute("redirectUri", redirect_uri);  // Truyền thẳng vào form
    return "consent";
}
```

```java
// Trong AuthorizeController.java — POST /authorize:
// Sau khi user click "Allow", redirect về bất kỳ đâu:
@PostMapping("/authorize")
public RedirectView doAuthorize(
    @RequestParam String redirect_uri,   // URL CỦA ATTACKER!
    @RequestParam String state,
    @RequestParam String client_id
) {
    String code = authCodeService.generateCode(client_id);
    // Redirect auth code về URL của ATTACKER mà KHÔNG kiểm tra!
    String location = redirect_uri + "?code=" + code + "&state=" + state;
    return new RedirectView(location);  // GỬI CODE CHO HACKER!
}
```

### 3.2 Sơ Đồ Luồng Quyết Định Trong OAuth Server

```mermaid
flowchart TD
    REQUEST["GET /authorize<br/>redirect_uri = ???"]

    CHECK{{"OAuth Server kiểm tra<br/>redirect_uri?"}}

    subgraph VULNERABLE_PATH["OAuth Server BỊ LỖI - Hiện tại"]
        V1["Không kiểm tra gì cả"]
        V2["Chấp nhận mọi redirect_uri"]
        V3["Hiển thị consent page"]
        V4["Redirect code -> redirect_uri<br/>có thể là URL hacker!"]
    end

    subgraph SECURE_PATH["OAuth Server AN TOÀN - Chuẩn RFC 6749"]
        S1["So sánh redirect_uri<br/>với whitelist đã đăng ký"]
        S2{{"redirect_uri<br/>có trong whitelist?"}}
        S3["Hiển thị consent page"]
        S4["400 Bad Request<br/>Invalid redirect_uri"]
    end

    REQUEST --> CHECK
    CHECK -->|"KHÔNG"| V1 --> V2 --> V3 --> V4
    CHECK -->|"CÓ"| S1 --> S2
    S2 -->|"CÓ"| S3
    S2 -->|"KHÔNG"| S4

    style VULNERABLE_PATH fill:#450a0a,stroke:#ef4444,color:#fca5a5
    style SECURE_PATH fill:#14532d,stroke:#22c55e,color:#86efac
    style CHECK fill:#581c87,stroke:#a855f7,color:#e9d5ff
    style V4 fill:#7f1d1d,stroke:#ef4444,color:#fff,stroke-width:3px
    style S4 fill:#166534,stroke:#22c55e,color:#fff,stroke-width:3px
```

---

## 4. Luồng Tấn Công Chi Tiết Từng Bước

```mermaid
flowchart TD
    START(["BAT DAU TAN CONG"])

    subgraph STEP1["BUOC 1 — Chuẩn Bị và Social Engineering"]
        S1A["Attacker cấu hình Hacker Tool<br/>OAuth URL: http://localhost:4003<br/>Client ID: react-client-app"]
        S1B["Đặt redirect_uri = http://localhost:3001/callback<br/>URL callback của Hacker Tool"]
        S1C["Tạo state parameter ngẫu nhiên<br/>để bypass CSRF check nếu có"]
        S1D["Tạo link phishing chứa<br/>authorize URL với redirect_uri giả"]
    end

    subgraph STEP2["BUOC 2 — Intercept Authorization Code"]
        S2A["Nạn nhân click link hoặc được redirect<br/>Mở trang Consent trên OAuth Server"]
        S2B["Nạn nhân thấy trang Consent hợp lệ<br/>tên app, quyền hạn, redirect_uri"]
        S2C["Nạn nhân click Allow<br/>tin tưởng vì trang OAuth Server thật"]
        S2D["OAuth Server sinh auth code<br/>Redirect -> http://localhost:3001/callback?code=xxx"]
        S2E["Hacker Tool nhận code<br/>qua CallbackPage.jsx"]
        S2F["Callback page gửi postMessage<br/>về OAuth2AttackPage chính"]
    end

    subgraph STEP3["BUOC 3 — Exchange Code thanh Access Token"]
        S3A["Attacker gửi POST /token<br/>code + client_id + grant_type"]
        S3B["OAuth Server xác nhận code hợp lệ<br/>Xóa code - dùng 1 lần"]
        S3C["OAuth Server trả về<br/>access_token = at_xxxxx"]
        S3D["Attacker có Access Token!"]
    end

    subgraph STEP4["BUOC 4 — Khai Thác Resource"]
        S4A["Attacker gửi GET /userinfo<br/>Authorization: Bearer at_xxxxx"]
        S4B["OAuth Server trả về<br/>thông tin cá nhân nạn nhân"]
        S4C["Attacker chiếm đoạt danh tính<br/>name, email, sub"]
    end

    START --> S1A --> S1B --> S1C --> S1D
    S1D --> S2A --> S2B --> S2C --> S2D --> S2E --> S2F
    S2F --> S3A --> S3B --> S3C --> S3D
    S3D --> S4A --> S4B --> S4C

    style START fill:#7f1d1d,stroke:#ef4444,color:#fff
    style STEP1 fill:#1e1b4b,stroke:#6366f1,color:#c7d2fe
    style STEP2 fill:#422006,stroke:#f59e0b,color:#fde68a
    style STEP3 fill:#172554,stroke:#3b82f6,color:#93c5fd
    style STEP4 fill:#450a0a,stroke:#ef4444,color:#fca5a5
```

---

## 5. Sequence Diagram Toàn Bộ Cuộc Tấn Công

```mermaid
sequenceDiagram
    actor Attacker as Attacker<br/>(Hacker Tool - Port 3001)
    actor Victim as Nạn nhân<br/>(Browser)
    participant OAuth as OAuth2 Server<br/>(Port 4003)
    participant Callback as Hacker Callback<br/>(/callback)

    Note over Attacker, Callback: BUOC 1: Chuẩn Bị Link Phishing

    Attacker->>Attacker: Cấu hình OAuth2AttackPage:<br/>oauth_url = http://localhost:4003<br/>client_id = react-client-app<br/>redirect_uri = http://localhost:3001/callback

    Attacker->>Attacker: Tạo authorize URL chứa<br/>redirect_uri dẫn tới Hacker Tool

    Note over Attacker, Callback: BUOC 2: Lừa Nạn Nhân và Intercept Code

    Attacker->>Victim: Gửi link phishing<br/>(email, tin nhắn, website giả...)
    Victim->>OAuth: Click link -> GET /authorize<br/>với redirect_uri của HACKER

    Note over OAuth: SERVER KHÔNG VALIDATE redirect_uri!<br/>Chấp nhận http://localhost:3001/callback

    OAuth->>Victim: Hiển thị Consent Page<br/>(trông hợp lệ, có tên app và scope)
    Victim->>OAuth: Click "Allow" (POST /authorize)

    OAuth->>OAuth: authCodeService.generateCode()<br/>code = "a1b2c3d4e5f6"

    OAuth->>Victim: 302 Redirect<br/>-> http://localhost:3001/callback?code=a1b2c3d4e5f6

    Note over Victim, Callback: Browser tự động redirect<br/>theo HTTP 302

    Victim->>Callback: GET /callback?code=a1b2c3d4e5f6

    Callback->>Callback: CallbackPage.jsx:<br/>Đọc code từ URL params
    Callback->>Attacker: window.opener.postMessage<br/>type: OAUTH_CODE_INTERCEPTED<br/>code: a1b2c3d4e5f6

    Note over Attacker, Callback: BUOC 3: Đổi Code Lấy Access Token

    Attacker->>Attacker: Nhận code từ postMessage<br/>Hiển thị trong Captured Auth Code

    Attacker->>OAuth: POST /token<br/>code: a1b2c3d4e5f6<br/>client_id: react-client-app<br/>grant_type: authorization_code

    OAuth->>OAuth: authCodeService.exchangeCode()<br/>Verify code hợp lệ -> Xóa code<br/>Tạo access_token = at_ + UUID

    OAuth-->>Attacker: 200 OK<br/>access_token: at_xxx...<br/>token_type: Bearer<br/>expires_in: 3600

    Note over Attacker, Callback: BUOC 4: Khai Thác Dữ Liệu Nạn Nhân

    Attacker->>OAuth: GET /userinfo<br/>Authorization: Bearer at_xxx...

    OAuth->>OAuth: authCodeService.getUserInfo(token)<br/>Tìm thông tin gắn với token

    OAuth-->>Attacker: 200 OK<br/>sub: user_react-client-app<br/>name: Demo User<br/>email: demo@example.com

    Note over Attacker, Callback: TAN CONG THANH CONG!<br/>Attacker chiếm đoạt danh tính nạn nhân
```

---

## 6. Chi Tiết Cơ Chế Callback Interception

> [!IMPORTANT]
> Hacker Tool sử dụng cơ chế `window.open()` + `postMessage()` để tự động chặn auth code mà không cần nạn nhân tương tác thêm.

```mermaid
flowchart LR
    subgraph HACKER_MAIN["OAuth2AttackPage - Tab chính"]
        H1["window.open authorizeUrl"]
        H5["addEventListener message"]
        H6["Nhận code từ postMessage<br/>setInterceptedCode"]
    end

    subgraph POPUP["Popup Window - Consent + Callback"]
        P1["Mở trang Consent<br/>trên OAuth Server"]
        P2["User click Allow"]
        P3["302 Redirect -><br/>localhost:3001/callback?code=xxx"]
        P4["CallbackPage.jsx load"]
        P5["window.opener.postMessage<br/>OAUTH_CODE_INTERCEPTED"]
    end

    H1 -->|"Mở popup"| P1
    P1 --> P2 --> P3 --> P4 --> P5
    P5 -->|"postMessage"| H5 --> H6

    style HACKER_MAIN fill:#422006,stroke:#f59e0b,color:#fde68a
    style POPUP fill:#1e1b4b,stroke:#6366f1,color:#c7d2fe
```

### Luồng dữ liệu qua CallbackPage:

```
1. OAuth Server redirect browser -> http://localhost:3001/callback?code=a1b2c3&state=xyz
2. React Router match route "/callback" -> render CallbackPage.jsx
3. CallbackPage đọc URL params: code = "a1b2c3", state = "xyz"
4. Kiểm tra window.opener (popup window reference)
5. Gửi postMessage tới parent window (OAuth2AttackPage)
6. OAuth2AttackPage nhận event -> cập nhật interceptedCode state
7. Attacker thấy code hiện trong UI -> tiến hành exchange
```

---

## 7. Tại Sao Tấn Công Thành Công?

> [!WARNING]
> Cuộc tấn công thành công do **3 yếu tố kết hợp**: Server không validate redirect_uri + Auth code dùng 1 lần nhưng không gắn với redirect_uri + Nạn nhân tin tưởng trang consent hợp lệ.

```mermaid
flowchart TD
    subgraph ROOT["3 Yếu Tố Gây Ra Lỗ Hổng"]
        direction TB
        F1["1. KHÔNG validate redirect_uri<br/>Server chấp nhận bất kỳ URL nào<br/>trong tham số redirect_uri"]
        F2["2. Auth code KHÔNG gắn với redirect_uri<br/>Code sinh ra không kiểm tra<br/>redirect_uri khi exchange"]
        F3["3. Trang Consent trông hợp lệ<br/>User thấy tên app, scope đúng<br/>tin tưởng và click Allow"]
    end

    subgraph RESULT["Hậu Quả"]
        R1["Attacker chiếm auth code"]
        R2["Đổi code -> access token"]
        R3["Truy cập tài nguyên nạn nhân"]
        R4["Chiếm đoạt danh tính"]
    end

    F1 --> R1
    F2 --> R2
    F3 --> R1
    R1 --> R2 --> R3 --> R4

    style ROOT fill:#1e1b4b,stroke:#6366f1,color:#c7d2fe
    style RESULT fill:#450a0a,stroke:#ef4444,color:#fca5a5
    style R4 fill:#7f1d1d,stroke:#ef4444,color:#fff,stroke-width:3px
```

**Chi tiết kỹ thuật:**

```
Luồng hợp lệ:
  redirect_uri = http://localhost:3000/callback  (Client App)
  -> OAuth Server redirect code -> Client App
  -> Client App exchange code -> access token
  -> Client App dùng token truy cập resource

Luồng bị tấn công:
  redirect_uri = http://localhost:3001/callback  (Hacker Tool!)
  -> OAuth Server redirect code -> HACKER TOOL
  -> Hacker Tool exchange code -> access token
  -> Hacker Tool dùng token truy cập resource CỦA NẠN NHÂN!
```

---

## 8. Các Kịch Bản Tấn Công Thực Tế

### 8.1 Kịch bản trong Demo (Localhost)

```mermaid
flowchart LR
    LEGIT["Client hợp lệ<br/>localhost:3000/callback"]
    HACKER["Hacker Tool<br/>localhost:3001/callback"]
    
    OAUTH["OAuth Server<br/>localhost:4003"]
    
    OAUTH -->|"Luồng bình thường"| LEGIT
    OAUTH -->|"Bị chiếm đoạt!"| HACKER
    
    style LEGIT fill:#14532d,stroke:#22c55e,color:#86efac
    style HACKER fill:#450a0a,stroke:#ef4444,color:#fca5a5
    style OAUTH fill:#3b0764,stroke:#a855f7,color:#d8b4fe
```

### 8.2 Các Kịch Bản Ngoài Thực Tế

| Kịch bản | Mô tả | redirect_uri giả |
|-----------|--------|-------------------|
| **Phishing Email** | Gửi email chứa link authorize với redirect_uri dẫn tới server hacker | `https://evil-hacker.com/steal` |
| **Open Redirect Chain** | Lợi dụng lỗi open redirect trên chính domain hợp lệ | `https://legit-app.com/redirect?url=https://evil.com` |
| **Subdomain Takeover** | Chiếm subdomain bỏ hoang của tổ chức | `https://old-service.legit-app.com/callback` |
| **Typosquatting** | Đăng ký domain giống domain thật | `https://leglt-app.com/callback` |
| **Referer Leakage** | Auth code lộ qua HTTP Referer header | Không cần thay đổi redirect_uri |

---

## 9. So Sánh: OAuth Server Bị Lỗi vs An Toàn

```mermaid
flowchart TD
    INPUT["Authorization Request đến<br/>redirect_uri = http://evil.com/steal"]

    subgraph VULNERABLE["OAuth Server BI LOI"]
        direction TB
        V1["Nhận redirect_uri từ request"]
        V2["KHÔNG kiểm tra whitelist"]
        V3["Hiển thị consent page"]
        V4["User click Allow"]
        V5["Redirect code -> http://evil.com/steal<br/>CODE BI DANH CAP!"]

        V1 --> V2 --> V3 --> V4 --> V5
    end

    subgraph SECURE["OAuth Server AN TOAN"]
        direction TB
        S1["Nhận redirect_uri từ request"]
        S2{"redirect_uri trong whitelist?"}
        S3["400 Bad Request<br/>redirect_uri_mismatch"]
        S4["Hiển thị consent page"]
        S5["User click Allow"]
        S6["Redirect code -> whitelisted URL only"]

        S1 --> S2
        S2 -->|"KHÔNG"| S3
        S2 -->|"CÓ"| S4 --> S5 --> S6
    end

    INPUT --> V1
    INPUT --> S1

    style VULNERABLE fill:#450a0a,stroke:#ef4444,color:#fca5a5
    style SECURE fill:#14532d,stroke:#22c55e,color:#86efac
    style V5 fill:#7f1d1d,stroke:#ef4444,color:#fff,stroke-width:3px
    style S3 fill:#166534,stroke:#22c55e,color:#fff,stroke-width:3px
    style INPUT fill:#1e293b,stroke:#64748b,color:#e2e8f0
```

---

## 10. Cách Phòng Chống

> [!TIP]
> Phòng chống lỗ hổng OAuth2 redirect_uri interception cần áp dụng đồng thời nhiều biện pháp ở cả phía Authorization Server và Client.

### 10.1 Biện pháp chính: Validate redirect_uri Whitelist

```mermaid
flowchart LR
    REQ["redirect_uri<br/>từ request"] --> CHECK{{"So sánh với<br/>whitelist đã đăng ký"}}
    CHECK -->|"EXACT MATCH"| ALLOW["Cho phép<br/>Hiển thị consent"]
    CHECK -->|"KHÔNG KHỚP"| REJECT["400 Bad Request<br/>redirect_uri_mismatch"]

    style REJECT fill:#166534,stroke:#22c55e,color:#fff,stroke-width:2px
    style CHECK fill:#581c87,stroke:#a855f7,color:#e9d5ff
    style ALLOW fill:#1e3a5f,stroke:#3b82f6,color:#93c5fd
```

### 10.2 Code Phòng Chống

```java
// AuthorizeController.java — Phiên bản AN TOÀN:

// Whitelist các redirect_uri đã đăng ký
private static final Set<String> ALLOWED_REDIRECT_URIS = Set.of(
    "http://localhost:3000/callback",
    "https://my-app.com/oauth/callback"
);

@GetMapping("/authorize")
public String authorize(
    @RequestParam String client_id,
    @RequestParam String redirect_uri,
    @RequestParam String scope,
    @RequestParam String state,
    Model model
) {
    // KIỂM TRA REDIRECT_URI — Exact match!
    if (!ALLOWED_REDIRECT_URIS.contains(redirect_uri)) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Invalid redirect_uri: " + redirect_uri
        );
    }
    // ... tiếp tục xử lý
}
```

### 10.3 Checklist Bảo Mật OAuth2 Đầy Đủ

| Biện pháp | Mô tả | Mức độ |
|-----------|--------|--------|
| **Exact redirect_uri match** | So sánh chính xác, không dùng wildcard hay prefix match | Bắt buộc |
| **Đăng ký redirect_uri trước** | Client phải khai báo redirect_uri khi đăng ký ứng dụng | Bắt buộc |
| **State parameter** | Chống CSRF — so sánh state gửi đi và nhận về | Bắt buộc |
| **PKCE (RFC 7636)** | code_verifier + code_challenge chống interception | Khuyến nghị cao |
| **Auth code dùng 1 lần** | Xóa code ngay sau khi exchange thành công | Bắt buộc |
| **Auth code hết hạn nhanh** | Code chỉ valid trong 30s-60s | Khuyến nghị |
| **Bind code với redirect_uri** | Khi exchange, kiểm tra redirect_uri phải khớp lúc authorize | Bắt buộc |
| **HTTPS only** | Chặn man-in-the-middle đánh cắp code qua HTTP | Bắt buộc (production) |
| **Token binding** | Gắn token với session/device cụ thể | Nâng cao |

### 10.4 PKCE — Giải Pháp Mạnh Nhất

```mermaid
sequenceDiagram
    actor Client as Client App
    participant OAuth as OAuth Server

    Note over Client, OAuth: PKCE Flow (RFC 7636)

    Client->>Client: Tạo code_verifier (random 128 bytes)<br/>Tạo code_challenge = SHA256(code_verifier)

    Client->>OAuth: GET /authorize?<br/>code_challenge=xxx<br/>&code_challenge_method=S256<br/>&redirect_uri=...
    
    OAuth-->>Client: 302 Redirect ?code=abc123

    Note over Client, OAuth: Khi exchange code:

    Client->>OAuth: POST /token<br/>code: abc123<br/>code_verifier: original_secret
    
    OAuth->>OAuth: Verify:<br/>SHA256(code_verifier) == code_challenge?
    
    Note over OAuth: Nếu attacker đánh cắp code,<br/>KHÔNG CÓ code_verifier<br/>-> KHÔNG exchange được!

    OAuth-->>Client: 200 OK { access_token }
```

---

## 11. Mapping Sơ Đồ -> Mã Nguồn

| Bước trong sơ đồ | File mã nguồn | Mô tả |
|---|---|---|
| Cấu hình tấn công | [OAuth2AttackPage.jsx](file:///d:/Document/Security/jwt-attack-demo/hacker-tool/src/pages/OAuth2AttackPage.jsx#L3-L8) | State: oauthUrl, clientId, redirectUri |
| Tạo authorize URL | [OAuth2AttackPage.jsx](file:///d:/Document/Security/jwt-attack-demo/hacker-tool/src/pages/OAuth2AttackPage.jsx#L40-L52) | `startAuthorization()` — mở popup consent |
| **Lỗ hổng: Không validate redirect_uri** | [AuthorizeController.java](file:///d:/Document/Security/jwt-attack-demo/oauth-server/src/main/java/com/attt/oauth/controller/AuthorizeController.java#L28-L45) | `GET /authorize` — thiếu kiểm tra whitelist |
| **Redirect code về hacker** | [AuthorizeController.java](file:///d:/Document/Security/jwt-attack-demo/oauth-server/src/main/java/com/attt/oauth/controller/AuthorizeController.java#L51-L62) | `POST /authorize` — redirect không kiểm tra |
| Callback intercept code | [CallbackPage.jsx](file:///d:/Document/Security/jwt-attack-demo/hacker-tool/src/pages/CallbackPage.jsx#L10-L19) | Đọc code từ URL params, gửi postMessage |
| Nhận code qua postMessage | [OAuth2AttackPage.jsx](file:///d:/Document/Security/jwt-attack-demo/hacker-tool/src/pages/OAuth2AttackPage.jsx#L25-L38) | `handleMessage` event listener |
| Sinh auth code | [AuthCodeService.java](file:///d:/Document/Security/jwt-attack-demo/oauth-server/src/main/java/com/attt/oauth/service/AuthCodeService.java#L21-L26) | `generateCode()` — UUID random |
| Exchange code -> token | [TokenController.java](file:///d:/Document/Security/jwt-attack-demo/oauth-server/src/main/java/com/attt/oauth/controller/TokenController.java#L31-L64) | `POST /token` |
| Exchange logic | [AuthCodeService.java](file:///d:/Document/Security/jwt-attack-demo/oauth-server/src/main/java/com/attt/oauth/service/AuthCodeService.java#L29-L45) | `exchangeCode()` — verify code, tạo token |
| Khai thác userinfo | [TokenController.java](file:///d:/Document/Security/jwt-attack-demo/oauth-server/src/main/java/com/attt/oauth/controller/TokenController.java#L67-L76) | `GET /userinfo` |
| Trang consent (Thymeleaf) | [consent.html](file:///d:/Document/Security/jwt-attack-demo/oauth-server/src/main/resources/templates/consent.html) | Form hiển thị cho user |
| Security config (CORS) | [SecurityConfig.java](file:///d:/Document/Security/jwt-attack-demo/oauth-server/src/main/java/com/attt/oauth/config/SecurityConfig.java#L22-L32) | Cho phép CORS từ port 3000, 3001 |

---

## 12. So Sánh Với Tấn Công JWT Algorithm Confusion

| Tiêu chí | JWT Algorithm Confusion (CVE-2015-9235) | OAuth2 Redirect URI Interception |
|-----------|------------------------------------------|----------------------------------|
| **Mục tiêu** | Giả mạo JWT token | Chiếm đoạt authorization code |
| **Lỗ hổng ở đâu** | Thư viện JWT cũ (auto-detect alg) | OAuth Server (thiếu validate redirect_uri) |
| **Attacker cần gì** | Public Key (công khai) | Nạn nhân click link phishing |
| **Yếu tố con người** | Không cần (tự động hoàn toàn) | Cần lừa nạn nhân click Allow |
| **Kết quả** | Token giả với quyền admin | Access token của nạn nhân |
| **Phòng chống** | Algorithm whitelist | redirect_uri whitelist + PKCE |
| **CVE** | CVE-2015-9235 | Nhiều CVE, phổ biến nhất: CWE-601 |
