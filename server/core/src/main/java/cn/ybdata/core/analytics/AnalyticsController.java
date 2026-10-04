package cn.ybdata.core.analytics;

import cn.ybdata.core.config.YbProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Pass-through to the Python analytics service, so the browser only ever
 * talks to the core API (one origin, one audit/auth boundary).
 * GET /api/v1/analytics/** → {analyticsUrl}/analytics/**
 *
 * <p>Calls time out after {@code yb.analytics-timeout-ms} (default 3 s). The last good
 * response per path + query is kept; when the service is slow or down it is served
 * instead, marked {@code X-YB-Analytics: stale} (fresh answers carry {@code live}).
 * Client errors (4xx, e.g. a bad query parameter) are passed through, never masked.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    static final String HEADER = "X-YB-Analytics";
    static final int CACHE_MAX = 128;
    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

    private record Entry(JsonNode body, Instant at) {}

    private final RestClient http;
    /** last good response per request URI, least-recently-used evicted */
    private final Map<String, Entry> lastGood = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > CACHE_MAX;
        }
    };

    public AnalyticsController(YbProperties props, RestClient.Builder builder,
                               @Value("${yb.analytics-timeout-ms:3000}") long timeoutMs) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofMillis(timeoutMs));
        rf.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.http = builder.baseUrl(props.analyticsUrl()).requestFactory(rf).build();
    }

    @GetMapping("/**")
    public ResponseEntity<JsonNode> proxy(HttpServletRequest req) {
        String path = req.getRequestURI().substring("/api/v1".length());
        String q = req.getQueryString();
        String uri = q == null ? path : path + "?" + q;
        try {
            JsonNode body = http.get().uri(uri).retrieve().body(JsonNode.class);
            synchronized (lastGood) {
                lastGood.put(uri, new Entry(body, Instant.now()));
            }
            return ResponseEntity.ok().header(HEADER, "live").body(body);
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).header(HEADER, "error").build();
        } catch (RestClientException e) {
            Entry hit;
            synchronized (lastGood) {
                hit = lastGood.get(uri);
            }
            if (hit == null) {
                log.warn("analytics {} unavailable, nothing cached: {}", uri, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).header(HEADER, "down").build();
            }
            log.warn("analytics {} unavailable, serving response from {}: {}", uri, hit.at(), e.getMessage());
            return ResponseEntity.ok()
                    .header(HEADER, "stale")
                    .header("X-YB-Analytics-At", hit.at().toString())
                    .body(hit.body());
        }
    }
}
