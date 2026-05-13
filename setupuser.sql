-- 1. Tạo Database
CREATE DATABASE IF NOT EXISTS auction_db;
USE auction_db;

-- 2. Tạo bảng users
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id INT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) DEFAULT 'user',
    status VARCHAR(50) DEFAULT 'LOGOUT', -- Thêm cột này vì tôi thấy trong hình bạn có dùng
    email VARCHAR(100) DEFAULT NULL,
    wallet_balance DECIMAL(15, 2) DEFAULT 0.00, 
    PRIMARY KEY (id),
    UNIQUE INDEX uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- 3. Chèn dữ liệu mẫu (Phải có username và password vì NOT NULL)
-- INSERT INTO users (username, password, role, email, wallet_balance) 
-- VALUES ('admin', 'hashed_password_here', 'admin', 'admin@example.com', 1000.00);