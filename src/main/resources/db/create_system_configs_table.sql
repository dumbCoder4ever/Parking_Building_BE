-- System configuration (GĐ 12) — Railway Query Editor compatible

CREATE TABLE IF NOT EXISTS system_configs (
    config_id VARCHAR(36) PRIMARY KEY,
    config_key VARCHAR(80) NOT NULL,
    config_value VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_system_configs_key (config_key)
);

INSERT INTO system_configs (config_id, config_key, config_value, description, created_at, updated_at)
SELECT UUID(), 'GRACE_PERIOD_MINUTES', '15',
       'Grace period (minutes) after reservationStart before auto-expire', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'GRACE_PERIOD_MINUTES');

INSERT INTO system_configs (config_id, config_key, config_value, description, created_at, updated_at)
SELECT UUID(), 'PAYMENT_REMINDER_MINUTES', '15',
       'Minutes after check-in before unpaid payment reminder', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'PAYMENT_REMINDER_MINUTES');

INSERT INTO system_configs (config_id, config_key, config_value, description, created_at, updated_at)
SELECT UUID(), 'MAX_PARKING_HOURS', '24',
       'Default maximum parking duration (hours) for overstay alerts', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'MAX_PARKING_HOURS');

INSERT INTO system_configs (config_id, config_key, config_value, description, created_at, updated_at)
SELECT UUID(), 'PEAK_HOUR_STDDEV_FACTOR', '1.0',
       'Peak threshold = average + stdDev * factor', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'PEAK_HOUR_STDDEV_FACTOR');

INSERT INTO system_configs (config_id, config_key, config_value, description, created_at, updated_at)
SELECT UUID(), 'OVERSTAY_NOTIFY_ENABLED', 'true',
       'Enable vehicle overstay notification job', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_configs WHERE config_key = 'OVERSTAY_NOTIFY_ENABLED');
