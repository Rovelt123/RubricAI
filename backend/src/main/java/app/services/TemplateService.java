package app.services;

import app.utils.Utils;
import app.exceptions.ApiException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FilePurpose;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputFile;
import com.openai.models.responses.ResponseInputItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TemplateService {

    private final String rubric = Utils.getPropertyValue("RUBRIC", "config.properties");
    private final String systemPromtPath = Utils.getPropertyValue("SYSTEMPROMT", "config.properties");
    private final String model = Utils.getPropertyValue("MODEL", "config.properties");
    private final String systemPromptText;

    private final OpenAIClient client;

    public TemplateService() {

        String api = Utils.getPropertyValue("API", "config.properties");
        String key = Utils.getPropertyValue("API_KEY", "config.properties");

        if (key.isBlank() || key.equals("MY_KEY_HERE") || key.equals("replace-with-your-api-key")) {
            throw new ApiException(503, "Angiv API_KEY i backendens konfiguration.");
        }
        if (!Files.isRegularFile(Path.of(rubric))) {
            throw new ApiException(503, "Rubric.pdf mangler. Kontrollér RUBRIC i backendens konfiguration.");
        }

        client = OpenAIOkHttpClient.builder()
            .apiKey(key)
            .baseUrl(api)
            .build();


        try {
            systemPromptText = Files.readString(Path.of(systemPromtPath));
        } catch (IOException e) {
            throw new ApiException(503, "Systemprompten kunne ikke læses. Kontrollér SYSTEMPROMT i backendens konfiguration.");
        }
        if (systemPromptText.isBlank()) {
            throw new ApiException(503, "Systemprompten er tom.");
        }
    }

    public ArrayList<String> evaluate(Path uploadedFilePath, String prompt) {

        ArrayList<String> answer = new ArrayList<>();
        var characterCount = new PdfCharacterCountService().count(uploadedFilePath);

        List<String> uploadedIds = new ArrayList<>();
        try {
        var uploadedFile = client.files().create(
            FileCreateParams.builder()
                .file(uploadedFilePath)
                .purpose(FilePurpose.USER_DATA)
                .build()
        );

        uploadedIds.add(uploadedFile.id());
        var rubricFile = client.files().create(
            FileCreateParams.builder()
                .file(Path.of(rubric))
                .purpose(FilePurpose.USER_DATA)
                .build()
        );

        uploadedIds.add(rubricFile.id());
        var response = client.responses().create(
            ResponseCreateParams.builder()
                .model(model)

                .instructions(systemPromptText + characterCount.modelInstructions())

                .inputOfResponse(
                    List.of(
                        ResponseInputItem.ofMessage(
                            ResponseInputItem.Message.builder()
                                .role(ResponseInputItem.Message.Role.USER)

                                .addInputTextContent("Elevopgave (materiale til vurdering):")

                                .addContent(
                                    ResponseInputFile.builder()
                                        .fileId(uploadedFile.id())
                                        .build()
                                )

                                // Rubric
                                .addInputTextContent("Vurderingsmateriale (rubric):")
                                .addContent(
                                    ResponseInputFile.builder()
                                        .fileId(rubricFile.id())
                                        .build()
                                )

                                .addInputTextContent(prompt)
                                .build()
                        )
                    )
                )
                .build()
        );

        response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .forEach(text -> answer.add(text.text()));

        if (answer.isEmpty()) {
            throw new ApiException(502, "Modellen returnerede ingen vurdering. Prøv igen.");
        }
        answer.addFirst(characterCount.markdown());
        return answer;
        } finally {
            for (String id : uploadedIds) {
                try {
                    client.files().delete(id);
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(TemplateService.class)
                            .warn("Kunne ikke slette en midlertidig fil hos modeludbyderen.");
                }
            }
        }
    }


}
