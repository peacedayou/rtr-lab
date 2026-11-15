package io.github.peacedayou.rtrlab.app;

import io.github.peacedayou.rtrlab.engine.Ledger;
import io.github.peacedayou.rtrlab.engine.Reconciler;
import io.github.peacedayou.rtrlab.iso.InvalidMessageException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService service;
    private final JdbcLedger ledger;

    public PaymentController(PaymentService service, JdbcLedger ledger) {
        this.service = service;
        this.ledger = ledger;
    }

    /** Reçoit un pacs.008 et répond par le pacs.002 correspondant. */
    @PostMapping(value = "/pacs008", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, MediaType.TEXT_PLAIN_VALUE},
            produces = MediaType.APPLICATION_XML_VALUE)
    public String submit(@RequestBody String pacs008) {
        return service.submit(pacs008).pacs002();
    }

    @GetMapping("/comptes")
    public List<Ledger.Account> accounts() {
        return ledger.all();
    }

    @GetMapping("/rapprochement")
    public Reconciler.Report reconciliation() {
        return service.reconcile();
    }

    /** Message illisible ou non conforme au XSD : rejet global FF01, avec le détail en en-tête. */
    @ExceptionHandler(InvalidMessageException.class)
    public ResponseEntity<String> invalid(InvalidMessageException e) {
        String detail = java.text.Normalizer.normalize(e.getMessage(), java.text.Normalizer.Form.NFD).replaceAll("[^\\x20-\\x7E]", "");
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_XML)
                .header("X-Rejet-Detail", detail.length() > 300 ? detail.substring(0, 300) : detail)
                .body(service.reject(detail));
    }
}
