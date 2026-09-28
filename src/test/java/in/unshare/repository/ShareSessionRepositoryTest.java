package in.unshare.repository;

import in.unshare.entity.SessionStatus;
import in.unshare.entity.ShareSession;
import in.unshare.support.AbstractIntegrationTest;
import in.unshare.support.ShareSessionFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ShareSessionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ShareSessionRepository shareSessionRepository;

    @Test
    void savesAndLoadsSessionByToken() {
        Instant now = Instant.now();
        ShareSession session = ShareSessionFixtures.newSession(now, now.plus(Duration.ofMinutes(30)), 2);

        ShareSession saved = shareSessionRepository.saveAndFlush(session);

        Optional<ShareSession> loaded = shareSessionRepository.findByToken(saved.getToken());

        assertThat(loaded).isPresent();
        assertThat(loaded.get().getId()).isEqualTo(saved.getId());
        assertThat(loaded.get().getStatus()).isEqualTo(SessionStatus.CREATED);
        assertThat(loaded.get().getMaxReceivers()).isEqualTo(2);
        assertThat(loaded.get().getLastActivityAt()).isEqualTo(now);
    }

    @Test
    void enforcesUniqueToken() {
        Instant now = Instant.now();
        String token = "dup_" + UUID.randomUUID();
        shareSessionRepository.saveAndFlush(ShareSession.create(token, 1, null, now, now.plusSeconds(60)));

        assertThat(shareSessionRepository.existsByToken(token)).isTrue();
        assertThat(shareSessionRepository.existsByToken("does-not-exist")).isFalse();

        assertThatThrownBy(() ->
                shareSessionRepository.saveAndFlush(ShareSession.create(token, 1, null, now, now.plusSeconds(60))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void expireStaleSessionsFlipsOnlyNonTerminalRowsPastDeadline() {
        Instant now = Instant.now();
        ShareSession stale = shareSessionRepository.saveAndFlush(
                ShareSessionFixtures.newSession(now.minusSeconds(120), now.minusSeconds(60)));
        ShareSession fresh = shareSessionRepository.saveAndFlush(
                ShareSessionFixtures.newSession(now, now.plusSeconds(600)));
        ShareSession alreadyCancelled = ShareSessionFixtures.newSession(now.minusSeconds(120), now.minusSeconds(60));
        alreadyCancelled.transitionTo(SessionStatus.CANCELLED, now);
        shareSessionRepository.saveAndFlush(alreadyCancelled);

        int updated = shareSessionRepository.expireStaleSessions(now, SessionStatus.nonTerminalStatuses());

        assertThat(updated).isEqualTo(1);
        assertThat(shareSessionRepository.findById(stale.getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.EXPIRED);
        assertThat(shareSessionRepository.findById(fresh.getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.CREATED);
        assertThat(shareSessionRepository.findById(alreadyCancelled.getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.CANCELLED);
    }
}
