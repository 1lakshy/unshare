package in.unshare.service;

import in.unshare.entity.SessionStatus;
import in.unshare.repository.ShareSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
public class SessionExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionExpiryScheduler.class);

    private final ShareSessionRepository shareSessionRepository;
    private final Clock clock;

    public SessionExpiryScheduler(ShareSessionRepository shareSessionRepository, Clock clock) {
        this.shareSessionRepository = shareSessionRepository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.share.expiry-sweep-interval-ms:60000}")
    @Transactional
    public void sweepExpiredSessions() {
        int updated = shareSessionRepository.expireStaleSessions(clock.instant(), SessionStatus.nonTerminalStatuses());
        if (updated > 0) {
            log.info("Expired {} stale share session(s)", updated);
        }
    }
}
