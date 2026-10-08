package cn.ybdata.core;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Integration tests that call the API without logging in authenticate with {@code X-YB-User: <login>}
 * (a real seeded user, access-checked as that user). The header is off by default, so such tests switch it
 * on explicitly via {@link #enable}.
 */
public final class DevHeader {

    public static final String HEADER = "X-YB-User";

    private DevHeader() {}

    /** {@code yb.auth.dev-header=true} for this test context. */
    public static void enable(DynamicPropertyRegistry r) {
        r.add("yb.auth.dev-header", () -> "true");
    }

    /** Send {@code X-YB-User: login} on every request of {@code http} that does not set its own credentials. */
    public static void defaultUser(TestRestTemplate http, String login) {
        var interceptors = http.getRestTemplate().getInterceptors();
        if (interceptors.stream().anyMatch(i -> i instanceof Default)) return;
        interceptors.add(new Default(login));
    }

    private record Default(String login) implements ClientHttpRequestInterceptor {
        @Override
        public org.springframework.http.client.ClientHttpResponse intercept(org.springframework.http.HttpRequest req, byte[] body,
                org.springframework.http.client.ClientHttpRequestExecution exec) throws java.io.IOException {
            var h = req.getHeaders();
            if (!h.containsKey(HEADER) && !h.containsKey(org.springframework.http.HttpHeaders.AUTHORIZATION)) h.set(HEADER, login);
            return exec.execute(req, body);
        }
    }
}
