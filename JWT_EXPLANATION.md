# Giải thích chi tiết: JWT hoạt động như thế nào trong hệ thống đấu giá

## Mục lục
- [1. JWT là gì?](#1-jwt-là-gì)
- [2. Cấu trúc code JWT (JwtUtil.java)](#2-cấu-trúc-code-jwt-jwtutiljava)
- [3. Luồng đăng nhập — tạo token](#3-luồng-đăng-nhập--tạo-token)
- [4. Client lưu token như thế nào](#4-client-lưu-token-như-thế-nào)
- [5. Luồng đặt giá — xác thực token](#5-luồng-đặt-giá--xác-thực-token)
- [6. Refresh token — khi access token hết hạn](#6-refresh-token--khi-access-token-hết-hạn)
- [7. Sơ đồ tổng thể](#7-sơ-đồ-tổng-thể)
- [8. Tóm tắt bảo mật](#8-tóm-tắt-bảo-mật)

---

## 1. JWT là gì?

**JWT (JSON Web Token)** là một chuỗi gồm 3 phần, phân cách bằng dấu chấm (`.`):

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.dQw4w9WgXcQ
```

1. **Header** — chứa thuật toán ký (VD: `HS256`)
2. **Payload** — chứa dữ liệu (userId, username, role, hạn dùng...)
3. **Signature** — chữ ký số, dùng để xác thực token không bị giả mạo

Trong hệ thống này, JWT dùng thuật toán **HMAC-SHA256** với một secret key chung server. Chỉ server biết key này, nên chỉ server mới tạo được token hợp lệ.

---

## 2. Cấu trúc code JWT (JwtUtil.java)

**File:** `src/main/java/server/util/JwtUtil.java`

### 2.1. Secret Key (dòng 24-25)

```java
private static final String SECRET = "BaiTapLonNhom3HeThongDauGiaTrucTuyen2026!Kh0aBiMatJWT";
private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
```

- Secret key là một chuỗi cố định, được chuyển thành `SecretKey` của thuật toán HMAC-SHA256.
- Key này nằm hoàn toàn ở server, client không bao giờ biết → client KHÔNG THỂ tự tạo token.

### 2.2. Thời hạn token (dòng 26-27)

```java
private static final long ACCESS_TOKEN_EXPIRATION_MS = 15 * 60 * 1000;      // 15 phút
private static final long REFRESH_TOKEN_EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000; // 7 ngày
```

- **Access token**: 15 phút — ngắn hạn, dùng cho mọi request (đặt giá, xem lịch sử...)
- **Refresh token**: 7 ngày — dài hạn, chỉ dùng để cấp lại access token mới

### 2.3. Tạo access token (dòng 34-47)

```java
public static String generateToken(int userId, String username, String role) {
    Date now = new Date();
    Date expiration = new Date(now.getTime() + ACCESS_TOKEN_EXPIRATION_MS);  // 15 phút sau

    return Jwts.builder()
            .subject(String.valueOf(userId))        // sub = "1"
            .claim("username", username)            // username = "alice"
            .claim("role", role)                    // role = "BIDDER"
            .claim("token_type", "access")          // phân biệt access vs refresh
            .issuedAt(now)                          // thời điểm tạo
            .expiration(expiration)                 // thời điểm hết hạn
            .signWith(SECRET_KEY)                   // ký số
            .compact();                             // nén thành chuỗi JWT
}
```

Access token sau khi tạo sẽ có dạng (giải mã):

```json
{
  "sub": "1",
  "username": "alice",
  "role": "BIDDER",
  "token_type": "access",
  "iat": 1748240920,
  "exp": 1748241820
}
```

### 2.4. Tạo refresh token (dòng 52-65)

Refresh token **cấu trúc giống hệt** access token, chỉ khác:
- `token_type` = `"refresh"` (thay vì `"access"`)
- `expiration` = 7 ngày (thay vì 15 phút)

Mục đích: để server có thể phân biệt và không cho phép refresh token dùng để đặt giá.

### 2.5. Xác thực token (dòng 71-82)

```java
public static Claims validateToken(String token) {
    try {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(SECRET_KEY)        // B1: xác minh chữ ký số
                .build()
                .parseSignedClaims(token);     // B2: giải mã payload
        return jws.getPayload();               // B3: trả về claims (dữ liệu bên trong)
    } catch (JwtException | IllegalArgumentException e) {
        return null;                           // Token không hợp lệ/hết hạn
    }
}
```

Quá trình xác thực gồm 2 bước:
1. **Kiểm tra chữ ký số** — nếu token bị sửa, chữ ký không khớp → throw exception
2. **Kiểm tra thời hạn (exp)** — thư viện JJWT tự động kiểm tra, nếu hết hạn → throw exception

Nếu cả 2 đều OK, trả về dữ liệu claims. Nếu không, trả về `null`.

### 2.6. Xác thực refresh token riêng (dòng 88-94)

```java
public static Claims validateRefreshToken(String token) {
    Claims claims = validateToken(token);           // Kiểm tra chữ ký + hạn dùng
    if (claims == null) return null;
    String tokenType = claims.get("token_type", String.class);
    if (!"refresh".equals(tokenType)) return null;  // Phải là refresh token
    return claims;
}
```

- Gọi `validateToken()` để kiểm tra chữ ký + hạn dùng
- Kiểm tra thêm `token_type` phải là `"refresh"`
- Nếu dùng access token để refresh → bị từ chối

---

## 3. Luồng đăng nhập — tạo token

### 3.1. Client gửi request đăng nhập

**File:** `src/main/java/client/controller/LoginController.java` — dòng 82-101

```java
private void handleLogin(ActionEvent event) {
    String username = usernameField.getText();
    String password = passwordField.getText();

    User user = authClient.login(username, password);  // Gửi request đến server
    ClientSession.setCurrentUser(user);                // Lưu user vào session
    openDashboard(event, user);                        // Mở dashboard tương ứng
}
```

### 3.2. AuthClient gửi request + nhận token

**File:** `src/main/java/client/network/AuthClient.java` — dòng 27-45

```java
public User login(String username, String password) {
    // Tạo payload gửi lên server
    LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
    payload.put("username", username);
    payload.put("password", password);

    // Gửi qua socket
    Map<String, Object> response = socketClient.sendRequest("login", payload);

    // Lưu token từ server
    Object accessTokenObj = response.get("accessToken");
    if (accessTokenObj != null) {
        ClientSession.setAuthToken(String.valueOf(accessTokenObj));  // Lưu access token
    }
    Object refreshTokenObj = response.get("refreshToken");
    if (refreshTokenObj != null) {
        ClientSession.setRefreshToken(String.valueOf(refreshTokenObj));  // Lưu refresh token
    }

    return toUser(response);
}
```

Client nhận về **2 token** từ server:
- `accessToken` — dùng cho các request tiếp theo
- `refreshToken` — dùng khi access token hết hạn

### 3.3. Server xử lý đăng nhập

**File:** `src/main/java/server/service/AuthService.java` — dòng 34-63

```java
public Map<String, Object> login(String username, String password) {
    // B1: Tìm user trong database
    User user = userDAO.findByUsername(username)
            .orElseThrow(() -> new AuthenticationException("Không tìm thấy tên đăng nhập"));

    // B2: Kiểm tra user không bị BANNED
    if (user.getStatus() == UserStatus.BANNED) {
        throw new AuthenticationException("Tài khoản đã bị khoá");
    }

    // B3: Kiểm tra mật khẩu
    boolean passwordMatches = PasswordUtil.verify(password, user.getPassword());
    if (!passwordMatches) throw new AuthenticationException("Sai mật khẩu");

    // B4: Cập nhật trạng thái LOGIN
    user.setStatus(UserStatus.LOGIN);
    userDAO.update(user);

    // B5: Tạo access token + refresh token
    String accessToken = JwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
    String refreshToken = JwtUtil.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());

    // B6: Trả về cả token lẫn thông tin user
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("accessToken", accessToken);
    result.put("refreshToken", refreshToken);
    result.put("user", user);
    return result;
}
```

### 3.4. RequestHandler trả về response cho client

**File:** `src/main/java/server/network/RequestHandler.java` — dòng 88-112

```java
private String handleLogin(Map<String, Object> request, ClientHandler clientHandler) {
    Map<String, Object> loginResult = authService.login(username, password);

    String accessToken = (String) loginResult.get("accessToken");
    String refreshToken = (String) loginResult.get("refreshToken");
    User user = (User) loginResult.get("user");

    // Gán token cho client handler (phía server)
    clientHandler.markAuthenticated(user.getId(), accessToken);

    // Tạo response gửi về client
    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
    response.put("status", "success");
    response.put("accessToken", accessToken);    // <-- access token
    response.put("refreshToken", refreshToken);  // <-- refresh token
    response.put("userId", user.getId());
    response.put("username", user.getUsername());
    response.put("role", user.getRole());
    // ... thêm walletBalance nếu là Bidder/Seller
}
```

---

## 4. Client lưu token như thế nào

**File:** `src/main/java/client/application/ClientSession.java`

```java
public final class ClientSession {
    private static User currentUser;
    private static String authToken;      // JWT access token (15 phút)
    private static String refreshToken;   // Refresh token (7 ngày)

    public static void setAuthToken(String token) { authToken = token; }
    public static String getAuthToken() { return authToken; }

    public static void setRefreshToken(String token) { refreshToken = token; }
    public static String getRefreshToken() { return refreshToken; }

    public static void clear() {
        currentUser = null;
        authToken = null;
        refreshToken = null;   // Xoá hết token khi logout
    }
}
```

Token được lưu trong **static variables** (bộ nhớ RAM), không phải file hay database. Khi tắt ứng dụng, token mất.

---

## 5. Luồng đặt giá — xác thực token

Đây là phần quan trọng nhất: làm sao server biết chắc chắn ai đang đặt giá.

### 5.1. Client gửi token kèm request đặt giá

**File:** `src/main/java/client/network/BidClient.java` — dòng 57-65

```java
public void placeBid(String auctionId, double amount) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("auctionId", auctionId);
    payload.put("amount", amount);
    payload.put("token", ClientSession.getAuthToken());  // <-- QUAN TRỌNG: gửi kèm JWT
    Map<String, Object> response = socketClient.sendRequestWithAutoRefresh("place_bid", payload);
    ensureSuccess(response);
}
```

**`sendRequestWithAutoRefresh()` khác `sendRequest()` ở điểm nào?**
- Nếu token hết hạn (sau 15 phút), `sendRequest()` sẽ trả về lỗi "Token không hợp lệ"
- `sendRequestWithAutoRefresh()` tự động phát hiện lỗi này → gọi API `refresh_token` với refresh token → nhận access token mới → gửi lại request với token mới → user không bị mất thao tác

**Tại sao không gửi bidderId mà gửi token?**
- Nếu gửi `bidderId`, hacker có thể sửa thành `bidderId` của người khác
- Token đã ký, không thể giả mạo → server dùng token để **trích xuất userId thật**

### 5.2. Server xác thực token

**File:** `src/main/java/server/network/RequestHandler.java` — dòng 288-304

```java
private String handlePlaceBid(Map<String, Object> request) {
    // B1: Lấy token từ request
    String token = getRequiredText(request, "token");

    // B2: Xác thực token — lấy userId thật từ JWT
    User user = authService.authenticate(token);

    // B3: Kiểm tra user có phải bidder không
    if (!(user instanceof Bidder bidder))
        throw new AuthenticationException("User khong phai bidder");

    // B4: Lấy dữ liệu từ request
    String auctionId = getRequiredText(request, "auctionId");
    double amount = getRequiredDouble(request, "amount");

    // B5: Đặt giá — bidder đã được xác thực qua JWT
    BidTransaction bid = bidService.placeBid(auctionId, bidder, amount);

    return JsonUtils.toJson(Map.of("status", "success", "bidId", bid.getId()));
}
```

### 5.3. AuthService.authenticate() — giải mã token chi tiết

**File:** `src/main/java/server/service/AuthService.java` — dòng 84-98

```java
public User authenticate(String token) {
    // B1: Giải mã JWT lấy userId
    int userId = JwtUtil.getUserIdFromToken(token);
    if (userId <= 0) {
        throw new AuthenticationException("Token không hợp lệ hoặc đã hết hạn");
    }

    // B2: Tìm user trong database
    User user = userDAO.findById(userId)
            .orElseThrow(() -> new AuthenticationException("Không tìm thấy user của token"));

    // B3: Kiểm tra user không bị BANNED
    if (user.getStatus() == UserStatus.BANNED) {
        throw new AuthenticationException("Tài khoản đã bị khoá");
    }

    return user;
}
```

### 5.4. JwtUtil.getUserIdFromToken() — trích xuất userId

**File:** `src/main/java/server/util/JwtUtil.java` — dòng 113-121

```java
public static int getUserIdFromToken(String token) {
    Claims claims = validateToken(token);         // Xác thực chữ ký + hạn dùng
    if (claims == null) return -1;
    return Integer.parseInt(claims.getSubject()); // sub = "1" → 1
}
```

**Tóm tắt luồng đặt giá:**
```
Client:                           Server:
  placeBid(auctionId, amount)      
    ↓                               
  Gửi request:                     
  {                                
    "auctionId": "5",              
    "amount": 150.0,               
    "token": "eyJhbGciOiJ..."  ──→ 1. Xác thực chữ ký token
  }                                2. Giải mã → userId = 1
                                  3. Kiểm tra user không bị BANNED
                                  4. Lấy bidder từ database
                                  5. Xử lý đặt giá với bidderId = 1
                                   (KHÔNG tin bidderId từ client)
```

---

## 6. Refresh token — khi access token hết hạn

Access token chỉ sống 15 phút. Khi hết hạn, thay vì bắt user đăng nhập lại, hệ thống dùng refresh token để cấp access token mới.

### 6.1. Cấp lại access token từ refresh token

**File:** `src/main/java/server/util/JwtUtil.java` — dòng 100-108

```java
public static String refreshAccessToken(String refreshToken) {
    // Xác thực refresh token (kiểm tra chữ ký + hạn dùng + token_type == "refresh")
    Claims claims = validateRefreshToken(refreshToken);
    if (claims == null) return null;

    // Trích xuất thông tin từ refresh token
    int userId = Integer.parseInt(claims.getSubject());
    String username = claims.get("username", String.class);
    String role = claims.get("role", String.class);

    // Tạo access token mới (hạn 15 phút từ thời điểm hiện tại)
    return generateToken(userId, username, role);
}
```

**Quan trọng:** Access token mới được tạo từ thông tin trong refresh token — không cần hỏi lại database.

### 6.2. API refresh token

**File:** `src/main/java/server/network/RequestHandler.java` — dòng 119-124

```java
private String handleRefreshToken(Map<String, Object> request) {
    String refreshToken = getRequiredText(request, "refreshToken");
    Map<String, Object> result = authService.refreshAccessToken(refreshToken);
    String newAccessToken = (String) result.get("accessToken");
    return JsonUtils.toJson(Map.of("status", "success", "accessToken", newAccessToken));
}
```

### 6.3. So sánh access token và refresh token

| Thuộc tính | Access Token | Refresh Token |
|------------|-------------|---------------|
| Thời hạn | 15 phút | 7 ngày |
| `token_type` | `"access"` | `"refresh"` |
| Dùng để đặt giá? | ✅ Có | ❌ Không |
| Dùng để refresh? | ❌ Không | ✅ Có |
| Tần suất dùng | Mỗi request | Khi access token hết hạn |

---

## 7. Sơ đồ tổng thể

```
┌─────────────────────────────────────────────────────────────────────┐
│                        HỆ THỐNG JWT TOKEN                           │
└─────────────────────────────────────────────────────────────────────┘

PHẦN 1: ĐĂNG NHẬP
═══════════════════════════════════════════════════════════════════════

  LoginController              AuthClient                Server
  ┌──────────────┐            ┌──────────────┐          ┌──────────────┐
  │ username:     │            │              │          │ 1. Xác thực  │
  │ "alice"      │───────────>│ login()      │─────────>│    user/pass │
  │ password:    │            │              │          │              │
  │ "secret"     │            │              │          │ 2. Tạo JWT   │
  │              │            │              │          │    sub: "1"   │
  │              │            │              │          │    username   │
  │              │            │              │          │    role      │
  │              │            │              │          │              │
  │              │<───────────│              │<─────────│ 3. Trả về    │
  │              │            │ Lưu token    │          │    access    │
  │              │            │ vào          │          │    + refresh │
  │              │            │ ClientSession│          │    token     │
  └──────────────┘            └──────────────┘          └──────────────┘

PHẦN 2: ĐẶT GIÁ (dùng JWT để xác thực)
═══════════════════════════════════════════════════════════════════════

  BidderController             BidClient                  Server
  ┌──────────────┐            ┌──────────────┐          ┌──────────────┐
  │ placeBid(    │            │              │          │              │
  │ auction,     │───────────>│ placeBid()   │─────────>│ 1. Lấy token │
  │ amount)      │            │ {            │          │    từ request│
  │              │            │  auctionId,  │          │              │
  │              │            │  amount,     │          │ 2. Giải mã   │
  │              │            │  token: JWT  │          │    → userId  │
  │              │            │ }            │          │              │
  │              │            │              │          │ 3. Kiểm tra  │
  │              │            │              │          │    user ≠ ban│
  │              │            │              │          │              │
  │              │            │              │          │ 4. Đặt giá   │
  │              │            │              │          │    với userId │
  │              │            │              │          │    thật      │
  └──────────────┘            └──────────────┘          └──────────────┘

PHẦN 3: REFRESH TOKEN (khi access token hết hạn)
═══════════════════════════════════════════════════════════════════════

  Client                         Server
  ┌──────────────┐             ┌────────────────────────────────────┐
  │ access token │             │                                    │
  │ hết hạn      │───────────>│ 1. validateRefreshToken(refresh)    │
  │              │  refresh    │    - Kiểm tra chữ ký               │
  │ Gửi refresh  │  token     │    - Kiểm tra hạn dùng             │
  │ token        │             │    - Kiểm tra token_type="refresh" │
  │              │             │                                    │
  │              │             │ 2. generateToken(userId, username, │
  │              │             │        role) → access token mới    │
  │              │             │                                    │
  │              │<────────────│ 3. Trả về access token mới         │
  └──────────────┘             └────────────────────────────────────┘
```

---

## 8. Tóm tắt bảo mật

### JWT giải quyết những vấn đề gì?

| Vấn đề bảo mật | JWT giải quyết thế nào | Code minh hoạ |
|---------------|------------------------|---------------|
| **Giả mạo userId** | Server lấy userId từ JWT (đã ký số), không tin client | `RequestHandler.java:291` — `authService.authenticate(token)` |
| **Giả mạo role** | Role nằm trong JWT, không thể sửa vì chữ ký sẽ hỏng | `JwtUtil.java:41` — `.claim("role", role)` |
| **Replay attack** | Token có hạn 15 phút (exp), nếu đánh cắp cũng không dùng được lâu | `JwtUtil.java:26` — `ACCESS_TOKEN_EXPIRATION_MS = 15 * 60 * 1000` |
| **User bị ban vẫn dùng được token cũ** | Mỗi request đều kiểm tra `UserStatus.BANNED` trong database | `AuthService.java:93` — `if (user.getStatus() == UserStatus.BANNED)` |
| **Dùng access token để refresh** | Server kiểm tra `token_type` phải là `"refresh"` | `JwtUtil.java:91` — `if (!"refresh".equals(tokenType))` |
| **Dùng refresh token để đặt giá** | Server kiểm tra `token_type` phải là `"access"` (mặc định từ `generateToken`) | `JwtUtil.java:43` — `.claim("token_type", "access")` |
| **Server lưu session** | Không cần! JWT là stateless, thông tin nằm trong token | Không cần database session |

### Tại sao không dùng session đơn giản (như cookie)?

1. **Stateless** — Server không cần lưu session trong database hay bộ nhớ → dễ mở rộng (scale)
2. **Bảo mật tốt hơn** — Token có chữ ký, không thể giả mạo
3. **Linh hoạt** — Token chứa sẵn thông tin user (userId, role), server không cần query database mỗi request
4. **Chống CSRF** — Token gửi qua request body (socket), không dùng cookie tự động

### Hạn chế của JWT trong hệ thống này

1. **Không thể thu hồi token ngay lập tức** — Nếu token bị đánh cắp, hacker có thể dùng trong 15 phút (thời gian hết hạn). Server chỉ có thể chặn bằng cách ban user (kiểm tra `BANNED` ở mỗi request).
2. **Secret key cố định** — Secret key nằm trong code (`JwtUtil.java:24`). Trong thực tế, nên đưa ra file cấu hình hoặc biến môi trường.