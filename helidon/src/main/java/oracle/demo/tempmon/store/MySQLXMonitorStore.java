package oracle.demo.tempmon.store;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.mysql.cj.xdevapi.Collection;
import com.mysql.cj.xdevapi.DocResult;
import com.mysql.cj.xdevapi.Schema;
import com.mysql.cj.xdevapi.Session;
import com.mysql.cj.xdevapi.SessionFactory;
import com.tangosol.dev.compiler.Info;

import oracle.demo.tempmon.RackInfo;

public class MySQLXMonitorStore implements MonitorStore {

    private final Session session;
    private final Schema schema;
    private final Collection collection;

    public MySQLXMonitorStore(){
        final io.helidon.config.Config appConfig = io.helidon.config.Config.create().get("mysqlx");
        final String url = appConfig.get("url").asString().orElse("mysqlx://127.0.0.1:33060/demo?user=oracle&password=mysql");
        final String database = appConfig.get("database").asString().orElse("demo");
        session = new SessionFactory().getSession(url);
        schema = session.getSchema(database);
        collection = schema.createCollection("temperature", true);
    }


    @Override
    public void clear() {
        collection.remove("_id like :id").bind("id", "%").execute();
    }

    @Override
    public RackInfo[] getAllRackInfo() {
        final List<RackInfo> rackList = new ArrayList<>();

        DocResult docs = collection.find().execute();
        docs.forEach(doc -> rackList.add(RackInfo.fromJson(doc.toString())));
        return rackList.toArray(new RackInfo[rackList.size()]);
    }

    @Override
    public RackInfo getRackInfo(String id) {
        DocResult docs = collection.find("_id like :id").limit(1).bind("id", id).execute();
        return RackInfo.fromJson(docs.fetchOne().toString());
    }

    @Override
    public RackInfo updateRackInfo(String id, RackInfo rackInfo) {
        final RackInfo info = Optional.ofNullable(rackInfo.getTimestamp()).isPresent() 
            ? rackInfo : new RackInfo(id, rackInfo.getTemperature(), new Date());
        collection.addOrReplaceOne(id, info.toJson());
        return null;
    }

    @Override
    public void close() {
        session.close();
    }


}