package pj.dooit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class DooitApplication {

    public static void main(String[] args) {
        SpringApplication.run(DooitApplication.class, args);
        log.info("\n==================================" +
                 "\n  To Do Lab Application           " +
                 "\n==================================");
    }
}
