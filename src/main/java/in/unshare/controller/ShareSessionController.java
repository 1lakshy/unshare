package in.unshare.controller;

import in.unshare.dto.CreateShareRequest;
import in.unshare.dto.ShareSessionResponse;
import in.unshare.exception.RateLimitExceededException;
import in.unshare.service.RateLimiterService;
import in.unshare.service.ShareSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shares")
public class ShareSessionController {

    private final ShareSessionService shareSessionService;
    private final RateLimiterService rateLimiterService;

    public ShareSessionController(ShareSessionService shareSessionService, RateLimiterService rateLimiterService) {
        this.shareSessionService = shareSessionService;
        this.rateLimiterService = rateLimiterService;
    }

    @PostMapping
    public ResponseEntity<ShareSessionResponse> create(@Valid @RequestBody CreateShareRequest request,
                                                         HttpServletRequest httpRequest) {
        String clientKey = clientKey(httpRequest);
        if (!rateLimiterService.tryAcquire(clientKey)) {
            throw new RateLimitExceededException("Too many share sessions created from this address, try again later");
        }

        ShareSessionResponse response = shareSessionService.createShare(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{token}")
    public ShareSessionResponse get(@PathVariable String token) {
        return shareSessionService.getByToken(token);
    }

    @PostMapping("/{token}/join")
    public ShareSessionResponse join(@PathVariable String token) {
        return shareSessionService.join(token);
    }

    @DeleteMapping("/{token}")
    public ShareSessionResponse cancel(@PathVariable String token) {
        return shareSessionService.cancel(token);
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
