package com.todolab.notification.service;

import com.todolab.notification.config.PushNotificationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class ExpoPushClient {

    private final PushNotificationProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public ExpoPushClient(PushNotificationProperties properties) {
        this(properties, new RestTemplate());
    }

    ExpoPushClient(PushNotificationProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public ExpoPushTicket send(ExpoPushMessage message) {
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    properties.endpoint(),
                    new HttpEntity<>(requestBody(message), headers()),
                    Map.class
            );
            return ticketFrom(response.getBody());
        } catch (RestClientResponseException ex) {
            return ExpoPushTicket.failure("HTTP_" + ex.getStatusCode().value(), truncate(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            return ExpoPushTicket.failure("PROVIDER_REQUEST_FAILED", truncate(ex.getMessage()));
        }
    }

    private Map<String, Object> requestBody(ExpoPushMessage message) {
        return Map.of(
                "to", message.to(),
                "title", message.title(),
                "body", message.body(),
                "data", message.data() == null ? Map.of() : message.data()
        );
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (properties.accessToken() != null) {
            headers.setBearerAuth(properties.accessToken());
        }
        return headers;
    }

    private ExpoPushTicket ticketFrom(Map<?, ?> body) {
        Object data = body == null ? null : body.get("data");
        if (data instanceof List<?> tickets && !tickets.isEmpty()) {
            return ticketFrom(tickets.getFirst());
        }
        return ticketFrom(data);
    }

    private ExpoPushTicket ticketFrom(Object ticket) {
        if (!(ticket instanceof Map<?, ?> ticketMap)) {
            return ExpoPushTicket.failure("EMPTY_TICKET", "Expo push response did not include a ticket.");
        }

        String status = stringValue(ticketMap.get("status"));
        if ("ok".equals(status)) {
            return ExpoPushTicket.success(stringValue(ticketMap.get("id")));
        }

        return ExpoPushTicket.failure(errorCode(ticketMap), stringValue(ticketMap.get("message")));
    }

    private String errorCode(Map<?, ?> ticketMap) {
        Object details = ticketMap.get("details");
        if (details instanceof Map<?, ?> detailMap) {
            String error = stringValue(detailMap.get("error"));
            if (error != null) {
                return error;
            }
        }
        String status = stringValue(ticketMap.get("status"));
        return status == null ? "EXPO_TICKET_ERROR" : status;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }
}
