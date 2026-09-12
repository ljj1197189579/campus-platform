USE campus_platform;
CREATE TABLE IF NOT EXISTS media_asset (
 id VARCHAR(36) PRIMARY KEY, owner_id BIGINT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(owner_id) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS goods_image (
 goods_id BIGINT NOT NULL, media_id VARCHAR(36) NOT NULL, sort_no INT NOT NULL,
 PRIMARY KEY(goods_id,media_id), FOREIGN KEY(goods_id) REFERENCES goods(id), FOREIGN KEY(media_id) REFERENCES media_asset(id)
);
CREATE TABLE IF NOT EXISTS service_fee (
 order_id BIGINT PRIMARY KEY, amount DECIMAL(10,2) NOT NULL, fee DECIMAL(10,2) NOT NULL,
 status ENUM('UNPAID','PENDING','CONFIRMED','SETTLED','REJECTED','REFUND_PENDING','REFUNDED','CANCELLED') NOT NULL DEFAULT 'UNPAID',
 payer_reference VARCHAR(100), proof MEDIUMBLOB, verified_reference VARCHAR(100) UNIQUE,
 verified_by BIGINT, verified_at DATETIME, note VARCHAR(255), refund_reference VARCHAR(100) UNIQUE,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(amount>=0 AND fee>=0 AND fee<=amount), FOREIGN KEY(order_id) REFERENCES order_intention(id)
);
CREATE TABLE IF NOT EXISTS payment_setting (
 id INT PRIMARY KEY, qr_url VARCHAR(100), payee_name VARCHAR(80), instructions VARCHAR(255)
);
INSERT IGNORE INTO payment_setting(id) VALUES(1);
