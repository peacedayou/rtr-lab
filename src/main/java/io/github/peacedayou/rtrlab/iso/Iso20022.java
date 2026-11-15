package io.github.peacedayou.rtrlab.iso;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Outils XML communs aux messages ISO 20022, sans dépendance externe (JDK seulement). */
public final class Iso20022 {

    public static final String PACS_008 = "pacs.008.001.08";
    public static final String PACS_002 = "pacs.002.001.10";
    public static final String CAMT_054 = "camt.054.001.08";

    private Iso20022() {}

    private static final java.time.format.DateTimeFormatter DATE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** Date et heure ISO 8601 avec secondes et fuseau, toujours conformes au type xs:dateTime. */
    public static String dateTime(java.time.OffsetDateTime t) {
        return DATE_TIME.format(t);
    }

    public static String ns(String messageId) {
        return "urn:iso:std:iso:20022:tech:xsd:" + messageId;
    }

    /** Analyseur durci : pas de DTD, pas d'entités externes (protection XXE). */
    public static Document parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(null);
            return b.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new InvalidMessageException("XML illisible : " + e.getMessage(), e);
        }
    }

    public static Document newDocument() {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            return f.newDocumentBuilder().newDocument();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String toXml(Document doc) {
        try {
            var t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter w = new StringWriter();
            t.transform(new DOMSource(doc), new StreamResult(w));
            return w.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Ajoute un enfant dans l'espace de noms du parent, avec un texte facultatif. */
    public static Element add(Element parent, String name, String text) {
        Element e = parent.getOwnerDocument().createElementNS(parent.getNamespaceURI(), name);
        if (text != null) {
            e.setTextContent(text);
        }
        parent.appendChild(e);
        return e;
    }

    public static Element add(Element parent, String name) {
        return add(parent, name, null);
    }

    /** Suit un chemin d'enfants directs, par exemple « PmtId/EndToEndId ». Null si absent. */
    public static Element child(Element from, String path) {
        Element cur = from;
        for (String part : path.split("/")) {
            Element next = null;
            for (Node n = cur.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element el && part.equals(el.getLocalName())) {
                    next = el;
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            cur = next;
        }
        return cur;
    }

    public static String text(Element from, String path) {
        Element e = child(from, path);
        return e == null ? null : e.getTextContent().trim();
    }

    public static List<Element> children(Element from, String name) {
        List<Element> out = new ArrayList<>();
        for (Node n = from.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element el && name.equals(el.getLocalName())) {
                out.add(el);
            }
        }
        return out;
    }
}
