package in.unshare.repository;

import in.unshare.entity.SharedFile;
import in.unshare.entity.ShareSession;
import in.unshare.entity.TransferSession;
import in.unshare.support.AbstractIntegrationTest;
import in.unshare.support.ShareSessionFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SharedFileRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ShareSessionRepository shareSessionRepository;

    @Autowired
    private TransferSessionRepository transferSessionRepository;

    @Autowired
    private SharedFileRepository sharedFileRepository;

    @Autowired
    private TestEntityManager entityManager;

    private TransferSession newTransferSession(Instant now) {
        ShareSession session = shareSessionRepository.saveAndFlush(ShareSessionFixtures.newSession(now));
        return transferSessionRepository.saveAndFlush(new TransferSession(session, now));
    }

    @Test
    void persistsFilesInSequenceOrder() {
        Instant now = Instant.now();
        TransferSession transfer = newTransferSession(now);

        sharedFileRepository.saveAll(List.of(
                new SharedFile(transfer, UUID.randomUUID(), "b.txt", 20L, "text/plain", 1),
                new SharedFile(transfer, UUID.randomUUID(), "a.txt", 10L, "text/plain", 0)
        ));
        sharedFileRepository.flush();

        List<SharedFile> files = sharedFileRepository.findByTransferSessionIdOrderBySequenceOrderAsc(transfer.getId());

        assertThat(files).hasSize(2);
        assertThat(files.get(0).getOriginalFilename()).isEqualTo("a.txt");
        assertThat(files.get(1).getOriginalFilename()).isEqualTo("b.txt");
    }

    @Test
    void rejectsDuplicateSequenceWithinTheSameTransfer() {
        Instant now = Instant.now();
        TransferSession transfer = newTransferSession(now);
        sharedFileRepository.saveAndFlush(new SharedFile(transfer, UUID.randomUUID(), "a.txt", 10L, "text/plain", 0));

        assertThatThrownBy(() -> sharedFileRepository.saveAndFlush(
                new SharedFile(transfer, UUID.randomUUID(), "b.txt", 20L, "text/plain", 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonPositiveFileSize() {
        Instant now = Instant.now();
        TransferSession transfer = newTransferSession(now);

        assertThatThrownBy(() -> sharedFileRepository.saveAndFlush(
                new SharedFile(transfer, UUID.randomUUID(), "empty.txt", 0L, "text/plain", 0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTransferSessionCascadesToItsFiles() {
        Instant now = Instant.now();
        TransferSession transfer = newTransferSession(now);
        SharedFile file = sharedFileRepository.saveAndFlush(
                new SharedFile(transfer, UUID.randomUUID(), "a.txt", 10L, "text/plain", 0));

        transferSessionRepository.delete(transfer);
        transferSessionRepository.flush();
        entityManager.clear();

        assertThat(sharedFileRepository.findById(file.getId())).isEmpty();
    }
}
