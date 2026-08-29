package pj.dooit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionDocumentationConfigurationTest {

    @Test
    @DisplayName("prod 설정은 문서 UI와 OpenAPI JSON을 비공개 기본값으로 둔다")
    void productionDocumentationEndpointsDisabledByDefault() throws Exception {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"));

        assertThat(propertySources.getFirst().getProperty("app.docs.public-enabled"))
                .isEqualTo("${DOOIT_DOCS_PUBLIC_ENABLED:false}");
        assertThat(propertySources.getFirst().getProperty("springdoc.api-docs.enabled"))
                .isEqualTo("${DOOIT_SPRINGDOC_API_DOCS_ENABLED:false}");
        assertThat(propertySources.getFirst().getProperty("springdoc.swagger-ui.enabled"))
                .isEqualTo("${DOOIT_SPRINGDOC_SWAGGER_UI_ENABLED:false}");
    }
}
