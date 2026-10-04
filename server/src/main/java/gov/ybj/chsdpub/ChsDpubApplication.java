package gov.ybj.chsdpub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChsDpubApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChsDpubApplication.class, args);
    }
}
