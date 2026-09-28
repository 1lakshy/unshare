package in.unshare.service;

import in.unshare.repository.ShareSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenGeneratorServiceTest {

    @Mock
    private ShareSessionRepository shareSessionRepository;

    @Test
    void generatesUrlSafeTokenOfExpectedLength() {
        when(shareSessionRepository.existsByToken(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        TokenGeneratorService service = new TokenGeneratorService(shareSessionRepository);

        String token = service.generateUniqueToken();

        assertThat(token).matches("[A-Za-z0-9_-]+");
        assertThat(token.length()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void retriesOnCollisionUntilUniqueTokenFound() {
        when(shareSessionRepository.existsByToken(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true, true, false);
        TokenGeneratorService service = new TokenGeneratorService(shareSessionRepository);

        String token = service.generateUniqueToken();

        assertThat(token).isNotBlank();
    }

    @Test
    void givesUpAfterMaxAttempts() {
        when(shareSessionRepository.existsByToken(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        TokenGeneratorService service = new TokenGeneratorService(shareSessionRepository);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, service::generateUniqueToken);
    }
}
