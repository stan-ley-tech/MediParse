package com.mediparse.processing;

import com.mediparse.config.ProcessingProperties;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Pulls plain text out of PDF/DOCX/TXT via Tika's AutoDetectParser, which
 * streams the input rather than buffering the whole document — the only
 * thing we hold in memory afterwards is the extracted text, capped at
 * mediparse.processing.text-extraction-char-limit so a huge file degrades
 * to a truncated extraction instead of an out-of-memory error.
 */
@Service
public class TextExtractionService {

    private final Parser parser = new AutoDetectParser();
    private final ProcessingProperties properties;

    public TextExtractionService(ProcessingProperties properties) {
        this.properties = properties;
    }

    public ExtractedText extract(InputStream content, String contentType) {
        BodyContentHandler handler = new BodyContentHandler(properties.textExtractionCharLimit());
        Metadata metadata = new Metadata();
        if (contentType != null) {
            metadata.set(Metadata.CONTENT_TYPE, contentType);
        }

        boolean truncated = false;
        try {
            parser.parse(content, handler, metadata, new ParseContext());
        } catch (WriteLimitReachedException e) {
            truncated = true;
        } catch (Exception e) {
            throw new TextExtractionException("Failed to extract text from document", e);
        }

        String text = handler.toString();
        return new ExtractedText(text, text.length(), truncated);
    }
}
