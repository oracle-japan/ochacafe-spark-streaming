package oracle.demo.tempmon;

import java.util.Date;
import java.util.Optional;
import java.io.StringReader;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.bind.annotation.JsonbTransient;

import org.jboss.weld.exceptions.IllegalArgumentException;

public class RackInfo{

    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());

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

    @JsonbTransient
    public String getTimestampStr(){
        return timestamp.toInstant()
                .atZone(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        //return timestamp.toInstant().atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.MILLIS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public void setTimestamp(Date timestamp){
        this.timestamp = timestamp;
    }

    public void updateTimestamp(){
        this.timestamp = new Date();
    }

    public String toString() {
        return String.format("[rackId=%s, temperature=%s, timestamp=%s]", rackId, Double.toString(temperature), getTimestampStr());
    }

    public String toJson() {
        return JSON.createObjectBuilder()
            .add("rackId", rackId)
            .add("temperature", temperature)
            .add("timestamp", getTimestampStr())
            .build()
            .toString();
    }

    public static RackInfo fromJson(String json){
        //System.out.println("json: " + json);
        JsonReader jsonReader = Json.createReader(new StringReader(json));
        JsonObject jobj = jsonReader.readObject();
        return new RackInfo(
                jobj.getString("rackId"),
                jobj.getJsonNumber("temperature").doubleValue(),
                Date.from(ZonedDateTime.parse(jobj.getString("timestamp")).toInstant())
            );
    }


}