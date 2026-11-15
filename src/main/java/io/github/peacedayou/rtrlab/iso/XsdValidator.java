package io.github.peacedayou.rtrlab.iso;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/** Validation XSD des messages ISO 20022 (schémas chargés une fois, depuis le classpath). */
public final class XsdValidator {

    private static final Map<String, Schema> CACHE = new ConcurrentHashMap<>();

    private XsdValidator() {}

    /** Liste des erreurs de validation ; vide si le message est conforme. */
    public static List<String> errors(String messageId, String xml) {
        List<String> errors = new ArrayList<>();
        try {
            Validator v = schema(messageId).newValidator();
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            v.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            v.setErrorHandler(new ErrorHandler() {
                @Override public void warning(SAXParseException e) {}
                @Override public void error(SAXParseException e) { errors.add(format(e)); }
                @Override public void fatalError(SAXParseException e) { errors.add(format(e)); }
            });
            v.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            if (errors.isEmpty()) {
                errors.add(format(e));
            }
        } catch (Exception e) {
            errors.add(e.getMessage());
        }
        return errors;
    }

    public static void requireValid(String messageId, String xml) {
        List<String> errors = errors(messageId, xml);
        if (!errors.isEmpty()) {
            throw new InvalidMessageException("Non conforme à " + messageId + " : " + String.join(" ; ", errors));
        }
    }

    private static Schema schema(String messageId) {
        return CACHE.computeIfAbsent(messageId, id -> {
            try {
                SchemaFactory f = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                f.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                var url = XsdValidator.class.getResource("/xsd/" + id + ".xsd");
                if (url == null) {
                    throw new IllegalStateException("Schéma introuvable : " + id);
                }
                return f.newSchema(url);
            } catch (Exception e) {
                throw new IllegalStateException("Schéma illisible : " + id, e);
            }
        });
    }

    private static String format(SAXParseException e) {
        return "ligne " + e.getLineNumber() + " : " + e.getMessage();
    }
}
