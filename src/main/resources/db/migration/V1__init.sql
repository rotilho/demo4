CREATE TABLE test.account
(
    public_key     VARBINARY(32) PRIMARY KEY,
    balance        BIGINT UNSIGNED NOT NULL,
    representative VARBINARY(32) NOT NULL,
    persisted_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);