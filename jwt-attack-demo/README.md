# Hướng Dẫn Chạy Dự Án JWT & OAuth2 Security Demo

Dự án này là mô hình thực hành tấn công và phòng thủ bảo mật API bằng JWT và OAuth2. Dưới đây là hướng dẫn khởi chạy nhanh.

---

## 🛠️ Yêu Cầu Hệ Thống
* **Node.js** (để chạy các ứng dụng React).
* **Cách 1 (Khuyên dùng):** **Docker Desktop** (không cần cài Java hay Maven cục bộ).
* **Cách 2 (Thủ công):** **Java JDK 17+** và **Maven**.

---

## 🚀 Cách 1: Chạy Bằng Docker Compose (Khuyên Dùng)

### 1. Khởi động 3 Backend Server (Spring Boot):
Mở terminal tại thư mục gốc của dự án này:
```bash
docker-compose up --build
```
* **Secure Server:** Port `4001`
* **Vulnerable Server:** Port `4002`
* **OAuth Server:** Port `4003`

### 2. Khởi động các ứng dụng Frontend:
Mở **2 terminal mới** để khởi chạy 2 ứng dụng React:

* **Terminal 1 — Client App (Port 3000):**
  ```bash
  cd client
  npm install
  npm run dev
  ```

* **Terminal 2 — Hacker Tool (Port 3001):**
  ```bash
  cd hacker-tool
  npm install
  npm run dev
  ```

---

## 💻 Cách 2: Chạy Thủ Công (Không dùng Docker)
Nếu không dùng Docker, bạn hãy mở **5 terminal** riêng biệt để khởi chạy từng dịch vụ:

1. **Terminal 1: OAuth Server (Port 4003)**
   ```bash
   cd oauth-server
   mvn spring-boot:run
   ```
2. **Terminal 2: Server Vulnerable (Port 4002)**
   ```bash
   cd server-vulnerable
   mvn spring-boot:run
   ```
3. **Terminal 3: Server Secure (Port 4001)**
   ```bash
   cd server-secure
   mvn spring-boot:run
   ```
4. **Terminal 4: Client App (Port 3000)**
   ```bash
   cd client
   npm install
   npm run dev
   ```
5. **Terminal 5: Hacker Tool (Port 3001)**
   ```bash
   cd hacker-tool
   npm install
   npm run dev
   ```

---

## 🔑 Tài Khoản Test (H2 Database)
Dữ liệu mẫu đã được nạp sẵn khi khởi động server:
* `admin` / `admin123` (Quyền **ADMIN**)
* `alice` / `alice123` (Quyền **USER**)
* `bob` / `bob123` (Quyền **USER**)

---

## 🛑 Chú ý về module `server-no-auth` (v1)
Thư mục dự án hiện tại chưa chứa module `server-no-auth` chạy trên cổng 4000. Nếu bạn cần demo trọn vẹn cả v1 (No-Auth), hãy báo với mình để mình tạo giúp bạn module này ngay lập tức!
