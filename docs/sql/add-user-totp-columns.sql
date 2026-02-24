-- CCDigital: columnas para MFA TOTP en tabla users
-- Ejecutar una sola vez sobre la base de datos del proyecto.

ALTER TABLE users
  ADD COLUMN totp_secret_base32 VARCHAR(128) NULL,
  ADD COLUMN totp_enabled TINYINT(1) NULL DEFAULT 0,
  ADD COLUMN totp_confirmed_at DATETIME NULL,
  ADD COLUMN totp_last_time_step BIGINT NULL;

UPDATE users
SET totp_enabled = COALESCE(totp_enabled, 0)
WHERE totp_enabled IS NULL;
