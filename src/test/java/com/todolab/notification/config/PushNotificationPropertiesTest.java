package com.todolab.notification.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushNotificationPropertiesTest {

    @Test
    @DisplayName("push notification 설정 기본값은 EXPO provider와 비활성 상태다")
    void defaultValues() {
        PushNotificationProperties properties = new PushNotificationProperties(false, null, null, null);

        assertThat(properties.enabled()).isFalse();
        assertThat(properties.provider()).isEqualTo(PushNotificationProvider.EXPO);
        assertThat(properties.endpoint()).isEqualTo("https://exp.host/--/api/v2/push/send");
        assertThat(properties.accessToken()).isNull();
    }
}
