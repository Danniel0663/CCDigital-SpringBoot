-- ==============================================================
-- CCDigital - Control de acceso de usuario final (Admin + Login)
-- Agrega estado de acceso (ENABLED/SUSPENDED/DISABLED) y
-- trazabilidad de sincronización con Indy por cuenta.
-- ==============================================================

SET @db_name := DATABASE();

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='users' AND COLUMN_NAME='access_state'),
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN access_state ENUM(''ENABLED'',''SUSPENDED'',''DISABLED'') NOT NULL DEFAULT ''ENABLED'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='users' AND COLUMN_NAME='access_state_reason'),
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN access_state_reason VARCHAR(500) NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='users' AND COLUMN_NAME='access_state_updated_at'),
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN access_state_updated_at DATETIME NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='users' AND COLUMN_NAME='indy_access_synced'),
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN indy_access_synced TINYINT(1) NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='users' AND COLUMN_NAME='indy_access_sync_at'),
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN indy_access_sync_at DATETIME NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db_name AND TABLE_NAME='users' AND COLUMN_NAME='indy_access_sync_error'),
  'SELECT 1',
  'ALTER TABLE users ADD COLUMN indy_access_sync_error VARCHAR(1200) NULL'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE users
SET access_state = 'ENABLED'
WHERE access_state IS NULL;
