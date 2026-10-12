package app.services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PdfCharacterCountServiceTest {
    @TempDir Path directory;
    private final PdfCharacterCountService service = new PdfCharacterCountService();

    @Test void countsExtractedDanishTextIncludingSpacesAcrossPages() throws Exception {
        var result = service.count(pdf("Æble ø å!", "Hej verden"));
        assertEquals(20, result.characters()); // 9 + page separator + 10
        assertTrue(result.allPagesHaveText());
        assertTrue(result.modelInstructions().contains("20 tegn inklusive mellemrum"));
    }

    @Test void normalizesLayoutWhitespaceAndCombiningCharacters() {
        assertEquals("Å æ ø", PdfCharacterCountService.normalize("  A\u030A\r\n\tæ\u00a0  ø  "));
    }

    @Test void doesNotTreatBlankOrUnreadablePdfAsZeroCharacters() throws Exception {
        assertNull(service.count(pdf("")).characters());
        Path invalid = directory.resolve("invalid.pdf");
        Files.writeString(invalid, "%PDF-broken");
        assertNull(service.count(invalid).characters());
        assertTrue(service.count(invalid).markdown().contains("kunne ikke tælles"));
    }

    @Test void flagsPagesWithoutTextAndKeepsAppendicesInWholeDocumentCount() throws Exception {
        var result = service.count(pdf("Rapport", "", "Bilag"));
        assertEquals(13, result.characters());
        assertFalse(result.allPagesHaveText());
        assertTrue(result.markdown().contains("ufuldstændig"));
        assertTrue(result.markdown().contains("ikke automatisk fjernet"));
    }

    @Test void comparesBoundaryWithoutConfusingItWithWordsOrAnAutomaticRejection() {
        assertTrue(new PdfCharacterCountService.Result(11999, true).markdown().contains("1 tegn under"));
        assertTrue(new PdfCharacterCountService.Result(12000, true).markdown().contains("præcis på"));
        assertTrue(new PdfCharacterCountService.Result(12001, true).markdown().contains("1 tegn over"));
        assertTrue(new PdfCharacterCountService.Result(12001, true).modelInstructions().contains("beviser ikke"));
    }

    private Path pdf(String... pages) throws Exception {
        Path file = directory.resolve("example.pdf");
        try (var document = new PDDocument()) {
            for (String text : pages) {
                var page = new PDPage();
                document.addPage(page);
                if (!text.isEmpty()) {
                    try (var content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(50, 700);
                        content.showText(text);
                        content.endText();
                    }
                }
            }
            document.save(file.toFile());
        }
        return file;
    }
}
