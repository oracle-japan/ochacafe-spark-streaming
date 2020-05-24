package oracle.demo.tempmon;

import java.util.Date;
import java.util.Optional;
import java.text.SimpleDateFormat;
import java.util.Collections;

import javax.json.Json;
import javax.json.JsonBuilderFactory;

import org.jboss.weld.exceptions.IllegalArgumentException;

public class RackInfo {

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static final SimpleDateFormat iso8601format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    private String rackId;
    private double temperature;
    private Date timestamp;

    public RackInfo(){} // DO NOT DELETE !!

    public RackInfo(String rackId, double temperature) {
        this(rackId, temperature, new Date());
    }

    public RackInfo(String rackId, double temperature, Date timestamp) {
        if(!Optional.ofNullable(rackId).isPresent()) throw new IllegalArgumentException("rackId must not be null.");
        this.rackId = rackId;
        this.temperature = temperature;
        this.timestamp = timestamp;
    }

    public String getRackId() {
        return rackId;
    }

    public void setRackId(String rackId) {
        if(!Optional.ofNullable(rackId).isPresent()) throw new IllegalArgumentException("rackId must not be null.");
        this.rackId = rackId;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public Date getTimestamp(){
        return timestamp;
    }

    public String getTimestampStr(){
        return iso8601format.format(timestamp);
    }

    public void setTimestamp(Date timestamp){
        this.timestamp = timestamp;
    }

    public void updateTimestamp(){
        this.timestamp = new Date();
    }

    public String toString() {
        return String.format("[rackId=%s, temperature=%s, timestamp=%s]", rackId, Double.toString(temperature), iso8601format.format(timestamp));
    }

    public String toJson() {
        return JSON.createObjectBuilder()
            .add("rackId", rackId)
            .add("temperature", temperature)
            .add("timestamp", iso8601format.format(timestamp))
            .build()
            .toString();
    }

}