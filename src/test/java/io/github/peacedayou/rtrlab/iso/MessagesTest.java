package io.github.peacedayou.rtrlab.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MessagesTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = MessagesTest.class.getResourceAsStream("/exemples/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void exemplePacs008_estConformeEtLisible() throws IOException {
        String xml = resource("pacs008-lot.xml");
        assertEquals(java.util.List.of(), XsdValidator.errors(Iso20022.PACS_008, xml));
        var msg = Pacs008.parse(xml);
        assertEquals("LOT-20261116-001", msg.msgId());
        assertEquals(3, msg.transfers().size());
        var t = msg.transfers().get(0);
        assertEquals(new BigDecimal("125.50"), t.amount());
        assertEquals("CA-815-0001", t.debtorAccount());
        assertEquals("0006", t.creditorAgent());
        assertEquals("b3c1e0a2-6f1d-4c2e-9a7b-1d2e3f4a5b6c", t.uetr());
    }

    @Test
    void erreursDeSchema_sontDetaillees() throws IOException {
        String xml = resource("pacs008-lot.xml").replace("<SttlmMtd>CLRG</SttlmMtd>", "<SttlmMtd>XXXX</SttlmMtd>");
        var errors = XsdValidator.errors(Iso20022.PACS_008, xml);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).startsWith("ligne "));
    }

    @Test
    void montantNegatifOuTropDeDecimales_refuses() throws IOException {
        String base = resource("pacs008-lot.xml");
        assertFalse(XsdValidator.errors(Iso20022.PACS_008, base.replace(">125.50<", ">-1<")).isEmpty());
        assertFalse(XsdValidator.errors(Iso20022.PACS_008, base.replace(">125.50<", ">1.123456<")).isEmpty());
    }

    @Test
    void camt054_allerRetour() {
        var e = new Camt054.Entry("CA-006-0001", new BigDecimal("12.34"), "CAD", "CRDT", java.time.LocalDate.of(2026, 11, 16),
                "M1", "E2E-1", null);
        String xml = Camt054.write("NTF1", java.time.OffsetDateTime.parse("2026-11-16T09:30:00-05:00"), java.util.List.of(e));
        assertEquals(java.util.List.of(e), Camt054.parse(xml));
    }
}
