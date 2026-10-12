package app.controllers;

import app.exceptions.ApiException;
import app.server.Setup;
import io.javalin.Javalin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationUploadTest {
    @TempDir Path uploads;

    @Test
    void forwardsPdfAndPromptAndCleansUpTemporaryFile() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var controller = new TemplateController((file, prompt) -> {
            assertTrue(Files.exists(file));
            assertTrue(file.normalize().startsWith(uploads));
            assertEquals("Vurder opgaven", prompt);
            try { assertEquals("%PDF-1.7\nexample", Files.readString(file)); }
            catch (Exception e) { fail(e); }
            calls.incrementAndGet();
            return List.of("Karakter: 10");
        }, uploads);
        var app = start(controller);
        try {
            var response = send(app, "Vurder opgaven", "../../elev.pdf", "%PDF-1.7\nexample");
            assertEquals(200, response.statusCode());
            assertEquals("[\"Karakter: 10\"]", response.body());
            assertEquals(1, calls.get());
            assertEmptyUploads();
        } finally {
            app.stop();
        }
    }

    @Test
    void rejectsMissingPromptMissingFileAndFakePdfWithoutCallingModel() throws Exception {
        var controller = new TemplateController((file, prompt) -> {
            fail("Invalid uploads must not call the model");
            return List.of();
        }, uploads);
        var app = start(controller);
        try {
            assertEquals(400, send(app, " ", "elev.pdf", "%PDF-1.7").statusCode());
            assertEquals(400, send(app, "Vurder", null, "").statusCode());
            assertEquals(400, send(app, "Vurder", "elev.txt", "plain text").statusCode());
            assertEquals(400, send(app, "Vurder", "elev.pdf", "fake pdf").statusCode());
            assertEquals(400, send(app, "Vurder", "elev.pdf", "").statusCode());
            assertEmptyUploads();
        } finally {
            app.stop();
        }
    }

    @Test
    void cleansUpWhenEvaluationFails() throws Exception {
        var controller = new TemplateController((file, prompt) -> {
            throw new ApiException(502, "Model unavailable");
        }, uploads);
        var app = start(controller);
        try {
            assertEquals(502, send(app, "Vurder", "elev.pdf", "%PDF-1.7").statusCode());
            assertEmptyUploads();
        } finally {
            app.stop();
        }
    }

    private Javalin start(TemplateController controller) {
        return Javalin.create(Setup::configuration)
                .exception(ApiException.class, (e, ctx) -> ctx.status(e.getStatus()).json(Map.of("message", e.getMessage())))
                .post("/api/evaluate", controller::evaluate).start(0);
    }

    private HttpResponse<String> send(Javalin app, String prompt, String filename, String content) throws Exception {
        String boundary = "RubricAITestBoundary";
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"prompt\"\r\n\r\n" + prompt + "\r\n";
        if (filename != null) {
            body += "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                    + "\"\r\nContent-Type: application/pdf\r\n\r\n" + content + "\r\n";
        }
        body += "--" + boundary + "--\r\n";
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/api/evaluate"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private void assertEmptyUploads() throws Exception {
        try (var files = Files.list(uploads)) { assertEquals(0, files.count()); }
    }
}

