package in.unshare.support;

import in.unshare.entity.ShareSession;

import java.time.Instant;
import java.util.UUID;

/** Shared factories for building valid persistence-layer entities in tests. */
public final class ShareSessionFixtures {

    private ShareSessionFixtures() {
    }

    public static ShareSession newSession(Instant now) {
        return newSession(now, now.plusSeconds(1800));
    }

    public static ShareSession newSession(Instant now, Instant expiresAt) {
        return ShareSession.create("tok_" + UUID.randomUUID(), 1, null, now, expiresAt);
    }

    public static ShareSession newSession(Instant now, Instant expiresAt, int maxReceivers) {
        return ShareSession.create("tok_" + UUID.randomUUID(), maxReceivers, null, now, expiresAt);
    }
}
