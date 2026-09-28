package in.unshare.repository;

import in.unshare.entity.SessionStatus;
import in.unshare.entity.ShareSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ShareSessionRepository extends JpaRepository<ShareSession, UUID> {

    Optional<ShareSession> findByToken(String token);

    boolean existsByToken(String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ShareSession s set s.status = 'EXPIRED', s.lastActivityAt = :now "
            + "where s.status in :activeStatuses and s.expiresAt < :now")
    int expireStaleSessions(@Param("now") Instant now,
                             @Param("activeStatuses") Collection<SessionStatus> activeStatuses);
}
