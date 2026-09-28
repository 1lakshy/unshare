package in.unshare.entity;

import java.util.EnumSet;
import java.util.Set;

public enum TransferStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<TransferStatus> TERMINAL = EnumSet.of(COMPLETED, FAILED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(TransferStatus target) {
        return switch (this) {
            case PENDING -> target == IN_PROGRESS || target == CANCELLED || target == FAILED;
            case IN_PROGRESS -> target == COMPLETED || target == FAILED || target == CANCELLED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }

    public static Set<TransferStatus> nonTerminalStatuses() {
        return EnumSet.complementOf(EnumSet.copyOf(TERMINAL));
    }
}
