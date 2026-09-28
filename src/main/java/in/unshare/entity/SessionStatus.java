package in.unshare.entity;

import java.util.EnumSet;
import java.util.Set;

public enum SessionStatus {
    CREATED,
    WAITING_FOR_RECEIVER,
    CONNECTING,
    CONNECTED,
    TRANSFERRING,
    COMPLETED,
    EXPIRED,
    CANCELLED,
    FAILED;

    private static final Set<SessionStatus> TERMINAL = EnumSet.of(COMPLETED, EXPIRED, CANCELLED, FAILED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(SessionStatus target) {
        return switch (this) {
            case CREATED -> target == WAITING_FOR_RECEIVER || target == CANCELLED || target == EXPIRED;
            case WAITING_FOR_RECEIVER -> target == CONNECTING || target == CANCELLED || target == EXPIRED;
            case CONNECTING ->
                    target == CONNECTED || target == WAITING_FOR_RECEIVER || target == CANCELLED
                            || target == EXPIRED || target == FAILED;
            case CONNECTED ->
                    target == TRANSFERRING || target == CANCELLED || target == EXPIRED || target == FAILED;
            case TRANSFERRING ->
                    target == COMPLETED || target == CANCELLED || target == FAILED || target == EXPIRED;
            case COMPLETED, EXPIRED, CANCELLED, FAILED -> false;
        };
    }

    public static Set<SessionStatus> nonTerminalStatuses() {
        return EnumSet.complementOf(EnumSet.copyOf(TERMINAL));
    }
}
