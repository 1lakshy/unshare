-- Core persistence schema for the unshare signaling backend.
-- No file contents, local/device paths, or unnecessary PII are ever stored here.

CREATE TABLE share_session (
    id               UUID PRIMARY KEY,
    token            VARCHAR(64) NOT NULL,
    status           VARCHAR(30) NOT NULL,
    sender_state     VARCHAR(20) NOT NULL DEFAULT 'NOT_CONNECTED',
    receiver_state   VARCHAR(20) NOT NULL DEFAULT 'NOT_CONNECTED',
    max_receivers    INTEGER NOT NULL DEFAULT 1,
    creator_label    VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    last_activity_at TIMESTAMPTZ NOT NULL,
    version          BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_share_session_token UNIQUE (token),
    CONSTRAINT ck_share_session_status CHECK (status IN (
        'CREATED', 'WAITING_FOR_RECEIVER', 'CONNECTING', 'CONNECTED',
        'TRANSFERRING', 'COMPLETED', 'EXPIRED', 'CANCELLED', 'FAILED'
    )),
    CONSTRAINT ck_share_session_sender_state CHECK (sender_state IN (
        'NOT_CONNECTED', 'CONNECTING', 'CONNECTED', 'DISCONNECTED'
    )),
    CONSTRAINT ck_share_session_receiver_state CHECK (receiver_state IN (
        'NOT_CONNECTED', 'CONNECTING', 'CONNECTED', 'DISCONNECTED'
    )),
    CONSTRAINT ck_share_session_max_receivers CHECK (max_receivers > 0),
    CONSTRAINT ck_share_session_expiry_after_creation CHECK (expires_at > created_at)
);

-- uq_share_session_token already gives us a unique btree index for token lookups.
CREATE INDEX idx_share_session_status ON share_session (status);
CREATE INDEX idx_share_session_expires_at ON share_session (expires_at);
CREATE INDEX idx_share_session_last_activity_at ON share_session (last_activity_at);

CREATE TABLE transfer_session (
    id                UUID PRIMARY KEY,
    share_session_id  UUID NOT NULL REFERENCES share_session (id) ON DELETE CASCADE,
    status            VARCHAR(20) NOT NULL,
    started_at        TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    last_activity_at  TIMESTAMPTZ NOT NULL,
    version           BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_transfer_session_status CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'
    )),
    CONSTRAINT ck_transfer_session_started_before_completed
        CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX idx_transfer_session_share_session_id ON transfer_session (share_session_id);
CREATE INDEX idx_transfer_session_status ON transfer_session (status);
CREATE INDEX idx_transfer_session_last_activity_at ON transfer_session (last_activity_at);

CREATE TABLE shared_file (
    id                   UUID PRIMARY KEY,
    transfer_session_id  UUID NOT NULL REFERENCES transfer_session (id) ON DELETE CASCADE,
    file_identifier      UUID NOT NULL,
    original_filename    VARCHAR(255) NOT NULL,
    mime_type            VARCHAR(127),
    file_size            BIGINT NOT NULL,
    sequence_order       INTEGER NOT NULL,

    CONSTRAINT ck_shared_file_size_positive CHECK (file_size > 0),
    CONSTRAINT ck_shared_file_sequence_non_negative CHECK (sequence_order >= 0),
    CONSTRAINT uq_shared_file_transfer_sequence UNIQUE (transfer_session_id, sequence_order)
);

CREATE INDEX idx_shared_file_transfer_session_id ON shared_file (transfer_session_id);
