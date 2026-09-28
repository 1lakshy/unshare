package in.unshare.repository;

import in.unshare.entity.TransferSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransferSessionRepository extends JpaRepository<TransferSession, UUID> {

    List<TransferSession> findByShareSessionIdOrderByLastActivityAtDesc(UUID shareSessionId);
}
