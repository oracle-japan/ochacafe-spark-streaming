package oracle.demo.tempmon.store;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;

import org.bson.Document;

import oracle.demo.tempmon.RackInfo;

public class MongoDBStore implements MonitorStore {

    private final MongoClient client;
    private final MongoDatabase database;
    private final MongoCollection<Document> collection;

    public MongoDBStore(){
        final io.helidon.config.Config appConfig = io.helidon.config.Config.create().get("mongo");
        final String url = appConfig.get("url").asString().orElse("mongodb://localhost:27017");
        final String dbName = appConfig.get("database").asString().orElse("tempmon");
        final String collName = appConfig.get("collection").asString().orElse("temps");

        client = MongoClients.create(url);
        database = client.getDatabase(dbName);
        collection = database.getCollection(collName);
        long counts = collection.countDocuments();
        System.out.println(String.format("MongoDB: database=%s, collection=%s, #documents=%d", dbName, collName, counts));
    }

    @Override
    public void clear() {
        collection.deleteMany(new Document());
    }

    @Override
    public RackInfo[] getAllRackInfo() {
        final List<RackInfo> rackList = new ArrayList<>();

        FindIterable<Document> iterator = collection.find();
        MongoCursor<Document> cursor = iterator.iterator();
        cursor.forEachRemaining(i -> {
            //System.out.println("json: " + i.toJson());
            RackInfo rackInfo = RackInfo.fromJson(i.toJson());
            rackList.add(rackInfo);
        });
        return rackList.toArray(new RackInfo[rackList.size()]);
    }

    @Override
    public RackInfo getRackInfo(String id) {
        Document doc = collection.find(Filters.eq("rackId", id)).first();
        return RackInfo.fromJson(doc.toJson());
    }

    @Override
    public RackInfo updateRackInfo(String id, RackInfo rackInfo) {
        if(!Optional.ofNullable(rackInfo.getTimestamp()).isPresent()){
            rackInfo.setTimestamp(new Date());
        }
        Document doc = 
            new Document("rackId", rackInfo.getRackId())
            .append("temperature", rackInfo.getTemperature())
            .append("timestamp", rackInfo.getTimestampStr());

        UpdateOptions option = new UpdateOptions().upsert(true);
        collection.updateOne(Filters.eq("rackId", id), new Document("$set", doc), option);            
        return null;
    }

    @Override
    public void close() {
        if(Optional.ofNullable(collection).isPresent()) collection.drop();
        if(Optional.ofNullable(client).isPresent())     client.close();
    }


}

