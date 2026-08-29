package pj.dooit.vtTest;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@TestConfiguration
public class VtTestEndPointConfig {

    @RestController
    static class VtProbeController {
        @GetMapping("/__vt/probe")
        public String probe() {
            // thread 정보 문자열로 반환
            return Thread.currentThread().toString();
        }
    }
}
