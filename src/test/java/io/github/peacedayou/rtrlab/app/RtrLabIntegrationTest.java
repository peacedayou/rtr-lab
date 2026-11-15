package io.github.peacedayou.rtrlab.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.peacedayou.rtrlab.engine.Reconciler;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

/** Bout en bout avec un vrai PostgreSQL et un vrai Kafka (Testcontainers). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RtrLabIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.8.0");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcLedger ledger;

    private ResponseEntity<String> post(String xml) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_XML);
        return http.postForEntity("/api/pacs008", new HttpEntity<>(xml, h), String.class);
    }

    private static String sample() throws Exception {
        try (InputStream in = RtrLabIntegrationTest.class.getResourceAsStream("/exemples/pacs008-lot.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void lotComplet_reglementIdempotenceKafkaEtRapprochement() throws Exception {
        ResponseEntity<String> first = post(sample());
        assertEquals(200, first.getStatusCode().value());
        String body = first.getBody();
        assertTrue(body.contains("<GrpSts>PART</GrpSts>"), body);
        assertTrue(body.contains("<Cd>AM04</Cd>"), body);
        assertTrue(body.contains("<Cd>AC04</Cd>"), body);

        // Renvoi du même lot : même réponse, aucun nouveau débit.
        ResponseEntity<String> second = post(sample());
        assertTrue(second.getBody().contains("<GrpSts>PART</GrpSts>"));
        assertEquals(0, new BigDecimal("874.50").compareTo(ledger.find("CA-815-0001").orElseThrow().balance()));
        assertEquals(0, new BigDecimal("125.50").compareTo(ledger.find("CA-006-0001").orElseThrow().balance()));

        // Le camt.054 fait l'aller-retour par Kafka, puis le rapprochement doit être équilibré.
        Reconciler.Report report = null;
        for (int i = 0; i < 60; i++) {
            report = http.getForObject("/api/rapprochement", Reconciler.Report.class);
            if (report != null && report.count(Reconciler.Kind.RAPPROCHE) == 1) {
                break;
            }
            Thread.sleep(500);
        }
        assertTrue(report != null && report.balanced(), String.valueOf(report));
        assertEquals(1, report.count(Reconciler.Kind.RAPPROCHE));
    }

    @Test
    void messageNonConforme_rejetFF01() {
        ResponseEntity<String> r = post("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\"><Rien/></Document>");
        assertEquals(400, r.getStatusCode().value());
        assertTrue(r.getBody().contains("<Cd>FF01</Cd>"));
        assertTrue(r.getHeaders().getFirst("X-Rejet-Detail").contains("pacs.008"));
    }
}
