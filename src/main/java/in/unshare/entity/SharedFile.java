package in.unshare.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Metadata for one file offered within a {@link TransferSession}. No file content or local/device
 * path is ever stored here - only what the receiver needs to know what is being sent.
 */
@Entity
@Table(name = "shared_file")
public class SharedFile {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_session_id", nullable = false, updatable = false)
    private TransferSession transferSession;

    /** Client-supplied correlation id used to match this record to the WebRTC data-channel transfer. */
    @Column(name = "file_identifier", nullable = false, updatable = false)
    private UUID fileIdentifier;

    @Column(name = "original_filename", nullable = false, updatable = false)
    private String originalFilename;

    @Column(name = "mime_type", updatable = false)
    private String mimeType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    @Column(name = "sequence_order", nullable = false, updatable = false)
    private int sequenceOrder;

    protected SharedFile() {
        // JPA
    }

    public SharedFile(TransferSession transferSession, UUID fileIdentifier, String originalFilename,
                       long fileSize, String mimeType, int sequenceOrder) {
        this.transferSession = transferSession;
        this.fileIdentifier = fileIdentifier;
        this.originalFilename = originalFilename;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.sequenceOrder = sequenceOrder;
    }

    public UUID getId() {
        return id;
    }

    public TransferSession getTransferSession() {
        return transferSession;
    }

    public UUID getFileIdentifier() {
        return fileIdentifier;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getSequenceOrder() {
        return sequenceOrder;
    }
}
