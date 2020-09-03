package oracle.demo.tempmon;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.json.Json;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.Jsonb;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.reactive.messaging.Incoming;

import io.helidon.config.Config;
import io.helidon.messaging.connectors.kafka.KafkaMessage;

@ApplicationScoped
public class SlackAlerter {

    private static final Logger logger = Logger.getLogger(SlackAlerter.class.getName());

    private static final SimpleDateFormat iso8601format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final SimpleDateFormat myformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss XXX");

    private final Config config;
    private final boolean slackAlerterEnabled;
    private final String webHookUrl;

    private SlackAlerter() {
        config = Config.create().get("slack-alerter");
        slackAlerterEnabled = config.get("enabled").asBoolean().orElse(true);
        webHookUrl = config.get("webhook-url").asString().get();
    }

    // Kafka Connector
    // based on MicroProfile Reactive Streams Messaging
    @Incoming("from-kafka")
    public void consumeKafka(KafkaMessage<String, String> message) {
        String json = JsonUtil.gerPrettyPrint(message.getPayload());
        System.out.println("****************************************");
        System.out.println(String.format("[Received] %s(%d)%s", message.getKey(), message.getOffset().get(), json));
        System.out.println("----------------------------------------");
        try{
            if(slackAlerterEnabled) sendToSlack(message);
        }catch(Exception e){
            logger.log(Level.WARNING, "Couldn't send a message to Slack: " + e.getMessage(), e);
        }
    }


    private final Map<String, RackStateSW> savedStates = new HashMap<>();

    private void sendToSlack(KafkaMessage<String, String> message) throws ParseException {

        String key = message.getKey().get();
        String value = message.getPayload();

        // items for slack alerts
        String rackId = null;
        String status = null;
        double temperature = 0;
        Date tsDate = null;

        Jsonb jsonb = JsonbBuilder.create();
        if(key.equalsIgnoreCase("SW")){
            RackStateSW rackStateSW = jsonb.fromJson(value, RackStateSW.class);
            rackId = rackStateSW.rackId; // check the last state
            tsDate = iso8601format.parse(rackStateSW.window.end);
            long currentTime = tsDate.getTime();

            logger.info("Current state: " + rackStateSW.toString());

            // check the last state and process only if the state changes
            RackStateSW savedStateSW = savedStates.get(rackId);
            if(null == savedStateSW){ // this is the first state of the specific rack
                logger.info("No saved state.");
                savedStates.put(rackId, rackStateSW);
                return;
            }else{
                logger.info("Saved state: " + savedStateSW.toString());

                final long savedLastTime = iso8601format.parse(savedStateSW.window.end).getTime();
                if(savedLastTime > currentTime){ // currentTime is older
                    logger.info("Current state is older than the saved state.");
                    return; // no need to save the current state
                }
                savedStates.put(rackId, rackStateSW); // save the current state
                final String currentStatus = rackStateSW.status;
                final String lastStatus = savedStateSW.status;
                if(
                    currentStatus.equalsIgnoreCase("Void") ||     // no need to alert void status
                    currentStatus.equalsIgnoreCase("Transient") ||     // no need to alert transient status
                    currentStatus.equalsIgnoreCase(lastStatus) ){    // no need to alert on the same status
                        logger.info("No need to alert, the status is either Void/Transient or the same as before.");
                        return;
                }
            }
            // now the state has changed
            logger.info("The status has changed!!");
            status = rackStateSW.status;
            temperature = rackStateSW.status.equalsIgnoreCase("Warning") ? rackStateSW.minTemp : rackStateSW.maxTemp;
        }else if(key.equalsIgnoreCase("ASP")){
            RackStateASP rackStateASP = jsonb.fromJson(value, RackStateASP.class);
            rackId = rackStateASP.rackId;
            status = rackStateASP.status;
            temperature = rackStateASP.temperature;
            tsDate = rackStateASP.ts;
        }else{
            return;
        }

        // sending slack alert
        System.out.println("\n<<<<<<<<<< STATUS HAS CHANGED >>>>>>>>>>");
        System.out.println(String.format("Rack ID: %s", rackId));
        System.out.println(String.format("Status: %s", status));
        System.out.println("<<<<<<<<<< STATUS HAS CHANGED >>>>>>>>>>\n");

        if(rateLimit(3, 10 * 1000)){
            logger.warning("Rate limit hit on sending Slack");
            return;
        }

        String emoji = status.equals("Normal") ? ":information_source:" : ":warning:";
        String text = Json.createObjectBuilder()
        .add("text", "OCHaCafe Demo Alert !!")
        .add("blocks", 
          Json.createArrayBuilder()
          .add(
            Json.createObjectBuilder()
            .add("type", "section")
            .add("text", 
                Json.createObjectBuilder()
                .add("type", "mrkdwn")
                .add("text", String.format("%s *Status of %s has changed to %s (%s)* \ntime: %s\ntemperature: %.1f", 
                                                emoji, rackId, status, key, myformat.format(tsDate), temperature)
                )
            )    
          )
          .add(Json.createObjectBuilder().add("type", "divider"))
        )
        .build()
        .toString();
        logger.info("Slack message: " + text);

        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(webHookUrl);
        Response response = target.request().post(Entity.json(text));
        logger.info("Slack sent - status: " + response.getStatusInfo().toString());
    }

    private final Map<Long, Long> posts = new ConcurrentHashMap<>();
    private boolean rateLimit(int limit, long duration){ // limits upto 3 posts in last 10 seconds
        long now = System.currentTimeMillis();
        // add now
        posts.put(now, now);
        // erase old data & count posts
        final AtomicInteger count = new AtomicInteger();
        posts.keySet().stream().forEach(key -> {
            if(key + duration < now) posts.remove(key);
            else count.incrementAndGet();
        });
        if(count.get() > limit) return true;
        return false;
    }



/*
{
    "rackId": "rack-03",
    "window": {
        "start": "2020-02-19T13:55:10.000+09:00",
        "end": "2020-02-19T13:56:10.000+09:00"
    },
    "status": "NORMAL",
    "maxTemp": 95.0,
    "minTemp": 95.0
}
*/
    public static class RackStateSW{
        public String rackId;
        public RackStateWindow window;
        public String status;
        public double maxTemp;
        public double minTemp;

        public String toString(){
            return String.format("[rackId=%s, start=%s, end=%s, status=%s, max=%.1f, min=%.1f]", 
                rackId, window.start, window.end, status, maxTemp, minTemp);
        }
    }

    public static class RackStateWindow{
        public String start;
        public String end;
    }

/*
{
    "rackId": "rack-03",
    "status": "Normal",
    "ts": "2020-02-19T14:01:24.000+09:00",
    "temperature": 95.0
} 
*/
    public static class RackStateASP{
        public String rackId;
        public String status;
        public Date ts;
        public double temperature;
    }


}

/*
{
	"blocks": [
		{
			"type": "section",
			"text": {
				"type": "mrkdwn",
				"text": ":warning: :information_source: *this is bold*, and ~this is crossed out~, and <https://google.com|this is a link>"
			}
		}
	]
}
*/
