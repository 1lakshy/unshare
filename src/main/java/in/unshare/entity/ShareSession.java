package in.unshare.entity;

import in.unshare.exception.InvalidShareStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * A temporary, token-addressable file-sharing session. Holds no file content or paths;
 * only the coordination state needed to bring a sender and receiver together over WebRTC.
 */
@Entity
@Table(name = "share_session")
public class ShareSession {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "token", nullable = false, unique = true, updatable = false)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_state", nullable = false)
    private ConnectionState senderState;

    @Enumerated(EnumType.STRING)
    @Column(name = "receiver_state", nullable = false)
    private ConnectionState receiverState;

    @Column(name = "max_receivers", nullable = false)
    private int maxReceivers;

    @Column(name = "creator_label")
    private String creatorLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ShareSession() {
        // JPA
    }

    private ShareSession(String token, int maxReceivers, String creatorLabel, Instant now, Instant expiresAt) {
        this.token = token;
        this.status = SessionStatus.CREATED;
        this.senderState = ConnectionState.NOT_CONNECTED;
        this.receiverState = ConnectionState.NOT_CONNECTED;
        this.maxReceivers = maxReceivers;
        this.creatorLabel = creatorLabel;
        this.createdAt = now;
        this.expiresAt = expiresAt;
        this.lastActivityAt = now;
    }

    public static ShareSession create(String token, int maxReceivers, String creatorLabel, Instant now, Instant expiresAt) {
        if (!expiresAt.isAfter(now)) {
            throw new InvalidShareStateException("expiresAt must be after the creation instant");
        }
        return new ShareSession(token, maxReceivers, creatorLabel, now, expiresAt);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public void transitionTo(SessionStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidShareStateException(
                    "Cannot transition share session from " + status + " to " + target);
        }
        this.status = target;
        this.lastActivityAt = now;
    }

    public void updateSenderState(ConnectionState state, Instant now) {
        this.senderState = state;
        this.lastActivityAt = now;
    }

    public void updateReceiverState(ConnectionState state, Instant now) {
        this.receiverState = state;
        this.lastActivityAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public ConnectionState getSenderState() {
        return senderState;
    }

    public ConnectionState getReceiverState() {
        return receiverState;
    }

    public int getMaxReceivers() {
        return maxReceivers;
    }

    public String getCreatorLabel() {
        return creatorLabel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public long getVersion() {
        return version;
    }
}
