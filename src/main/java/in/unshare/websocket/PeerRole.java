package in.unshare.websocket;

public enum PeerRole {
    SENDER,
    RECEIVER;

    public PeerRole other() {
        return this == SENDER ? RECEIVER : SENDER;
    }
}
