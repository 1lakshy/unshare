package in.unshare.dto;

import java.time.Instant;
import java.util.List;

public record ShareSessionResponse(
        String token,
        String shareUrl,
        String status,
        String senderState,
        String receiverState,
        int maxReceivers,
        Instant createdAt,
        Instant expiresAt,
        Instant lastActivityAt,
        List<FileMetadataResponse> files
) {
}
