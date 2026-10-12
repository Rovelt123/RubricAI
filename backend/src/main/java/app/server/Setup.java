package app.server;

import app.enums.Role;
import app.exceptions.ApiException;
import io.javalin.Javalin;
import io.javalin.config.JavalinConfig;
import lombok.Getter;
import io.javalin.http.HttpResponseException;
import io.javalin.config.SizeUnit;
import java.util.Map;


@Getter
public class Setup {

    private final Javalin app;

    // ________________________________________________________

    public Setup(int port) {

        this.app = Javalin.create(Setup::configuration)
                .start(port);
    }

    // ________________________________________________________

    public static void configuration(JavalinConfig config) {
        config.showJavalinBanner = false;
        config.http.maxRequestSize = 21L * 1024 * 1024;
        config.jetty.multipartConfig.maxFileSize(20, SizeUnit.MB);
        config.jetty.multipartConfig.maxTotalRequestSize(21, SizeUnit.MB);
        config.bundledPlugins.enableRouteOverview("/routes", Role.ANYONE);



        config.bundledPlugins.enableCors(cors ->
                cors.addRule(rule -> {
                    rule.anyHost();
                })
        );
        config.bundledPlugins.enableHttpAllowedMethodsOnRoutes();
        //TODO: Add more configs maybe???
    }

    // ________________________________________________________

    public void initialize(){

        app.exception(ApiException.class, (e, ctx) -> {
            System.out.println(e.getMessage());
            System.out.println(e.getStatus());
            ctx.status(e.getStatus()).json(Map.of("message", e.getMessage()));
        });

        // Gives me error message
        app.exception(HttpResponseException.class, (e, ctx) ->
                ctx.status(e.getStatus()).json(Map.of("message", e.getMessage())));
        app.exception(Exception.class, (e, ctx) -> {
            org.slf4j.LoggerFactory.getLogger(Setup.class).error("Vurderingen fejlede ({})", e.getClass().getSimpleName());
            ctx.status(500).json(Map.of("message", "Vurderingen kunne ikke gennemføres. Kontrollér backendens konfiguration, API-adgang og PDF-filer, og prøv igen."));
        });


        app.unsafeConfig().router.apiBuilder(Routing.registerRoutes());
    }

    // ________________________________________________________

    // Should it ever end? It has been added to end the session!
    public void endSession() {
        if (app != null) app.stop();
    }
}
