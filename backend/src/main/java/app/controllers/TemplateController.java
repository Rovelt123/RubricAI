package app.controllers;

import app.exceptions.ApiException;
import app.services.TemplateService;
import app.utils.Utils;
import io.javalin.apibuilder.EndpointGroup;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

import static io.javalin.apibuilder.ApiBuilder.post;

public class TemplateController {
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;

    @FunctionalInterface
    public interface Evaluator {
        List<String> evaluate(Path file, String prompt);
    }

    private final Evaluator evaluator;
    private final Path uploadDirectory;

    public TemplateController(Evaluator evaluator, Path uploadDirectory) {
        this.evaluator = evaluator;
        this.uploadDirectory = uploadDirectory;
    }

    public static EndpointGroup registerRoutes() {
        TemplateController controller = new TemplateController(
                (file, prompt) -> new TemplateService().evaluate(file, prompt),
                Path.of(Utils.getPropertyValue("UPLOAD_DIR", "config.properties")));
        return () -> post("/evaluate", controller::evaluate);
    }

    public void evaluate(Context ctx) throws IOException {
        String prompt = ctx.formParam("prompt");
        if (prompt == null || prompt.isBlank()) {
            throw new ApiException(400, "Skriv en besked til vurderingen.");
        }
        if (prompt.length() > 10000) {
            throw new ApiException(400, "Beskeden må højst indeholde 10.000 tegn.");
        }
        UploadedFile file = ctx.uploadedFile("file");
        if (file == null || !file.filename().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new ApiException(400, "Upload en PDF i feltet 'file'.");
        }
        if (file.size() == 0 || file.size() > MAX_FILE_SIZE) {
            throw new ApiException(400, "PDF-filen skal være mellem 1 byte og 20 MB.");
        }

        Files.createDirectories(uploadDirectory);
        Path temporaryFile = Files.createTempFile(uploadDirectory, "assignment-", ".pdf");
        try {
            try (var input = file.content()) {
                Files.copy(input, temporaryFile, StandardCopyOption.REPLACE_EXISTING);
            }
            try (var input = Files.newInputStream(temporaryFile)) {
                String header = new String(input.readNBytes(5), StandardCharsets.US_ASCII);
                if (!header.equals("%PDF-")) {
                    throw new ApiException(400, "Filen har ikke et gyldigt PDF-header.");
                }
            }
            ctx.json(evaluator.evaluate(temporaryFile, prompt.trim()));
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }
}
