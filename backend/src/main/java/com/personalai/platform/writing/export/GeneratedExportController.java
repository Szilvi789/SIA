package com.personalai.platform.writing.export;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/writing/exports")
public class GeneratedExportController {
    private final GeneratedExportService exports;

    public GeneratedExportController(GeneratedExportService exports) { this.exports = exports; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public GeneratedExport save(@RequestParam("format") ExportFormat format, @RequestParam("file") MultipartFile file)
            throws IOException {
        // The client filename and MIME type are deliberately ignored; the server validates the bytes.
        try (var input = file.getInputStream()) { return exports.save(format, file.getSize(), input); }
    }
}
