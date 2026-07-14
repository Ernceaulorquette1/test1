package ai.nutriscan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class NutriScanApplication {

    public static void main(String[] args) {
        SpringApplication.run(NutriScanApplication.class, args);
    }
}
