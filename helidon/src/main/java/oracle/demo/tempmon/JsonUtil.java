
package oracle.demo.tempmon;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;

public class JsonUtil {

    private static final Map < String, Boolean > config = new HashMap < String, Boolean > ();
    private static JsonWriterFactory jwf;// = Json.createWriterFactory(config);
    
    static{
        config.put(JsonGenerator.PRETTY_PRINTING, true);
        jwf = Json.createWriterFactory(config);
    }

    public static String gerPrettyPrint(String str) {
    
        JsonReader jsonReader = Json.createReader(new StringReader(str));
        JsonStructure json = jsonReader.read();
        StringWriter sw = new StringWriter();
    
        try (JsonWriter jsonWriter = jwf.createWriter(sw)) {
            jsonWriter.write(json);
            return sw.toString();
        }
    }
}