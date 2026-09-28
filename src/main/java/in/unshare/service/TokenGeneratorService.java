package in.unshare.service;

import in.unshare.repository.ShareSessionRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TokenGeneratorService {

    private static final int TOKEN_BYTES = 24;
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ShareSessionRepository shareSessionRepository;

    public TokenGeneratorService(ShareSessionRepository shareSessionRepository) {
        this.shareSessionRepository = shareSessionRepository;
    }

    public String generateUniqueToken() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = generateToken();
            if (!shareSessionRepository.existsByToken(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique share token after " + MAX_ATTEMPTS + " attempts");
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
