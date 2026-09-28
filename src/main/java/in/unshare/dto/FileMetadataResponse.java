package in.unshare.dto;

import java.util.UUID;

public record FileMetadataResponse(
        UUID fileIdentifier,
        String fileName,
        long fileSize,
        String mimeType
) {
}
