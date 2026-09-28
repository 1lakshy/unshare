package in.unshare.mapper;

import in.unshare.config.AppProperties;
import in.unshare.dto.FileMetadataResponse;
import in.unshare.dto.ShareSessionResponse;
import in.unshare.entity.SharedFile;
import in.unshare.entity.ShareSession;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ShareSessionMapper {

    private final AppProperties appProperties;

    public ShareSessionMapper(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public ShareSessionResponse toResponse(ShareSession session, List<SharedFile> files) {
        List<FileMetadataResponse> fileResponses = files.stream()
                .map(this::toResponse)
                .toList();

        return new ShareSessionResponse(
                session.getToken(),
                buildShareUrl(session.getToken()),
                session.getStatus().name(),
                session.getSenderState().name(),
                session.getReceiverState().name(),
                session.getMaxReceivers(),
                session.getCreatedAt(),
                session.getExpiresAt(),
                session.getLastActivityAt(),
                fileResponses
        );
    }

    public FileMetadataResponse toResponse(SharedFile file) {
        return new FileMetadataResponse(file.getFileIdentifier(), file.getOriginalFilename(), file.getFileSize(), file.getMimeType());
    }

    private String buildShareUrl(String token) {
        String base = appProperties.baseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/s/" + token;
    }
}
