package in.unshare.repository;

import in.unshare.entity.SharedFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SharedFileRepository extends JpaRepository<SharedFile, UUID> {

    List<SharedFile> findByTransferSessionIdOrderBySequenceOrderAsc(UUID transferSessionId);
}
