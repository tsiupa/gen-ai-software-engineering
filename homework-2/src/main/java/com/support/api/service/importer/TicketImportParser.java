package com.support.api.service.importer;

import com.support.api.dto.TicketRequest;

import java.io.InputStream;
import java.util.List;

public interface TicketImportParser {
    List<TicketRequest> parse(InputStream input);
}