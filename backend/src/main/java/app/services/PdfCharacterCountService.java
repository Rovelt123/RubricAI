package app.services;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;

/** Simple count of extracted text from the entire PDF, not a word count or OCR. */
public class PdfCharacterCountService {
    public static final int CHARACTER_LIMIT = 12000;

    public Result count(Path pdf) {
        try (var document = Loader.loadPDF(pdf.toFile())) {
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                return new Result(null, false);
            }
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            var text = new StringBuilder();
            boolean allPagesHaveText = document.getNumberOfPages() > 0;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = normalize(stripper.getText(document));
                if (pageText.isEmpty()) allPagesHaveText = false;
                if (!pageText.isEmpty()) {
                    if (!text.isEmpty()) text.append(' ');
                    text.append(pageText);
                }
            }
            if (text.isEmpty()) return new Result(null, false);
            return new Result(text.codePointCount(0, text.length()), allPagesHaveText);
        } catch (IOException e) {
            // Counting must not prevent the existing model from reading a visual PDF.
            return new Result(null, false);
        }
    }

    static String normalize(String text) {
        // Layout whitespace becomes one space. NFC counts e.g. composed Danish letters once.
        return Normalizer.normalize(text, Normalizer.Form.NFC)
                .replaceAll("(?U)\\s+", " ").strip();
    }

    public record Result(Integer characters, boolean allPagesHaveText) {
        public String markdown() {
            if (characters == null) {
                return "## Anslagsoptælling\n\n"
                        + "Anslag kunne ikke tælles fra PDF'ens tekstlag. Omfanget kræver manuel kontrol eller OCR.";
            }
            int difference = characters - CHARACTER_LIMIT;
            String comparison = difference > 0 ? difference + " tegn over"
                    : difference < 0 ? -difference + " tegn under" : "præcis på";
            return "## Anslagsoptælling\n\n"
                    + "**" + characters + " tegn inklusive mellemrum** i hele PDF'ens udtrukne tekst. "
                    + "Det er " + comparison + " grænsen på " + CHARACTER_LIMIT + " tegn.\n\n"
                    + "Optællingen samler linjeskift og gentagne mellemrum til ét mellemrum. "
                    + "Bilag, forsider, sidehoveder og sidefødder er ikke automatisk fjernet. "
                    + "Kravet gælder hovedteksten eksklusive bilag; tallet er derfor ikke alene en endelig formaliakontrol."
                    + (allPagesHaveText ? "" : "\n\n**Bemærk:** Mindst én side havde intet udtrækkeligt tekstlag. Optællingen kan være ufuldstændig.");
        }

        public String modelInstructions() {
            return "\n\nBackendens beregnede anslagsoptælling (ikke elevens egen angivelse):\n"
                    + markdown()
                    + "\nBrug dette målte tal i afsnittet Formalia i stedet for at gætte eller bruge rapportens ordtal. "
                    + "En teknisk optælling er foretaget, så skriv ikke, at backend ikke har talt tegn. "
                    + "Tallet gælder kun det udtrukne tekstlag, ikke tekst i billeder. "
                    + "Hvis bilag eller udtræksfejl påvirker konklusionen, forklar konkret afgrænsningen. "
                    + "Et totalantal over grænsen beviser ikke, at hovedteksten er for lang. "
                    + "Opfind ikke et korrigeret antal eksklusive bilag.";
        }
    }
}
