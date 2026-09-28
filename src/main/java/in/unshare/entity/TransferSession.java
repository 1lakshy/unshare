package in.unshare.entity;

import in.unshare.exception.InvalidShareStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to move a declared batch of files for a {@link ShareSession}. A share session may
 * have more than one transfer session over its lifetime (e.g. a retry after a failed attempt).
 */
@Entity
@Table(name = "transfer_session")
public class TransferSession {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "share_session_id", nullable = false, updatable = false)
    private ShareSession shareSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransferStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected TransferSession() {
        // JPA
    }

    public TransferSession(ShareSession shareSession, Instant now) {
        this.shareSession = shareSession;
        this.status = TransferStatus.PENDING;
        this.lastActivityAt = now;
    }

    public void transitionTo(TransferStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidShareStateException(
                    "Cannot transition transfer session from " + status + " to " + target);
        }
        this.status = target;
        this.lastActivityAt = now;
        if (target == TransferStatus.IN_PROGRESS) {
            this.startedAt = now;
        } else if (target.isTerminal()) {
            this.completedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public ShareSession getShareSession() {
        return shareSession;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public long getVersion() {
        return version;
    }
}
