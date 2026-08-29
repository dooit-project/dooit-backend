package pj.dooit.notification.service;

import pj.dooit.notification.config.PushNotificationProvider;
import pj.dooit.notification.config.PushNotificationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExpoPushClientTest {

    @Test
    @DisplayName("Expo push client는 단건 메시지를 전송하고 성공 ticket id를 반환한다")
    void send_success() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ExpoPushClient client = new ExpoPushClient(properties("expo-access-token"), restTemplate);
        server.expect(requestTo("https://exp.host/--/api/v2/push/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer expo-access-token"))
                .andExpect(content().json("""
                        {
                          "to": "ExponentPushToken[token]",
                          "title": "일정 알림",
                          "body": "회의",
                          "data": {
                            "taskId": 42
                          }
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "status": "ok",
                            "id": "ticket-42"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ExpoPushTicket ticket = client.send(new ExpoPushMessage(
                "ExponentPushToken[token]",
                "일정 알림",
                "회의",
                Map.of("taskId", 42)
        ));

        assertThat(ticket.successful()).isTrue();
        assertThat(ticket.providerMessageId()).isEqualTo("ticket-42");
        server.verify();
    }

    @Test
    @DisplayName("Expo push client는 error ticket의 provider 오류 코드를 반환한다")
    void send_success_errorTicket() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ExpoPushClient client = new ExpoPushClient(properties(null), restTemplate);
        server.expect(requestTo("https://exp.host/--/api/v2/push/send"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {
                              "status": "error",
                              "message": "not registered",
                              "details": {
                                "error": "DeviceNotRegistered"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        ExpoPushTicket ticket = client.send(new ExpoPushMessage(
                "ExponentPushToken[token]",
                "일정 알림",
                "회의",
                Map.of()
        ));

        assertThat(ticket.successful()).isFalse();
        assertThat(ticket.errorCode()).isEqualTo("DeviceNotRegistered");
        assertThat(ticket.errorMessage()).isEqualTo("not registered");
        server.verify();
    }

    @Test
    @DisplayName("Expo push client는 HTTP 실패를 실패 ticket으로 반환한다")
    void send_fail_httpError() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ExpoPushClient client = new ExpoPushClient(properties(null), restTemplate);
        server.expect(requestTo("https://exp.host/--/api/v2/push/send"))
                .andRespond(withServerError());

        ExpoPushTicket ticket = client.send(new ExpoPushMessage(
                "ExponentPushToken[token]",
                "일정 알림",
                "회의",
                Map.of()
        ));

        assertThat(ticket.successful()).isFalse();
        assertThat(ticket.errorCode()).isEqualTo("HTTP_500");
        server.verify();
    }

    private PushNotificationProperties properties(String accessToken) {
        return new PushNotificationProperties(
                true,
                PushNotificationProvider.EXPO,
                "https://exp.host/--/api/v2/push/send",
                accessToken,
                null,
                null
        );
    }
}
