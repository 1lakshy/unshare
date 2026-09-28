package in.unshare.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateShareRequest(
        @NotEmpty @Valid List<FileMetadataRequest> files,
        @Min(1) @Max(1440) Integer ttlMinutes,
        @Min(1) Integer maxReceivers
) {
}
