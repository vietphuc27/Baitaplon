# BTL_N3 - Hệ thống đấu giá trực tuyến

## 1. Mô tả bài toán và phạm vi hệ thống

Đây là ứng dụng đấu giá trực tuyến dạng desktop, xây dựng theo mô hình Client/Server. Client JavaFX kết nối tới Server qua TCP Socket, trao đổi dữ liệu JSON, còn Server xử lý nghiệp vụ đấu giá và lưu trữ dữ liệu trong MySQL.

Hệ thống hỗ trợ 3 nhóm người dùng chính:

- **Admin**: quản lý tài khoản người dùng, khóa/mở khóa user, hủy phiên đấu giá.
- **Seller**: tạo, sửa, theo dõi và kết thúc phiên đấu giá của mình.
- **Bidder**: xem danh sách phiên đấu giá, đặt giá, sử dụng auto-bid, xem lịch sử đặt giá và nạp tiền vào ví.

Phạm vi hiện tại tập trung vào luồng đấu giá cơ bản: đăng ký/đăng nhập, quản lý phiên đấu giá, đặt giá theo thời gian thực, cập nhật trạng thái phiên theo thời gian, thanh toán cho người bán khi phiên kết thúc và quản trị người dùng.

## 2. Công nghệ sử dụng, môi trường chạy và yêu cầu cài đặt

### Công nghệ sử dụng

- **Java 25**: ngôn ngữ lập trình chính, `pom.xml` đang cấu hình `source/target` là `25`.
- **JavaFX 21.0.6**: xây dựng giao diện desktop.
- **Maven Wrapper**: build/run project bằng `mvnw` hoặc `mvnw.cmd`, không bắt buộc cài Maven riêng.
- **MySQL 8.x**: cơ sở dữ liệu cho user, item, auction và bid transaction.
- **Gson**: chuyển đổi JSON cho giao tiếp socket.
- **MySQL Connector/J**: kết nối Java tới MySQL.
- **JWT**: tạo và xác thực access token/refresh token.
- **Cloudinary SDK/API**: upload và quản lý ảnh sản phẩm đấu giá.
- **JUnit 5, JaCoCo, Checkstyle**: kiểm thử, đo coverage và kiểm tra coding convention.

### Yêu cầu môi trường

- Cài **JDK 25** hoặc mới hơn có hỗ trợ biên dịch source level 25.
- Cài và khởi động **MySQL Server**.
- Máy chạy Client cần có môi trường đồ họa để hiển thị JavaFX.
- Có kết nối Internet nếu sử dụng chức năng upload ảnh lên Cloudinary.

Kiểm tra nhanh môi trường:

```bash
java -version
mysql --version
```

### Cấu hình database

Mặc định ứng dụng kết nối tới:

```text
DB_URL=jdbc:mysql://localhost:3306/auction_db
DB_USER=root
DB_PASSWORD=
```

Nếu MySQL của máy bạn dùng tài khoản/mật khẩu khác, có thể cấu hình bằng biến môi trường:

Linux/macOS:

```bash
export DB_URL="jdbc:mysql://localhost:3306/auction_db"
export DB_USER="root"
export DB_PASSWORD="your_password"
```

Windows PowerShell:

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/auction_db"
$env:DB_USER = "root"
$env:DB_PASSWORD = "your_password"
```

Hoặc truyền trực tiếp khi chạy Server bằng các tham số `-Ddb.url`, `-Ddb.user`, `-Ddb.password`.

### Tạo database và dữ liệu mẫu

Linux/macOS, Git Bash hoặc Windows CMD:

```bash
mysql -u root -p < setupuser.sql
```

Windows PowerShell:

```powershell
Get-Content .\setupuser.sql | mysql -u root -p
```

Nếu đang ở trong MySQL Shell:

```sql
SOURCE /duong/dan/toi/setupuser.sql;
```

Tài khoản mẫu sau khi import database:

| Vai trò | Username | Password |
| --- | --- | --- |
| Admin | `admin` | `admin123` |
| Seller | `seller1` | `seller123` |
| Bidder | `bidder1` | `bidder123` |
| Bidder | `bidder2` | `bidder123` |

## 3. Cấu trúc thư mục và module chính

```text
.
├── pom.xml                         # Cấu hình Maven, dependency và plugin
├── mvnw, mvnw.cmd                  # Maven Wrapper cho Linux/macOS và Windows
├── setupuser.sql                   # Script tạo database, bảng và dữ liệu mẫu
├── checkstyle.xml                  # Cấu hình Checkstyle
├── src/main/java
│   ├── com/example/btl_n3
│   │   └── Main.java               # Entry point Client JavaFX
│   ├── client
│   │   ├── application             # Điều hướng màn hình và session client
│   │   ├── controller              # Controller JavaFX cho Login/Admin/Seller/Bidder
│   │   └── network                 # Client socket và các client API theo nghiệp vụ
│   ├── common
│   │   ├── exceptions              # Exception dùng chung
│   │   ├── itemfactory             # Factory tạo item theo loại
│   │   ├── models                  # Entity User, Item, Auction, BidTransaction...
│   │   ├── userfactory             # Factory tạo user theo role
│   │   └── utils                   # Json, validate, format, time, upload helper
│   └── server
│       ├── application             # AuctionServer và ServerLauncher
│       ├── config                  # Kết nối DB và migration
│       ├── manager                 # Quản lý auction, session, connection, auto-bid
│       ├── network                 # ClientHandler và RequestHandler
│       ├── repository              # DAO truy xuất MySQL
│       ├── service                 # Lớp nghiệp vụ auth/user/item/auction/bid/cloudinary
│       └── util                    # JWT và mã hóa mật khẩu
├── src/main/resources/view         # File FXML và CSS giao diện JavaFX
└── src/test/java                   # Unit test cho client, server, service, manager, model
```

## 4. Các lệnh dòng lệnh để build, test và chạy chương trình

> Chọn đúng lệnh theo hệ điều hành/shell đang dùng. Trên Linux/macOS dùng `./mvnw`, trên Windows dùng `.\mvnw.cmd`.

### Build project

Linux/macOS:

```bash
./mvnw clean compile
```

Windows PowerShell/CMD:

```powershell
.\mvnw.cmd clean compile
```

### Chạy test

Linux/macOS:

```bash
./mvnw test
```

Windows PowerShell/CMD:

```powershell
.\mvnw.cmd test
```

### Kiểm tra style

Linux/macOS:

```bash
./mvnw validate
```

Windows PowerShell/CMD:

```powershell
.\mvnw.cmd validate
```

### Chạy Server

Linux/macOS:

```bash
./mvnw exec:java -Dexec.mainClass=server.application.ServerLauncher
```

Windows PowerShell:

```powershell
.\mvnw.cmd exec:java "-Dexec.mainClass=server.application.ServerLauncher"
```

Windows CMD:

```cmd
mvnw.cmd exec:java -Dexec.mainClass=server.application.ServerLauncher
```

Nếu cần truyền cấu hình database trực tiếp:

Linux/macOS:

```bash
./mvnw exec:java -Dexec.mainClass=server.application.ServerLauncher -Ddb.user=root -Ddb.password=your_password
```

Windows PowerShell:

```powershell
.\mvnw.cmd exec:java "-Dexec.mainClass=server.application.ServerLauncher" "-Ddb.user=root" "-Ddb.password=your_password"
```

### Chạy Client JavaFX

Linux/macOS:

```bash
./mvnw javafx:run
```

Windows PowerShell/CMD:

```powershell
.\mvnw.cmd javafx:run
```

## 5. Hướng dẫn chạy Server/Client theo thứ tự

1. Khởi động MySQL Server.
2. Import database mẫu bằng `setupuser.sql`.
3. Mở terminal thứ nhất và chạy Server:

   ```bash
   ./mvnw exec:java -Dexec.mainClass=server.application.ServerLauncher
   ```

4. Đợi đến khi terminal Server hiển thị thông báo đang lắng nghe ở cổng `2026`.
5. Mở terminal thứ hai và chạy Client:

   ```bash
   ./mvnw javafx:run
   ```

6. Đăng nhập bằng một trong các tài khoản mẫu, hoặc đăng ký tài khoản mới trên màn hình Login.
7. Có thể mở nhiều Client cùng lúc để kiểm tra cập nhật giá đấu thầu realtime.
8. Khi kết thúc demo, đóng cửa sổ Client và nhấn `Ctrl + C` trong terminal Server.

Nếu chạy trên Windows, thay `./mvnw` bằng `.\mvnw.cmd` như các lệnh ở mục 4.

## 6. Danh sách chức năng đã hoàn thành

- Đăng ký tài khoản theo role Seller/Bidder/Admin.
- Đăng nhập, đăng xuất, quản lý trạng thái user và tự động làm mới access token bằng refresh token.
- Mã hóa mật khẩu bằng PBKDF2WithHmacSHA256.
- Quản lý phiên đấu giá theo trạng thái `OPEN`, `RUNNING`, `FINISHED`, `PAID`, `CANCELED`.
- Tự động cập nhật trạng thái phiên đấu giá theo thời gian trên Server.
- Seller tạo phiên đấu giá mới với các loại sản phẩm: điện tử, phương tiện, tác phẩm nghệ thuật.
- Seller sửa phiên đấu giá còn `OPEN`, kết thúc phiên đấu giá của mình và theo dõi danh sách phiên.
- Upload, hiển thị và xóa ảnh sản phẩm thông qua Cloudinary.
- Bidder xem danh sách phiên đấu giá, xem chi tiết phiên và lịch sử bid.
- Bidder đặt giá, có validate giá hợp lệ, trạng thái phiên và số dư ví.
- Hỗ trợ auto-bid với mức giá tối đa và bước tăng.
- Có cơ chế chống đặt giá đồng thời bằng lock theo bidder và auction.
- Có cơ chế gia hạn phiên khi có bid sát giờ kết thúc.
- Cập nhật realtime cho các Client khi có bid mới, tạo/sửa/hủy/kết thúc phiên đấu giá.
- Nạp tiền vào ví cho Bidder/Seller.
- Tự động trừ tiền người thắng và cộng tiền cho Seller khi phiên kết thúc hợp lệ.
- Admin xem danh sách user, khóa/mở khóa user và hủy phiên đấu giá.
- Lưu trữ dữ liệu bằng MySQL thông qua các DAO.
- Có unit test cho nhiều lớp client, server, service, manager, repository, model và utility.
## 7. Link video demo
- https://drive.google.com/file/d/1YZ7F7l8PtQcf4hvSrbrnE3q7qov_Gduw/view
