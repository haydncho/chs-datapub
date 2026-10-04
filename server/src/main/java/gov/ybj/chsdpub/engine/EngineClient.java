package gov.ybj.chsdpub.engine;

import gov.ybj.chsdpub.common.ApiException;
import gov.ybj.chsdpub.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * 分析引擎（Python，内网）客户端。数字只来自引擎：引擎不可用时报 503，界面提示稍后重试，服务端不自行计算。
 */
@Component
public class EngineClient {

    private static final Logger log = LoggerFactory.getLogger(EngineClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient client;

    public EngineClient(AppProperties props) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(props.engine().connectTimeoutMs());
        f.setReadTimeout(props.engine().readTimeoutMs());
        this.client = RestClient.builder().baseUrl(props.engine().url()).requestFactory(f).build();
    }

    public Map<String, Object> post(String path, Object body) {
        try {
            Map<String, Object> r = client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(MAP);
            if (r == null) throw ApiException.engineUnavailable("统计服务暂无响应,请稍后重试");
            return r;
        } catch (RestClientException e) {
            log.warn("分析引擎调用失败 {}：{}", path, e.getMessage());
            throw ApiException.engineUnavailable("统计服务暂不可用,请稍后重试");
        }
    }
}
