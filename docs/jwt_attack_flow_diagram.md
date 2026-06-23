# Sơ Đồ Luồng Hoạt Động — Tấn Công JWT Algorithm Confusion (CVE-2015-9235)

---

## 1. Kiến Trúc Tổng Quan Hệ Thống

```mermaid
graph TB
    subgraph "Frontend Layer"
        CLIENT["Client App<br/>(React - Port 3000)"]
        HACKER["Hacker Tool<br/>(React - Port 3001)"]
    end

    subgraph "Backend Layer"
        VULN["Server Vulnerable<br/>(Spring Boot - Port 4002)<br/>❌ Không có Algorithm Whitelist"]
        SECURE["Server Secure<br/>(Spring Boot - Port 4001)<br/>✅ Chỉ chấp nhận RS256"]
    end

    subgraph "OAuth2 Layer"
        OAUTH["OAuth2 Server<br/>(Spring Boot - Port 4003)"]
    end

    subgraph "Database Layer"
        DB1["H2 Database<br/>(Vulnerable)"]
        DB2["H2 Database<br/>(Secure)"]
        DB3["H2 Database<br/>(OAuth2)"]
    end

    CLIENT -->|"Login/Register<br/>API Calls"| VULN
    CLIENT -->|"Login/Register<br/>API Calls"| SECURE
    CLIENT -->|"OAuth2 Flow"| OAUTH
    HACKER -->|"🔴 Gửi Forged Token"| VULN
    HACKER -.->|"🟢 Bị từ chối"| SECURE
    VULN --> DB1
    SECURE --> DB2
    OAUTH --> DB3

    style VULN fill:#7f1d1d,stroke:#ef4444,color:#fca5a5
    style SECURE fill:#14532d,stroke:#22c55e,color:#86efac
    style HACKER fill:#78350f,stroke:#f59e0b,color:#fde68a
    style CLIENT fill:#1e3a5f,stroke:#3b82f6,color:#93c5fd
    style OAUTH fill:#3b0764,stroke:#a855f7,color:#d8b4fe
```

---

## 2. Luồng Đăng Nhập Hợp Lệ (Normal JWT Flow)

> [!NOTE]
> Đây là luồng hoạt động bình thường khi user đăng nhập. Server ký JWT bằng **RSA Private Key** với thuật toán **RS256**, và verify bằng **RSA Public Key**.

```mermaid
sequenceDiagram
    actor User as 👤 User (Client App)
    participant Server as 🖥️ Server (Port 4002)
    participant RSA as 🔑 RSA Key Config
    participant DB as 💾 H2 Database
    participant Filter as 🛡️ JWT Filter

    Note over User, Filter: ── PHASE 1: Đăng Nhập ──

    User->>+Server: POST /api/auth/login<br/>{ username: "alice", password: "alice123" }
    Server->>DB: findByUsername("alice")
    DB-->>Server: User { id:2, username:"alice", role:"user" }
    Server->>Server: passwordEncoder.matches(password, hash) ✅

    Note over Server, RSA: Tạo JWT Token bằng RS256

    Server->>RSA: getPrivateKey()
    RSA-->>Server: RSAPrivateKey (2048-bit)
    Server->>Server: Jwts.builder()<br/>.setSubject("alice")<br/>.claim("role", "user")<br/>.claim("userId", 2)<br/>.signWith(privateKey, RS256)<br/>.compact()

    Server-->>-User: 200 OK<br/>{ token: "eyJhbGciOiJSUzI1NiJ9...",<br/>  publicKey: "-----BEGIN PUBLIC KEY-----..." }

    Note over User, Filter: ── PHASE 2: Truy Cập Tài Nguyên ──

    User->>+Filter: GET /api/protected/profile<br/>Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
    Filter->>Filter: Decode JWT Header<br/>alg = "RS256"

    alt Server Vulnerable
        Filter->>Filter: Không kiểm tra whitelist ⚠️<br/>Vào nhánh RS256 → verify bằng publicKey
    else Server Secure
        Filter->>Filter: Kiểm tra whitelist ✅<br/>RS256 == RS256 → OK → verify bằng publicKey
    end

    Filter->>RSA: getPublicKey()
    RSA-->>Filter: RSAPublicKey
    Filter->>Filter: Jwts.parserBuilder()<br/>.setSigningKey(publicKey)<br/>.parseClaimsJws(token) ✅

    Filter->>Filter: Set SecurityContext<br/>Authentication: alice / ROLE_USER
    Filter-->>-User: 200 OK<br/>{ message: "Hello alice", role: "user" }
```

---

## 3. Luồng Tấn Công CVE-2015-9235 (Chi Tiết Từng Bước)

> [!CAUTION]
> Đây là luồng tấn công **Algorithm Confusion** khai thác lỗ hổng CVE-2015-9235. Attacker đổi thuật toán từ RS256 sang HS256 và dùng **Public Key** (công khai) làm **HMAC Secret** để ký token giả.

### 3.1 Tổng Quan Các Bước Tấn Công

```mermaid
flowchart TD
    START(["🔴 BẮT ĐẦU TẤN CÔNG"])

    subgraph STEP1["BƯỚC 1 — Thu thập Public Key"]
        S1A["Attacker mở Hacker Tool<br/>(localhost:3001)"]
        S1B["Nhập Public Key PEM<br/>vào textarea thủ công"]
        S1C["Hoặc: Copy từ<br/>GET /api/public-key<br/>của server vulnerable"]
        S1D["✅ Lưu publicKeyPem<br/>vào biến state"]
    end

    subgraph STEP2["BƯỚC 2 — Phân Tích JWT Gốc (Tùy chọn)"]
        S2A["Paste JWT token gốc<br/>(lấy từ login hợp lệ)"]
        S2B["decodeJwt(token)<br/>Base64 decode header + payload"]
        S2C["Trích xuất thông tin:<br/>alg=RS256, sub=alice, role=user"]
        S2D["Tự động điền payload<br/>vào form JSON Editor"]
    end

    subgraph STEP3["BƯỚC 3 — Tạo Token Giả (Forge)"]
        S3A["Chỉnh sửa Payload JSON<br/>sub: 'hacker' → 'admin'<br/>role: 'user' → 'admin'"]
        S3B["new TextEncoder().encode(publicKeyPem)<br/>→ Chuyển PEM thành byte array"]
        S3C["new jose.SignJWT(payload)<br/>.setProtectedHeader({alg: 'HS256'})<br/>.sign(secret)"]
        S3D["⚠️ Token Giả tạo thành công!<br/>alg=HS256, role=admin<br/>Ký bằng HMAC(publicKey)"]
    end

    START --> S1A --> S1B --> S1D
    S1A --> S1C --> S1D
    S1D --> S2A --> S2B --> S2C --> S2D
    S2D --> S3A --> S3B --> S3C --> S3D

    style START fill:#7f1d1d,stroke:#ef4444,color:#fff
    style STEP1 fill:#1e1b4b,stroke:#6366f1,color:#c7d2fe
    style STEP2 fill:#172554,stroke:#3b82f6,color:#93c5fd
    style STEP3 fill:#422006,stroke:#f59e0b,color:#fde68a
```

### 3.2 Chi Tiết Kỹ Thuật: Quá Trình Server Xử Lý Token Giả

```mermaid
flowchart TD
    TOKEN["📨 Forged Token đến Server<br/>Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."]

    PARSE["VulnerableJwtFilter.doFilterInternal()<br/>Decode JWT header thủ công"]
    READ_ALG["Đọc alg từ header<br/>alg = 'HS256' ⚠️"]
    
    CHECK{"Phân nhánh<br/>theo alg?"}
    
    subgraph VULN_HS256["❌ NHÁNH HS256 (LỖ HỔNG)"]
        VH1["rsaKeyConfig.getPublicKeyPem()<br/>.getBytes(UTF_8)"]
        VH2["Keys.hmacShaKeyFor(keyBytes)<br/>→ SecretKey từ publicKey PEM"]
        VH3["Jwts.parserBuilder()<br/>.setSigningKey(secretKey)<br/>.parseClaimsJws(token)"]
        VH4["HMAC verify:<br/>HMAC-SHA256(publicKey, header.payload)<br/>== signature trong token"]
        VH5["✅ CHỮ KÝ KHỚP!<br/>Vì attacker cũng ký bằng<br/>HMAC-SHA256(publicKey, ...)"]
    end
    
    subgraph VULN_RS256["✅ NHÁNH RS256 (Bình thường)"]
        VR1["Jwts.parserBuilder()<br/>.setSigningKey(rsaPublicKey)<br/>.parseClaimsJws(token)"]
        VR2["RSA verify signature"]
    end
    
    subgraph VULN_NONE["❌ NHÁNH NONE (Lỗ hổng phụ)"]
        VN1["Decode payload trực tiếp<br/>Không verify chữ ký"]
    end

    GRANT["🔓 SecurityContext SET!<br/>sub=hacker, role=ADMIN<br/>Attacker có TOÀN QUYỀN!"]
    
    ADMIN_API["Truy cập /api/admin/**<br/>→ 200 OK ✅"]

    TOKEN --> PARSE --> READ_ALG --> CHECK
    CHECK -->|"HS256"| VH1
    VH1 --> VH2 --> VH3 --> VH4 --> VH5
    VH5 --> GRANT
    
    CHECK -->|"RS256"| VR1 --> VR2
    CHECK -->|"none"| VN1
    
    GRANT --> ADMIN_API

    style TOKEN fill:#1e293b,stroke:#64748b,color:#e2e8f0
    style CHECK fill:#581c87,stroke:#a855f7,color:#e9d5ff
    style VULN_HS256 fill:#450a0a,stroke:#ef4444,color:#fca5a5
    style VULN_RS256 fill:#14532d,stroke:#22c55e,color:#86efac
    style VULN_NONE fill:#78350f,stroke:#f59e0b,color:#fde68a
    style GRANT fill:#7f1d1d,stroke:#ef4444,color:#fff,stroke-width:3px
    style ADMIN_API fill:#7f1d1d,stroke:#ef4444,color:#fff
```

---

## 4. So Sánh: Server Vulnerable vs Server Secure

> [!IMPORTANT]
> Điểm khác biệt **duy nhất** giữa 2 server nằm ở **Algorithm Whitelist**. Server Secure kiểm tra `alg` **TRƯỚC KHI** verify, trong khi Server Vulnerable tin tưởng hoàn toàn vào giá trị `alg` từ JWT header của client.

```mermaid
flowchart TD
    INPUT["📨 Forged Token Đến<br/>alg=HS256, role=admin"]

    subgraph VULNERABLE["❌ Server Vulnerable (Port 4002)"]
        direction TB
        V1["Decode JWT header"]
        V2["Đọc alg = HS256"]
        V3{"alg == ?"}
        V4["HS256 → Dùng publicKey<br/>làm HMAC secret"]
        V5["Verify HMAC ✅"]
        V6["🔓 GRANT ACCESS<br/>role=ADMIN"]
        
        V1 --> V2 --> V3
        V3 -->|"HS256"| V4 --> V5 --> V6
    end

    subgraph SECURE["✅ Server Secure (Port 4001)"]
        direction TB
        S1["Decode JWT header"]
        S2["Đọc alg = HS256"]
        S3{"alg == RS256?"}
        S4["🚫 REJECT!<br/>401 Unauthorized<br/>'Algorithm not allowed'"]
        S5["Verify RSA signature ✅"]
        
        S1 --> S2 --> S3
        S3 -->|"❌ HS256 ≠ RS256"| S4
        S3 -->|"✅ RS256"| S5
    end

    INPUT --> V1
    INPUT --> S1

    style VULNERABLE fill:#450a0a,stroke:#ef4444,color:#fca5a5
    style SECURE fill:#14532d,stroke:#22c55e,color:#86efac
    style V6 fill:#7f1d1d,stroke:#ef4444,color:#fff,stroke-width:3px
    style S4 fill:#166534,stroke:#22c55e,color:#fff,stroke-width:3px
    style INPUT fill:#1e293b,stroke:#64748b,color:#e2e8f0
```

---

## 5. Sequence Diagram Toàn Bộ Cuộc Tấn Công

```mermaid
sequenceDiagram
    actor Attacker as 🔴 Attacker (Hacker Tool)
    participant VulnServer as ❌ Server Vulnerable<br/>(Port 4002)
    participant RSA as 🔑 RSA KeyConfig
    participant Filter as VulnerableJwtFilter
    participant SecCtx as SecurityContext
    participant Admin as /api/admin/**

    Note over Attacker, Admin: ═══ BƯỚC 1: Thu Thập Public Key ═══
    
    Attacker->>Attacker: Nhập RSA Public Key PEM<br/>thủ công vào Hacker Tool

    Note over Attacker, Admin: ═══ BƯỚC 2: Phân Tích JWT Gốc (Tùy chọn) ═══
    
    Attacker->>Attacker: Paste JWT token hợp lệ<br/>(lấy từ login trước đó)
    Attacker->>Attacker: Base64 decode header + payload<br/>→ alg=RS256, sub=alice, role=user
    Attacker->>Attacker: Payload tự động điền vào JSON editor

    Note over Attacker, Admin: ═══ BƯỚC 3: Tạo Token Giả (Forge) ═══

    Attacker->>Attacker: Chỉnh sửa JSON Payload:<br/>{ sub:"hacker", role:"admin", userId:1 }
    Attacker->>Attacker: TextEncoder.encode(publicKeyPem)<br/>→ key bytes
    Attacker->>Attacker: jose.SignJWT(payload)<br/>.setProtectedHeader({alg:"HS256"})<br/>.sign(publicKeyBytes)
    Attacker->>Attacker: 💀 Forged Token Ready!<br/>eyJhbGciOiJIUzI1NiJ9...

    Note over Attacker, Admin: ═══ SỬ DỤNG TOKEN GIẢ ĐỂ TẤN CÔNG ═══

    Attacker->>+VulnServer: GET /api/admin/users<br/>Authorization: Bearer [FORGED_TOKEN]
    VulnServer->>Filter: doFilterInternal(request)
    
    Filter->>Filter: token.split(".")[0]<br/>→ Base64 decode header
    Filter->>Filter: header.get("alg") = "HS256"
    
    Note over Filter: ⚠️ KHÔNG kiểm tra whitelist!<br/>Tin tưởng alg từ client!
    
    Filter->>RSA: getPublicKeyPem()
    RSA-->>Filter: "-----BEGIN PUBLIC KEY-----\n..."
    
    Filter->>Filter: publicKeyPem.getBytes(UTF_8)<br/>→ byte[]
    Filter->>Filter: Keys.hmacShaKeyFor(keyBytes)<br/>→ SecretKey
    Filter->>Filter: Jwts.parserBuilder()<br/>.setSigningKey(hmacSecretKey)<br/>.parseClaimsJws(token)
    
    Note over Filter: 💀 HMAC verify PASS!<br/>Vì attacker ký bằng cùng key!
    
    Filter->>Filter: claims.get("role") = "admin"
    Filter->>SecCtx: Set Authentication<br/>sub=hacker, ROLE_ADMIN
    SecCtx-->>Filter: ✅
    
    Filter->>Admin: Forward request
    Admin-->>Filter: 200 OK - Admin data
    Filter-->>VulnServer: Response
    VulnServer-->>-Attacker: 🔓 200 OK<br/>{ users: [...], secrets: [...] }

    Note over Attacker, Admin: 💀 TẤN CÔNG THÀNH CÔNG!<br/>Attacker có quyền ADMIN!
```

---

## 6. Giải Thích Kỹ Thuật Sâu

### 6.1 Tại sao tấn công thành công?

| Yếu tố | Chi tiết |
|---------|---------|
| **Thuật toán RS256** | Asymmetric — ký bằng Private Key, verify bằng Public Key |
| **Thuật toán HS256** | Symmetric — ký VÀ verify bằng **cùng một** Secret Key |
| **Public Key** | Luôn **công khai** (ai cũng lấy được) |
| **Lỗ hổng** | Server dùng `publicKey` để verify → nếu `alg=HS256`, `publicKey` trở thành HMAC secret |
| **Kết quả** | Attacker biết `publicKey` → ký được token bất kỳ → chiếm quyền admin |

### 6.2 Chuỗi Logic Tấn Công

```
1. Server dùng RS256 → verify(token, publicKey)
2. Attacker đổi alg=HS256 trong JWT header
3. Server đọc alg=HS256 từ header → dùng publicKey làm HMAC secret
4. Attacker cũng dùng publicKey làm HMAC secret để ký token
5. HMAC(publicKey, data) ở server == HMAC(publicKey, data) ở attacker
6. → Chữ ký KHỚP → Server tin token hợp lệ → Cấp quyền ADMIN!
```

### 6.3 Cách Phòng Chống (Server Secure)

```mermaid
flowchart LR
    TOKEN["Token đến<br/>alg=HS256"] --> CHECK{"alg == RS256?"}
    CHECK -->|"❌ NO"| REJECT["🚫 401 Rejected<br/>'Algorithm not allowed'<br/>'Protected against CVE-2015-9235'"]
    CHECK -->|"✅ YES"| VERIFY["Verify RSA<br/>signature ✅"]
    
    style REJECT fill:#166534,stroke:#22c55e,color:#fff,stroke-width:2px
    style CHECK fill:#581c87,stroke:#a855f7,color:#e9d5ff
```

> [!TIP]
> **Giải pháp cốt lõi**: Kiểm tra `alg` từ JWT header **TRƯỚC** khi verify. Chỉ cho phép danh sách thuật toán đã đăng ký (whitelist). Trong [SecureJwtFilter.java](file:///d:/Document/Security/jwt-attack-demo/server-secure/src/main/java/com/attt/secure/filter/SecureJwtFilter.java#L74-L81):
> ```java
> if (!"RS256".equals(alg)) {
>     // 🚫 TỪ CHỐI ngay lập tức
>     writeError(response, 401, "Algorithm not allowed", ...);
>     return;
> }
> ```

---

## 7. Mapping Sơ Đồ → Mã Nguồn

| Bước trong sơ đồ | File mã nguồn | Dòng quan trọng |
|---|---|---|
| Tạo RSA Key Pair | [RsaKeyConfig.java](file:///d:/Document/Security/jwt-attack-demo/server-vulnerable/src/main/java/com/attt/vulnerable/config/RsaKeyConfig.java#L22-L31) | `KeyPairGenerator.getInstance("RSA")` |
| Login & Ký JWT RS256 | [AuthController.java](file:///d:/Document/Security/jwt-attack-demo/server-vulnerable/src/main/java/com/attt/vulnerable/controller/AuthController.java#L64-L71) | `signWith(privateKey, RS256)` |
| Expose Public Key | [AuthController.java](file:///d:/Document/Security/jwt-attack-demo/server-vulnerable/src/main/java/com/attt/vulnerable/controller/AuthController.java#L25-L36) | `GET /api/public-key` |
| **LỖ HỔNG: HS256 branch** | [VulnerableJwtFilter.java](file:///d:/Document/Security/jwt-attack-demo/server-vulnerable/src/main/java/com/attt/vulnerable/filter/VulnerableJwtFilter.java#L83-L98) | `Keys.hmacShaKeyFor(publicKeyPem.getBytes())` |
| **BẢO VỆ: Algorithm whitelist** | [SecureJwtFilter.java](file:///d:/Document/Security/jwt-attack-demo/server-secure/src/main/java/com/attt/secure/filter/SecureJwtFilter.java#L74-L81) | `if (!"RS256".equals(alg))` |
| Forge Token (Hacker Tool) | [JwtAttackPage.jsx](file:///d:/Document/Security/jwt-attack-demo/hacker-tool/src/pages/JwtAttackPage.jsx#L67-L100) | `jose.SignJWT(payload).sign(publicKeyBytes)` |
