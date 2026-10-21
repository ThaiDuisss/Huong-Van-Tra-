package org.hvtra.fileservice.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class AiReportDocumentValidator {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_EXPANDED_BYTES = 50 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    private static final Set<String> REQUIRED = Set.of("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml");

    public void validate(byte[] bytes, String type) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw invalid();
        }
        if ("PDF".equals(type)) {
            String header = new String(bytes, 0, Math.min(8, bytes.length), StandardCharsets.US_ASCII);
            if (!header.matches("%PDF-(1\\.[0-7]|2\\.0)")) {
                throw invalid();
            }
            return;
        }
        if (!"XLSX".equals(type)) {
            throw invalid();
        }
        requireCompleteZip(bytes);
        Map<String, byte[]> required = new HashMap<>();
        Set<String> names = new HashSet<>();
        int expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (names.size() >= 512 || !names.add(name) || name.startsWith("/")
                        || name.contains("\\") || name.contains("../") || name.endsWith("/..")) {
                    throw invalid();
                }
                int entryBytes = 0;
                ByteArrayOutputStream output = REQUIRED.contains(name) ? new ByteArrayOutputStream() : null;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryBytes += read;
                    expanded += read;
                    if (entryBytes > MAX_ENTRY_BYTES || expanded > MAX_EXPANDED_BYTES
                            || (output != null && entryBytes > 1024 * 1024)
                            || expanded > Math.max(1024 * 1024L, bytes.length * 100L)) {
                        throw invalid();
                    }
                    if (output != null) output.write(buffer, 0, read);
                }
                if (output != null) required.put(name, output.toByteArray());
            }
            if (!required.keySet().containsAll(REQUIRED)) throw invalid();
            Document types = xml(required.get("[Content_Types].xml"));
            Document rels = xml(required.get("_rels/.rels"));
            Document workbook = xml(required.get("xl/workbook.xml"));
            requireRoot(types, "Types", "http://schemas.openxmlformats.org/package/2006/content-types");
            requireRoot(rels, "Relationships", "http://schemas.openxmlformats.org/package/2006/relationships");
            requireRoot(workbook, "workbook", "http://schemas.openxmlformats.org/spreadsheetml/2006/main");
            boolean mainType = false;
            var overrides = types.getDocumentElement().getElementsByTagNameNS("*", "Override");
            for (int i = 0; i < overrides.getLength(); i++) {
                var element = overrides.item(i).getAttributes();
                var part = element.getNamedItem("PartName");
                var mime = element.getNamedItem("ContentType");
                if (part != null && mime != null && "/xl/workbook.xml".equals(part.getNodeValue())
                        && "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml".equals(mime.getNodeValue())) {
                    mainType = true;
                }
            }
            boolean mainRelationship = false;
            var relationships = rels.getDocumentElement().getElementsByTagNameNS("*", "Relationship");
            for (int i = 0; i < relationships.getLength(); i++) {
                var attributes = relationships.item(i).getAttributes();
                var target = attributes.getNamedItem("Target");
                var typeAttribute = attributes.getNamedItem("Type");
                var mode = attributes.getNamedItem("TargetMode");
                if (target != null && typeAttribute != null && (mode == null || !"External".equals(mode.getNodeValue()))
                        && Set.of("xl/workbook.xml", "/xl/workbook.xml").contains(target.getNodeValue())
                        && "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument".equals(typeAttribute.getNodeValue())) {
                    mainRelationship = true;
                }
            }
            if (!mainType || !mainRelationship || names.stream().anyMatch(name -> name.endsWith("vbaProject.bin"))) throw invalid();
        } catch (ResponseStatusException error) {
            throw error;
        } catch (Exception error) {
            throw invalid();
        }
    }

    private Document xml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "128");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private void requireCompleteZip(byte[] bytes) {
        // ZIP64/multidisk are unnecessary for a <=10 MiB document. Require a real
        // central directory and complete EOCD, not only readable local entries.
        ByteBuffer data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset = bytes.length - 22; offset >= Math.max(0, bytes.length - 65557); offset--) {
            if (data.getInt(offset) != 0x06054b50) continue;
            int comment = Short.toUnsignedInt(data.getShort(offset + 20));
            int entries = Short.toUnsignedInt(data.getShort(offset + 10));
            long size = Integer.toUnsignedLong(data.getInt(offset + 12));
            long start = Integer.toUnsignedLong(data.getInt(offset + 16));
            if (offset + 22 + comment != bytes.length || data.getShort(offset + 4) != 0
                    || data.getShort(offset + 6) != 0 || data.getShort(offset + 8) != data.getShort(offset + 10)
                    || entries < 3 || entries > 512 || size < 46 || start + size != offset
                    || start > bytes.length - 4 || data.getInt((int) start) != 0x02014b50) throw invalid();
            return;
        }
        throw invalid();
    }

    private void requireRoot(Document document, String name, String namespace) {
        if (!name.equals(document.getDocumentElement().getLocalName())
                || !namespace.equals(document.getDocumentElement().getNamespaceURI())) throw invalid();
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Invalid PDF/XLSX document");
    }
}
