package in.unshare.exception;

public class ShareSessionExpiredException extends RuntimeException {

    public ShareSessionExpiredException(String token) {
        super("Share session has expired: " + token);
    }
}
