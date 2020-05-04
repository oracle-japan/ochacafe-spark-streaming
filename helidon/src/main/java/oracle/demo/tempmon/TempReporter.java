package oracle.demo.tempmon;

import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import io.helidon.config.Config;

@ApplicationScoped
public class TempReporter {

    private static final Logger logger = Logger.getLogger(TempReporter.class.getName());

    private static TempReporter reporter;

    public static synchronized TempReporter getInstance() {
        return Optional.ofNullable(reporter).orElse(new TempReporter());
    }

    private final Config config = Config.create().get("temp-reporter");
    private final AtomicBoolean fToGo = new AtomicBoolean(true);
    private final ExecutorService es;

    private TempReporter() {

        es = Executors.newSingleThreadExecutor();
        es.submit(() -> {
            final TempMonitor monitor = TempMonitor.getInstance();
            final KafkaProducer<String, String> producer = createKafkaProducer(); 
            final String topic = config.get("kafka.topic").asString().get();
            final long pollingInterval = config.get("polling-interval").asLong().orElse(5000L);

            logger.info("Start polling");
            while(fToGo.get()){
                if(!monitor.isPending()){
                    try{
                        logger.fine("Polling monitor...");
    
                        for(RackInfo rackInfo : monitor.getAllRackInfo()){
                            rackInfo.updateTimestamp();
                            logger.fine("Rack: " + rackInfo.toJson());
                            final ProducerRecord<String, String> record = 
                                new ProducerRecord<String, String>(topic, rackInfo.getRackId(), rackInfo.toJson());
                            producer.send(record, (m,e) -> {
                                if(Optional.ofNullable(e).isPresent()){
                                    logger.severe(String.format("Failed to publish %s - %s", rackInfo.toJson(), e.getMessage()));
                                }else{
                                    print(m, rackInfo);
                                }
                            });
                        }
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                try{
                    Thread.sleep(pollingInterval);
                }catch(InterruptedException ie){}
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stop();
        }));

    }

    private void print(RecordMetadata m, RackInfo rackInfo){
        //logger.info(String.format("Message sent, partition: %d - %s", m.partition(), rackInfo.toJson()));
        System.out.println("****************************************");
        System.out.println(String.format("[Sent] Offset: %d", m.offset()));
        System.out.println(String.format("Rack: %s", rackInfo.getRackId()));
        System.out.println(String.format("Temerature: %.1f", rackInfo.getTemperature()));
        System.out.println(String.format("Timestamp: %s", rackInfo.getTimestampStr()));
        System.out.println("----------------------------------------");
    }


    private void stop(){
        fToGo.set(false);
        System.err.println("\nWaiting for KafkaPublisher to be terminated...");
        try {
            es.shutdown();
            es.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {}
        System.err.println("KafkaPublisher stopped.");
    }

    private KafkaProducer<String, String> createKafkaProducer(){
        final String streamingServer;
        final String tenantName;
        final String userName;
        final String poolId;
        final String authToken;
        final Config kafkaConfig = config.get("kafka");

        streamingServer = kafkaConfig.get("streaming-server").asString().get();
        tenantName = kafkaConfig.get("tenant-name").asString().get();
        userName = kafkaConfig.get("user-name").asString().get();
        poolId = kafkaConfig.get("pool-id").asString().get();
        authToken = kafkaConfig.get("auth-token").asString().get();

        final String saslJaasConfig = 
            "org.apache.kafka.common.security.plain.PlainLoginModule " +
            "required username=\"" + tenantName + "/" + userName + "/" + poolId + "\" password=\"" + authToken + "\";";
    
        final Properties prop = new Properties();
        prop.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
        prop.put("sasl.mechanism", "PLAIN");
        prop.put("sasl.jaas.config", saslJaasConfig);
        prop.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, streamingServer);
        prop.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        prop.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        prop.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1024 * 1024); // limit request size to 1MB
        prop.put(ProducerConfig.RETRIES_CONFIG, 5); // retries on transient errors and load balancing disconnection
    
        return new KafkaProducer<String, String>(prop);
    }    

}

