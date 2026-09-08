CREATE TABLE users
(
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(60) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'BLOCKED')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE login_attempts
(
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username     VARCHAR(50) NOT NULL,
    ip_address   VARCHAR(45) NOT NULL,
    success      BOOLEAN     NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- "최근 N분간 특정 계정의 실패 횟수" 조회용
CREATE INDEX idx_login_attempts_username_time
    ON login_attempts (username, attempted_at DESC);

CREATE TABLE fraud_detections
(
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50) NOT NULL,
    ip_address    VARCHAR(45) NOT NULL,
    rule_type     VARCHAR(20) NOT NULL
        CONSTRAINT ck_fraud_detections_rule_type CHECK (rule_type IN ('BRUTE_FORCE')),
    trigger_count INT         NOT NULL,
    detected_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    action_taken  VARCHAR(20) NOT NULL
        CONSTRAINT ck_fraud_detections_action_taken CHECK (action_taken IN ('BLOCKED', 'ALERTED'))
);

CREATE INDEX idx_fraud_detections_username_time
    ON fraud_detections (username, detected_at DESC);
