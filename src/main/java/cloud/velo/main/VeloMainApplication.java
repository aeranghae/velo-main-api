package cloud.velo.main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class VeloMainApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeloMainApplication.class, args);
    }

}
