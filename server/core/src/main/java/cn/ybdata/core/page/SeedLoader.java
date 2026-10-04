package cn.ybdata.core.page;

import cn.ybdata.core.config.YbProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Imports classpath:seed/pages/{code}.json (exported from the frontend demo
 * data by `npm run export-seed`) into page_payload on start-up.
 */
@Component
public class SeedLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedLoader.class);

    private final PageService pages;
    private final YbProperties props;

    public SeedLoader(PageService pages, YbProperties props) {
        this.pages = pages;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Resource[] seeds = new PathMatchingResourcePatternResolver().getResources("classpath*:seed/pages/*.json");
        for (Resource r : seeds) {
            String file = r.getFilename();
            if (file == null) continue;
            pages.put(file.substring(0, file.length() - ".json".length()),
                    r.getContentAsString(StandardCharsets.UTF_8), props.seedOverwrite());
        }
        log.info("page seeds: {} files, overwrite={}", seeds.length, props.seedOverwrite());
    }
}
