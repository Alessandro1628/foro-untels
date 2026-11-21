package pe.edu.untels.forum.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;

@Service
@EnableScheduling
public class CalendarDelivery {
    private final int retrySeconds;
    private final JdbcTemplate db;
    private final RestClient client;
    private final String clientId, secret, refreshToken, calendar, base, tokenEndpoint;

    public CalendarDelivery(
            JdbcTemplate db,
            @Value("${CALENDAR_CLIENT_ID:}") String clientId,
            @Value("${CALENDAR_CLIENT_SECRET:}") String secret,
            @Value("${CALENDAR_REFRESH_TOKEN:}") String refreshToken,
            @Value("${CALENDAR_ID:primary}") String calendar,
            @Value("${calendar.api-url:https://www.googleapis.com/calendar/v3}") String base,
            @Value("${calendar.retry-seconds:120}") int retrySeconds,
            @Value("${calendar.token-url:https://oauth2.googleapis.com/token}")
                    String tokenEndpoint) {
        this.retrySeconds = Math.max(1, retrySeconds);
        this.db = db;
        this.clientId = clientId;
        this.secret = secret;
        this.refreshToken = refreshToken;
        this.calendar = calendar;
        this.base = base;
        this.tokenEndpoint = tokenEndpoint;
        var http =
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(6));
        client = RestClient.builder().requestFactory(factory).build();
    }

    record Token(String access_token) {}

    record ConferenceStatus(String statusCode) {}

    record ConferenceRequest(ConferenceStatus status) {}

    record Conference(ConferenceRequest createRequest) {}

    record Event(String hangoutLink, Conference conferenceData) {
        boolean failed() {
            return conferenceData != null
                    && conferenceData.createRequest() != null
                    && conferenceData.createRequest().status() != null
                    && "failure".equals(conferenceData.createRequest().status().statusCode());
        }
    }

    private String authenticate() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        form.add("refresh_token", refreshToken);
        form.add("grant_type", "refresh_token");
        var result =
                client.post()
                        .uri(tokenEndpoint)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(Token.class);
        if (result == null || result.access_token() == null)
            throw new IllegalStateException("OAuth no disponible");
        return "Bearer " + result.access_token();
    }

    @Scheduled(fixedDelayString = "${calendar.poll-ms:5000}")
    @Transactional
    public void syncNext() {
        if (refreshToken.isBlank() || clientId.isBlank() || secret.isBlank()) return;
        var rows =
                db.queryForList(
                        """
select t.*,s.name as subject from tutoring_slots t join credentials c on t.credential_id=c.id
join subjects s on c.subject_id=s.id where student_id is not null and meet_url is null
and next_sync<=now() and ends_at>now() order by next_sync,t.id limit 1 for update of t skip locked
""");
        if (rows.isEmpty()) return;
        var row = rows.getFirst();
        String id = row.get("calendar_id").toString().replace("-", "");
        String events = base + "/calendars/{calendar}/events";
        try {
            String auth = authenticate();
            var request =
                    Map.of(
                            "createRequest",
                            Map.of(
                                    "requestId",
                                    id,
                                    "conferenceSolutionKey",
                                    Map.of("type", "hangoutsMeet")));
            var event =
                    Map.of(
                            "id",
                            id,
                            "summary",
                            "Tutoría · " + row.get("subject"),
                            "start",
                            Map.of(
                                    "dateTime",
                                    ((Timestamp) row.get("starts_at")).toInstant().toString()),
                            "end",
                            Map.of(
                                    "dateTime",
                                    ((Timestamp) row.get("ends_at")).toInstant().toString()),
                            "attendees",
                            List.of(
                                    Map.of("email", row.get("student_email")),
                                    Map.of("email", row.get("tutor_email"))),
                            "conferenceData",
                            request);
            Event result;
            try {
                result =
                        client.post()
                                .uri(events + "?conferenceDataVersion=1&sendUpdates=all", calendar)
                                .header("Authorization", auth)
                                .body(event)
                                .retrieve()
                                .body(Event.class);
            } catch (HttpClientErrorException.Conflict exists) {
                result =
                        client.get()
                                .uri(events + "/{id}", calendar, id)
                                .header("Authorization", auth)
                                .retrieve()
                                .body(Event.class);
                if (result != null && result.failed()) {
                    var retry =
                            Map.of(
                                    "conferenceData",
                                    Map.of(
                                            "createRequest",
                                            Map.of(
                                                    "requestId",
                                                    UUID.randomUUID().toString(),
                                                    "conferenceSolutionKey",
                                                    Map.of("type", "hangoutsMeet"))));
                    result =
                            client.patch()
                                    .uri(
                                            events
                                                    + "/{id}?conferenceDataVersion=1&sendUpdates=all",
                                            calendar,
                                            id)
                                    .header("Authorization", auth)
                                    .body(retry)
                                    .retrieve()
                                    .body(Event.class);
                }
            }
            String link = result == null ? null : result.hangoutLink();
            if (link != null && link.startsWith("https://meet.google.com/")) {
                db.update(
                        "update tutoring_slots set meet_url=?,sync_state='SYNCED' where id=?",
                        link,
                        row.get("id"));
            } else retryLater(row.get("id"));
        } catch (RestClientException | IllegalStateException ex) {
            retryLater(row.get("id"));
        }
    }

    private void retryLater(Object id) {
        db.update(
                "update tutoring_slots set sync_state='RETRY',next_sync=now()+make_interval(secs =>"
                    + " ?) where id=?",
                retrySeconds,
                id);
    }
}
