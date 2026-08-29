package pj.dooit.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
//@Profile("local")
//@Component
@RequiredArgsConstructor
public class MailSendTestRunner implements CommandLineRunner {

    private final MailService mailService;

    private static final String DEVELOPER_MAIL = "ardimento22@naver.com";

    @Override
    public void run(String... args) {
        log.info("Mail send test start");
        mailService.sendText(DEVELOPER_MAIL, "[Dooit] 메일 설정 테스트", "SMTP 테스트");
        log.info("Mail send test fin");
    }
}
