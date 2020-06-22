package oracle.demo.tempmon.store;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

import io.helidon.config.Config;

public class MonitorStoreFactory extends AbstractMonitorStoreFactory {

    private static final Logger logger = Logger.getLogger(MonitorStoreFactory.class.getName());

    private static MonitorStore cache;

    private final Config config = Config.create().get("monitor-store");
    private final String type = config.asString().orElse("map");

    public synchronized static MonitorStore create() {
        return new MonitorStoreFactory().newMonitorStore();
    }

    private MonitorStoreFactory() {
    }

    @Override
    MonitorStore newMonitorStore() {
        cache = Optional.ofNullable(cache).orElseGet(() -> {
            if (type.equalsIgnoreCase("map")) {
                return new MapMonitorStore();
            }else if(type.equalsIgnoreCase("nosql")) {
                try{
                    return new NosqlMonitorStore();
                }catch(IOException e){
                    throw new RuntimeException("Couldn't instanciate: " + e.getMessage());
                }
            }else if(type.equalsIgnoreCase("redis")) {
                return new RedisMonitorStore();
            }else if(type.equalsIgnoreCase("coherence")) {
                return new CoherenceMonitorStore();
            }else if(type.equalsIgnoreCase("dynamodb")) {
                return new DynamoDBStore();
            }else{
                logger.warning(String.format("Bad store type: '%s' - continue with MapMonitorStore", type));
                return new MapMonitorStore();
            }
        });
        return cache;
    }
    
}