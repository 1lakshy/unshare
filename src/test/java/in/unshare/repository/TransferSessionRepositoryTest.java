package in.unshare.repository;

import in.unshare.entity.ShareSession;
import in.unshare.entity.TransferSession;
import in.unshare.entity.TransferStatus;
import in.unshare.support.AbstractIntegrationTest;
import in.unshare.support.ShareSessionFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TransferSessionRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ShareSessionRepository shareSessionRepository;

    @Autowired
    private TransferSessionRepository transferSessionRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsAndLinksToItsShareSession() {
        Instant now = Instant.now();
        ShareSession session = shareSessionRepository.saveAndFlush(ShareSessionFixtures.newSession(now));

        TransferSession transfer = transferSessionRepository.saveAndFlush(new TransferSession(session, now));

        List<TransferSession> found = transferSessionRepository.findByShareSessionIdOrderByLastActivityAtDesc(session.getId());

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getId()).isEqualTo(transfer.getId());
        assertThat(found.getFirst().getStatus()).isEqualTo(TransferStatus.PENDING);
        assertThat(found.getFirst().getShareSession().getId()).isEqualTo(session.getId());
    }

    @Test
    void deletingShareSessionCascadesToItsTransferSessions() {
        Instant now = Instant.now();
        ShareSession session = shareSessionRepository.saveAndFlush(ShareSessionFixtures.newSession(now));
        TransferSession transfer = transferSessionRepository.saveAndFlush(new TransferSession(session, now));

        shareSessionRepository.delete(session);
        shareSessionRepository.flush();
        entityManager.clear();

        assertThat(transferSessionRepository.findById(transfer.getId())).isEmpty();
    }

    @Test
    void transitionToInProgressStampsStartedAt() {
        Instant now = Instant.now();
        ShareSession session = shareSessionRepository.saveAndFlush(ShareSessionFixtures.newSession(now));
        TransferSession transfer = new TransferSession(session, now);

        transfer.transitionTo(TransferStatus.IN_PROGRESS, now.plusSeconds(5));
        TransferSession saved = transferSessionRepository.saveAndFlush(transfer);

        assertThat(saved.getStartedAt()).isEqualTo(now.plusSeconds(5));
        assertThat(saved.getStatus()).isEqualTo(TransferStatus.IN_PROGRESS);
    }
}
