USE campus_platform;
SET @present = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lost_found' AND column_name='audit_status');
SET @statement = IF(@present=0,'ALTER TABLE lost_found ADD COLUMN audit_status ENUM(''PENDING'',''APPROVED'',''REJECTED'') NOT NULL DEFAULT ''PENDING''','SELECT 1');
PREPARE migration_statement FROM @statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
