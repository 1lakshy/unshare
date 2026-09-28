package in.unshare.service;

import in.unshare.config.AppProperties;
import in.unshare.dto.CreateShareRequest;
import in.unshare.dto.FileMetadataRequest;
import in.unshare.dto.ShareSessionResponse;
import in.unshare.entity.SessionStatus;
import in.unshare.entity.SharedFile;
import in.unshare.entity.ShareSession;
import in.unshare.exception.InvalidShareStateException;
import in.unshare.exception.ShareSessionExpiredException;
import in.unshare.exception.ShareSessionNotFoundException;
import in.unshare.mapper.ShareSessionMapper;
import in.unshare.repository.SharedFileRepository;
import in.unshare.repository.ShareSessionRepository;
import in.unshare.repository.TransferSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private ShareSessionRepository shareSessionRepository;

    @Mock
    private TransferSessionRepository transferSessionRepository;

    @Mock
    private SharedFileRepository sharedFileRepository;

    @Mock
    private TokenGeneratorService tokenGeneratorService;

    @Mock
    private ShareSessionMapper mapper;

    private AppProperties appProperties;
    private Clock clock;
    private ShareSessionService service;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties(
                "http://localhost:5173",
                new AppProperties.Share(30, 180, 2, 10_000L, 1, 5),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.RateLimit(20, 60)
        );
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ShareSessionService(shareSessionRepository, transferSessionRepository, sharedFileRepository,
                tokenGeneratorService, mapper, appProperties, clock);
    }

    @Test
    void createShare_savesSessionAndTransferWithFiles() {
        when(tokenGeneratorService.generateUniqueToken()).thenReturn("tok-123");
        when(shareSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transferSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sharedFileRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(ShareSession.class), any())).thenReturn(
                new ShareSessionResponse("tok-123", "http://x/s/tok-123", "WAITING_FOR_RECEIVER",
                        "NOT_CONNECTED", "NOT_CONNECTED", 1, NOW, NOW.plusSeconds(1800), NOW, List.of()));

        CreateShareRequest request = new CreateShareRequest(
                List.of(new FileMetadataRequest(null, "a.txt", 100L, "text/plain")), null, null);

        ShareSessionResponse response = service.createShare(request);

        ArgumentCaptor<ShareSession> sessionCaptor = ArgumentCaptor.forClass(ShareSession.class);
        org.mockito.Mockito.verify(shareSessionRepository).save(sessionCaptor.capture());
        ShareSession saved = sessionCaptor.getValue();

        assertThat(saved.getToken()).isEqualTo("tok-123");
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.WAITING_FOR_RECEIVER);
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusSeconds(30 * 60L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SharedFile>> filesCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(sharedFileRepository).saveAll(filesCaptor.capture());
        assertThat(filesCaptor.getValue()).hasSize(1);

        assertThat(response.token()).isEqualTo("tok-123");
    }

    @Test
    void createShare_rejectsTooManyFiles() {
        CreateShareRequest request = new CreateShareRequest(
                List.of(
                        new FileMetadataRequest(null, "a.txt", 10L, "text/plain"),
                        new FileMetadataRequest(null, "b.txt", 10L, "text/plain"),
                        new FileMetadataRequest(null, "c.txt", 10L, "text/plain")
                ), null, null);

        assertThatThrownBy(() -> service.createShare(request))
                .isInstanceOf(InvalidShareStateException.class);
    }

    @Test
    void createShare_rejectsTotalSizeAboveLimit() {
        CreateShareRequest request = new CreateShareRequest(
                List.of(new FileMetadataRequest(null, "big.bin", 20_000L, "application/octet-stream")), null, null);

        assertThatThrownBy(() -> service.createShare(request))
                .isInstanceOf(InvalidShareStateException.class);
    }

    @Test
    void getByToken_throwsNotFoundWhenMissing() {
        when(shareSessionRepository.findByToken("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByToken("missing"))
                .isInstanceOf(ShareSessionNotFoundException.class);
    }

    @Test
    void getByToken_marksExpiredSessionAndThrows() {
        ShareSession expiredSession = ShareSession.create("tok", 1, null, NOW.minusSeconds(3600), NOW.minusSeconds(1));
        when(shareSessionRepository.findByToken("tok")).thenReturn(Optional.of(expiredSession));

        assertThatThrownBy(() -> service.getByToken("tok"))
                .isInstanceOf(ShareSessionExpiredException.class);
    }

    @Test
    void join_transitionsWaitingSessionToConnected() {
        ShareSession session = ShareSession.create("tok", 1, null, NOW, NOW.plusSeconds(1800));
        session.transitionTo(SessionStatus.WAITING_FOR_RECEIVER, NOW);
        when(shareSessionRepository.findByToken("tok")).thenReturn(Optional.of(session));
        when(transferSessionRepository.findByShareSessionIdOrderByLastActivityAtDesc(any()))
                .thenReturn(List.of());
        when(mapper.toResponse(any(ShareSession.class), any())).thenReturn(
                new ShareSessionResponse("tok", "http://x/s/tok", "CONNECTED", "NOT_CONNECTED", "CONNECTED",
                        1, NOW, NOW.plusSeconds(1800), NOW, List.of()));

        ShareSessionResponse response = service.join("tok");

        assertThat(session.getStatus()).isEqualTo(SessionStatus.CONNECTED);
        assertThat(response.status()).isEqualTo("CONNECTED");
    }

    @Test
    void join_rejectsSessionNotWaitingForReceiver() {
        ShareSession session = ShareSession.create("tok", 1, null, NOW, NOW.plusSeconds(1800));
        when(shareSessionRepository.findByToken("tok")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.join("tok"))
                .isInstanceOf(InvalidShareStateException.class);
    }

    @Test
    void cancel_rejectsAlreadyTerminalSession() {
        ShareSession session = ShareSession.create("tok", 1, null, NOW, NOW.plusSeconds(1800));
        session.transitionTo(SessionStatus.CANCELLED, NOW);
        when(shareSessionRepository.findByToken("tok")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.cancel("tok"))
                .isInstanceOf(InvalidShareStateException.class);
    }
}
