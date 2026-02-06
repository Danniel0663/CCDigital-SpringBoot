package co.edu.unbosque.ccdigital;

import co.edu.unbosque.ccdigital.config.ExternalToolsProperties;
import co.edu.unbosque.ccdigital.config.FileStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        FileStorageProperties.class
})
public class CcDigitalApplication {

    public static void main(String[] args) {
        SpringApplication.run(CcDigitalApplication.class, args);
    }
}
