package in.unshare.exception;

public class ShareSessionNotFoundException extends RuntimeException {

    public ShareSessionNotFoundException(String token) {
        super("No share session found for token: " + token);
    }
}
