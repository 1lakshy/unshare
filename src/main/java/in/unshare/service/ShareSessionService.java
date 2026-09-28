package in.unshare.service;

import in.unshare.config.AppProperties;
import in.unshare.dto.CreateShareRequest;
import in.unshare.dto.FileMetadataRequest;
import in.unshare.dto.ShareSessionResponse;
import in.unshare.entity.ConnectionState;
import in.unshare.entity.SessionStatus;
import in.unshare.entity.SharedFile;
import in.unshare.entity.ShareSession;
import in.unshare.entity.TransferSession;
import in.unshare.exception.InvalidShareStateException;
import in.unshare.exception.ShareSessionExpiredException;
import in.unshare.exception.ShareSessionNotFoundException;
import in.unshare.mapper.ShareSessionMapper;
import in.unshare.repository.SharedFileRepository;
import in.unshare.repository.ShareSessionRepository;
import in.unshare.repository.TransferSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ShareSessionService {

    private static final Logger log = LoggerFactory.getLogger(ShareSessionService.class);

    private final ShareSessionRepository shareSessionRepository;
    private final TransferSessionRepository transferSessionRepository;
    private final SharedFileRepository sharedFileRepository;
    private final TokenGeneratorService tokenGeneratorService;
    private final ShareSessionMapper mapper;
    private final AppProperties appProperties;
    private final Clock clock;

    public ShareSessionService(ShareSessionRepository shareSessionRepository,
                                TransferSessionRepository transferSessionRepository,
                                SharedFileRepository sharedFileRepository,
                                TokenGeneratorService tokenGeneratorService,
                                ShareSessionMapper mapper,
                                AppProperties appProperties,
                                Clock clock) {
        this.shareSessionRepository = shareSessionRepository;
        this.transferSessionRepository = transferSessionRepository;
        this.sharedFileRepository = sharedFileRepository;
        this.tokenGeneratorService = tokenGeneratorService;
        this.mapper = mapper;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public ShareSessionResponse createShare(CreateShareRequest request) {
        validateFiles(request.files());

        int ttlMinutes = request.ttlMinutes() != null
                ? Math.min(request.ttlMinutes(), appProperties.share().maxTtlMinutes())
                : appProperties.share().defaultTtlMinutes();

        int maxReceivers = request.maxReceivers() != null
                ? Math.min(request.maxReceivers(), appProperties.share().maxAllowedReceivers())
                : appProperties.share().defaultMaxReceivers();

        Instant now = clock.instant();
        String token = tokenGeneratorService.generateUniqueToken();

        ShareSession session = ShareSession.create(token, maxReceivers, null, now, now.plusSeconds(ttlMinutes * 60L));
        session.transitionTo(SessionStatus.WAITING_FOR_RECEIVER, now);
        ShareSession savedSession = shareSessionRepository.save(session);

        TransferSession transfer = transferSessionRepository.save(new TransferSession(savedSession, now));

        List<FileMetadataRequest> requestedFiles = request.files();
        List<SharedFile> files = new ArrayList<>(requestedFiles.size());
        for (int i = 0; i < requestedFiles.size(); i++) {
            FileMetadataRequest file = requestedFiles.get(i);
            UUID fileIdentifier = file.fileIdentifier() != null ? file.fileIdentifier() : UUID.randomUUID();
            files.add(new SharedFile(transfer, fileIdentifier, file.fileName(), file.fileSize(), file.mimeType(), i));
        }
        List<SharedFile> savedFiles = sharedFileRepository.saveAll(files);

        log.info("Created share session token={} files={} expiresAt={}", savedSession.getToken(), savedFiles.size(), savedSession.getExpiresAt());
        return mapper.toResponse(savedSession, savedFiles);
    }

    @Transactional(readOnly = true)
    public ShareSessionResponse getByToken(String token) {
        ShareSession session = loadFreshSession(token);
        return mapper.toResponse(session, filesFor(session));
    }

    public ShareSessionResponse join(String token) {
        ShareSession session = loadFreshSession(token);

        if (session.getStatus() != SessionStatus.WAITING_FOR_RECEIVER) {
            throw new InvalidShareStateException(
                    "Share session cannot be joined in status " + session.getStatus());
        }

        Instant now = clock.instant();
        session.updateReceiverState(ConnectionState.CONNECTING, now);
        session.transitionTo(SessionStatus.CONNECTING, now);
        session.transitionTo(SessionStatus.CONNECTED, now);
        session.updateReceiverState(ConnectionState.CONNECTED, now);

        log.info("Receiver joined share session token={}", token);
        return mapper.toResponse(session, filesFor(session));
    }

    public ShareSessionResponse cancel(String token) {
        ShareSession session = loadFreshSession(token);

        if (session.getStatus().isTerminal()) {
            throw new InvalidShareStateException(
                    "Share session is already in a terminal status " + session.getStatus());
        }

        session.transitionTo(SessionStatus.CANCELLED, clock.instant());
        log.info("Cancelled share session token={}", token);
        return mapper.toResponse(session, filesFor(session));
    }

    private ShareSession loadFreshSession(String token) {
        ShareSession session = shareSessionRepository.findByToken(token)
                .orElseThrow(() -> new ShareSessionNotFoundException(token));

        if (!session.getStatus().isTerminal() && session.isExpired(clock.instant())) {
            session.transitionTo(SessionStatus.EXPIRED, clock.instant());
        }

        if (session.getStatus() == SessionStatus.EXPIRED) {
            throw new ShareSessionExpiredException(token);
        }

        return session;
    }

    private List<SharedFile> filesFor(ShareSession session) {
        return transferSessionRepository.findByShareSessionIdOrderByLastActivityAtDesc(session.getId()).stream()
                .findFirst()
                .map(transfer -> sharedFileRepository.findByTransferSessionIdOrderBySequenceOrderAsc(transfer.getId()))
                .orElseGet(List::of);
    }

    private void validateFiles(List<FileMetadataRequest> files) {
        if (files.size() > appProperties.share().maxFiles()) {
            throw new InvalidShareStateException(
                    "A share session may contain at most " + appProperties.share().maxFiles() + " files");
        }

        long totalSize = files.stream().mapToLong(FileMetadataRequest::fileSize).sum();
        if (totalSize > appProperties.share().maxTotalSizeBytes()) {
            throw new InvalidShareStateException(
                    "Total file size exceeds the maximum allowed of " + appProperties.share().maxTotalSizeBytes() + " bytes");
        }
    }
}
