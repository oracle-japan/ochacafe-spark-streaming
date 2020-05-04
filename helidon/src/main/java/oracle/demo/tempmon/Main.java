package oracle.demo.tempmon;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.microprofile.server.Server;

/**
 * The application main class.
 */
public final class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());
    
    /**
     * Cannot be instantiated.
     */
    private Main() { }

    /**
     * Application main entry point.
     * @param args command line arguments
     * @throws IOException if there are problems reading logging properties
     */
    public static void main(final String[] args) throws IOException {
        // load logging configuration
        setupLogging();

        final Config config = Config.create();
        final Config tempReporterConfig = config.get("temp-reporter");
        final Config slackAlerterConfig = config.get("slack-alerter");

        final boolean tempReporterEnabled = tempReporterConfig.get("enabled").asBoolean().orElse(true);
        final boolean slackAlerterEnabled = slackAlerterConfig.get("enabled").asBoolean().orElse(true);

        // start kafka publisher
        if(tempReporterEnabled){
            TempReporter.getInstance();

            // start the server
            final Server server = startServer();
            logger.info(String.format("Server started - host=%s, port=%d", server.host(), server.port()));

        }else{
            logger.warning("TempReporter is disabled.");
        }

        // start slack connector
        if(slackAlerterEnabled){
            SlackAlerter.getInstance();
        }else{
            logger.warning("SlackAlerter is disabled.");
        }

    }

    /**
     * Start the server.
     * @return the created {@link Server} instance
     */
    static Server startServer() {
        // Server will automatically pick up configuration from
        // microprofile-config.properties
        // and Application classes annotated as @ApplicationScoped
        return Server.create().start();
    }

    /**
     * Configure logging from logging.properties file.
     */
    private static void setupLogging() throws IOException {
        try (InputStream is = Main.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        }
    }
}
