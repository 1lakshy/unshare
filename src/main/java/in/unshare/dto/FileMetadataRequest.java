package in.unshare.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record FileMetadataRequest(
        UUID fileIdentifier,
        @NotBlank @Size(max = 255) String fileName,
        @Positive long fileSize,
        @Size(max = 127) String mimeType
) {
}
