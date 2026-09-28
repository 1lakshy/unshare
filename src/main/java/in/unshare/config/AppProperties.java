package in.unshare.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotEmpty String baseUrl,
        Share share,
        Cors cors,
        RateLimit rateLimit
) {

    public record Share(
            @Min(1) int defaultTtlMinutes,
            @Min(1) int maxTtlMinutes,
            @Min(1) int maxFiles,
            @Min(1) long maxTotalSizeBytes,
            @Min(1) int defaultMaxReceivers,
            @Min(1) int maxAllowedReceivers
    ) {
    }

    public record Cors(
            @NotEmpty List<String> allowedOrigins
    ) {
    }

    public record RateLimit(
            @Min(1) int capacity,
            @Min(1) int windowSeconds
    ) {
    }
}
