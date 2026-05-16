package com.support.api.service.importer;

import com.support.api.exception.ImportFormatException;

public enum ImportFormat {
    CSV,
    JSON,
    XML;

    public static ImportFormat detect(String filename, String contentType) {
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".csv")) return CSV;
            if (lower.endsWith(".json")) return JSON;
            if (lower.endsWith(".xml")) return XML;
        }
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("csv")) return CSV;
            if (ct.contains("json")) return JSON;
            if (ct.contains("xml")) return XML;
        }
        throw new ImportFormatException(
                "Could not determine file format from filename '" + filename
                        + "' or content type '" + contentType + "'. Supported: csv, json, xml.");
    }
}