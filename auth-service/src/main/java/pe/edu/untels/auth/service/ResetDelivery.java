package pe.edu.untels.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;

@Service
@EnableScheduling
public class ResetDelivery {
    private final JdbcTemplate db;
    private final RestClient mail;
    private final String key, sender, site;
    private final SecureRandom random = new SecureRandom();

    public ResetDelivery(
            JdbcTemplate db,
            @Value("${RESET_MAIL_KEY:}") String key,
            @Value("${RESET_MAIL_FROM:}") String sender,
            @Value("${PUBLIC_FRONTEND_URL:}") String site,
            @Value("${reset.mail-url:https://api.resend.com/emails}") String endpoint) {
        this.db = db;
        this.key = key;
        this.sender = sender;
        this.site = site.replaceAll("/+$", "");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.mail = RestClient.builder().baseUrl(endpoint).requestFactory(factory).build();
    }

    public boolean configured() {
        return !key.isBlank() && !sender.isBlank() && !site.isBlank();
    }

    public static String digest(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Scheduled(fixedDelayString = "${reset.poll-ms:3000}")
    @Transactional
    public void deliverNext() {
        if (!configured()) return;
        var rows =
                db.queryForList(
                        """
                        select id,email from users where enabled and reset_requested<=now()
                        order by reset_requested limit 1 for update skip locked
                        """);
        if (rows.isEmpty()) return;
        var row = rows.getFirst();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        try {
            mail.post()
                    .header("Authorization", "Bearer " + key)
                    .body(
                            Map.of(
                                    "from",
                                    sender,
                                    "to",
                                    List.of(row.get("email")),
                                    "subject",
                                    "Restablecer contraseña · Foro UNTELS",
                                    "text",
                                    "Abre este enlace en los próximos 20 minutos: "
                                            + site
                                            + "/#reset="
                                            + token))
                    .retrieve()
                    .toBodilessEntity();
            db.update(
                    "update users set reset_digest=?,reset_sent=now(),reset_requested=null where"
                        + " id=?",
                    digest(token),
                    row.get("id"));
        } catch (RestClientException ex) {
            db.update(
                    "update users set reset_requested=now()+interval '5 minutes' where id=?",
                    row.get("id"));
        }
    }
}
