CREATE DATABASE IF NOT EXISTS auction_db;
USE auction_db;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS bid_transactions;
DROP TABLE IF EXISTS auctions;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
    id INT NOT NULL,ne
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) DEFAULT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'LOGOUT',
    wallet_balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE items (
    id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    starting_price DECIMAL(15, 2) NOT NULL,
    seller_id VARCHAR(50) NOT NULL,
    item_type VARCHAR(20) NOT NULL,
    warranty_period INT DEFAULT NULL,
    mileage INT DEFAULT NULL,
    artist VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_items_seller_id (seller_id),
    KEY idx_items_type (item_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE auctions (
    id INT NOT NULL,
    item_id INT NOT NULL,
    seller_id VARCHAR(50) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    current_highest_bid DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    current_leader_id INT DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    PRIMARY KEY (id),
    KEY idx_auctions_item_id (item_id),
    KEY idx_auctions_seller_id (seller_id),
    KEY idx_auctions_status (status),
    KEY idx_auctions_current_leader_id (current_leader_id),
    CONSTRAINT fk_auctions_item
        FOREIGN KEY (item_id) REFERENCES items(id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_auctions_current_leader
        FOREIGN KEY (current_leader_id) REFERENCES users(id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bid_transactions (
    id INT NOT NULL AUTO_INCREMENT,
    auction_id INT NOT NULL,
    bidder_id INT NOT NULL,
    bid_amount DECIMAL(15, 2) NOT NULL,
    bid_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_bids_auction_id (auction_id),
    KEY idx_bids_bidder_id (bidder_id),
    KEY idx_bids_bid_time (bid_time),
    CONSTRAINT fk_bids_auction
        FOREIGN KEY (auction_id) REFERENCES auctions(id)
        ON UPDATE CASCADE
        ON DELETE CASCADE,
    CONSTRAINT fk_bids_bidder
        FOREIGN KEY (bidder_id) REFERENCES users(id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO users (id, username, email, password, role, status, wallet_balance) VALUES
    (100001, 'admin', 'admin@example.com', 'admin123', 'ADMIN', 'LOGOUT', 0.00),
    (200001, 'seller1', 'seller1@example.com', 'seller123', 'SELLER', 'LOGOUT', 0.00),
    (300001, 'bidder1', 'bidder1@example.com', 'bidder123', 'BIDDER', 'LOGOUT', 85000000.00),
    (300002, 'bidder2', 'bidder2@example.com', 'bidder123', 'BIDDER', 'LOGOUT', 71500000.00);

INSERT INTO items (id, name, description, starting_price, seller_id, item_type, warranty_period, mileage, artist) VALUES
    (400001, 'iPhone 15 Pro', 'Dien thoai cao cap, con bao hanh, tinh trang tot', 25000000.00, '200001', 'ELECTRONICS', 12, NULL, NULL),
    (400002, 'Toyota Camry 2020', 'Xe da qua su dung, tinh trang tot, giay to day du', 450000000.00, '200001', 'VEHICLE', NULL, 45000, NULL),
    (400003, 'Sunset Canvas', 'Tranh son dau ve hoang hon, kich thuoc 60x90cm', 12000000.00, '200001', 'ART', NULL, NULL, 'Nguyen Van A');

INSERT INTO auctions (id, item_id, seller_id, start_time, end_time, current_highest_bid, current_leader_id, status) VALUES
    (500001, 400001, '200001', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), 28500000.00, 300002, 'RUNNING'),
    (500002, 400002, '200001', DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 4 DAY), 0.00, NULL, 'OPEN'),
    (500003, 400003, '200001', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 15000000.00, 300001, 'FINISHED');

INSERT INTO bid_transactions (id, auction_id, bidder_id, bid_amount, bid_time) VALUES
    (600001, 500001, 300001, 25000000.00, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
    (600002, 500001, 300002, 27000000.00, DATE_SUB(NOW(), INTERVAL 12 HOUR)),
    (600003, 500001, 300001, 28000000.00, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
    (600004, 500001, 300002, 28500000.00, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
    (600005, 500003, 300002, 12500000.00, DATE_SUB(NOW(), INTERVAL 4 DAY)),
    (600006, 500003, 300001, 15000000.00, DATE_SUB(NOW(), INTERVAL 2 DAY));
