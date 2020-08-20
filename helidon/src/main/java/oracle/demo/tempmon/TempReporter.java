package oracle.demo.tempmon;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;

import io.helidon.common.configurable.ScheduledThreadPoolSupplier;
import io.helidon.config.Config;
import io.helidon.messaging.connectors.kafka.KafkaMessage;

@ApplicationScoped
public class TempReporter {

    private static final Logger logger = Logger.getLogger(TempReporter.class.getName());

    private final boolean tempReporterEnabled = Config.create().get("temp-reporter.enabled").asBoolean().orElse(true);
    private final Config config = Config.create().get("temp-reporter");

    //private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService ses = ScheduledThreadPoolSupplier.builder().threadNamePrefix("helidon-scheduler-").build().get();

    //private final SubmissionPublisher<KafkaMessage<String, String>> publisher 
    //    = new SubmissionPublisher<>(ForkJoinPool.commonPool(), Flow.defaultBufferSize());
    private final SubmissionPublisher<KafkaMessage<String, String>> publisher = new SubmissionPublisher<>();

    @Outgoing("to-kafka")
    public Publisher<KafkaMessage<String, String>> preparePublisher() {
        return ReactiveStreams
                .fromPublisher(FlowAdapters.toPublisher(publisher))
                .buildRs();
    }

    private void submit(RackInfo rackInfo) {
        logger.fine(String.format("Sending message: %s", rackInfo.toString()));
        logger.fine("Estimated maximum lag: " + publisher.estimateMaximumLag());

        final long estimateMinimumDemand = publisher.estimateMinimumDemand();
        logger.fine("Estimated minimum demand: " + estimateMinimumDemand);
        if(estimateMinimumDemand <= 0){
            logger.warning(String.format("You are sending a message while estimateMinimumDemand is %d which is <= 0.", estimateMinimumDemand));
        }

        publisher.submit(KafkaMessage.of(rackInfo.getRackId(), rackInfo.toJson(), () -> {
            logger.fine(String.format("Ack received: %s", rackInfo.toString()));
            print(rackInfo);
            return CompletableFuture.completedFuture(null);
        }));
    }

    public TempReporter() {
        if(!tempReporterEnabled) return;

        final TempMonitor monitor = TempMonitor.getInstance();
        final long pollingInterval = config.get("polling-interval").asLong().orElse(5000L);
        logger.fine("Max buffer capacity of publisher: " + publisher.getMaxBufferCapacity());

        logger.info("Start polling");
        ses.scheduleAtFixedRate(() -> {
            if(!monitor.isPending()){
                logger.fine("Polling monitor...");
                for(RackInfo rackInfo : monitor.getAllRackInfo()){
                    rackInfo.updateTimestamp();
                    logger.info("Rack: " + rackInfo.toJson());
                    submit(rackInfo);
                }
            }
        }, pollingInterval, pollingInterval, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stop();
        }));

    }

    private void stop(){
        System.err.println("\nWaiting for KafkaPublisher to be terminated...");
        try {
            ses.shutdown();
            ses.awaitTermination(10, TimeUnit.SECONDS);
            publisher.close();
        } catch (InterruptedException e) {}
        System.err.println("KafkaPublisher stopped.");
    }

    private void print(RackInfo rackInfo){
        System.out.println("****************************************");
        System.out.println(String.format("Rack: %s", rackInfo.getRackId()));
        System.out.println(String.format("Temerature: %.1f", rackInfo.getTemperature()));
        System.out.println(String.format("Timestamp: %s", rackInfo.getTimestampStr()));
        System.out.println("----------------------------------------");
    }

    // for debugging use
    private void offer(RackInfo rackInfo) {
        logger.fine(String.format("Sending message: %s", rackInfo.toString()));
        logger.fine("Estimated maximum lag: " + publisher.estimateMaximumLag());
        logger.fine("Estimated miinimum demand: " + publisher.estimateMinimumDemand());

        final int lag = publisher.offer(
            KafkaMessage.of(rackInfo.getRackId(), rackInfo.toJson(),() -> {
                logger.fine(String.format("Ack received: %s", rackInfo.toString()));
                print(rackInfo);
                return CompletableFuture.completedFuture(null);
            }),
            10, TimeUnit.SECONDS, 
            (subscriber, message) -> {
                logger.warning(String.format("publish offer on drop: %s", message.getPayload()));
                subscriber.onError(new RuntimeException("drop item:[" + message.getPayload() + "]"));
                return false; // no retry
            });
        if (lag < 0) {
            logger.warning(String.format("drops: %d", lag * -1));
        } else {
            logger.fine(String.format("lag: %d", lag));
        }
    }

}

