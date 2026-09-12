USE campus_platform;
SET @present = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='goods' AND column_name='original_price');
SET @statement = IF(@present=0,'ALTER TABLE goods ADD COLUMN original_price DECIMAL(10,2) NULL, ADD COLUMN trade_location VARCHAR(100) NOT NULL DEFAULT '''', ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0','SELECT 1');
PREPARE migration_statement FROM @statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
INSERT IGNORE INTO goods_category(name,sort_no) VALUES ('服饰鞋包',6),('美妆个护',7),('家用电器',8),('出行工具',9),('乐器文娱',10),('手作周边',11);
CREATE TABLE IF NOT EXISTS chat_conversation (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, user_low BIGINT NOT NULL, user_high BIGINT NOT NULL,
 low_read_id BIGINT NOT NULL DEFAULT 0, high_read_id BIGINT NOT NULL DEFAULT 0,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_chat_pair(user_low,user_high), CHECK(user_low<user_high),
 FOREIGN KEY(user_low) REFERENCES sys_user(id), FOREIGN KEY(user_high) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS chat_message (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, conversation_id BIGINT NOT NULL, sender_id BIGINT NOT NULL,
 content VARCHAR(2000) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY ix_chat_history(conversation_id,id), FOREIGN KEY(conversation_id) REFERENCES chat_conversation(id),
 FOREIGN KEY(sender_id) REFERENCES sys_user(id)
);
