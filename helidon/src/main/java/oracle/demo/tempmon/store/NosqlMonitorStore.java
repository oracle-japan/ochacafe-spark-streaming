package oracle.demo.tempmon.store;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import oracle.demo.tempmon.RackInfo;
import oracle.nosql.driver.Consistency;
import oracle.nosql.driver.NoSQLHandle;
import oracle.nosql.driver.NoSQLHandleConfig;
import oracle.nosql.driver.NoSQLHandleFactory;
import oracle.nosql.driver.Region;
import oracle.nosql.driver.iam.SignatureProvider;
import oracle.nosql.driver.ops.GetRequest;
import oracle.nosql.driver.ops.GetResult;
import oracle.nosql.driver.ops.PutRequest;
import oracle.nosql.driver.ops.PutResult;
import oracle.nosql.driver.ops.QueryRequest;
import oracle.nosql.driver.ops.QueryResult;
import oracle.nosql.driver.ops.TableLimits;
import oracle.nosql.driver.ops.TableRequest;
import oracle.nosql.driver.ops.TableResult;
import oracle.nosql.driver.values.MapValue;

public class NosqlMonitorStore implements MonitorStore {

    private static final Logger logger = Logger.getLogger(NosqlMonitorStore.class.getName());

    private final NoSQLHandle handle; // NoSQLHandle is thread-safe

    private final String tableName = "temperature";
    private final String ddl = "create table if not exists temperature(id string, reading json, primary key(id))";
    private final String stmtDrop = "drop table if exists temperature";
    private final String querySelect = "select * from temperature";
    private final String queryDelete = "delete from temperature";

    public NosqlMonitorStore() throws IOException{

        final io.helidon.config.Config appConfig = io.helidon.config.Config.create().get("nosql");
        final String compartmentId = appConfig.get("compartment-id").asString().get();
        final Region region = Region.fromRegionId(appConfig.get("region").asString().get());
        final io.helidon.config.Config tableLimits = appConfig.get("table-limits");
        final int readUnits = tableLimits.get("read").asInt().orElse(1);
        final int writeUnits = tableLimits.get("write").asInt().orElse(1);
        final int storageGB = tableLimits.get("storage").asInt().orElse(1);

        final NoSQLHandleConfig config = new NoSQLHandleConfig(region, new SignatureProvider())
            .setDefaultCompartment(compartmentId)
            .setConsistency(Consistency.EVENTUAL);
        handle = NoSQLHandleFactory.createNoSQLHandle(config);

        final TableRequest request = new TableRequest()
            .setStatement(ddl)
            .setTableLimits(new TableLimits(readUnits, writeUnits, storageGB));
        final TableResult result = handle.doTableRequest(request, 15 * 1000,  3 * 1000);
        System.err.println(String.format("[NoSQL] table \"%s\" is %s (%d,%d,%d)", 
                    result.getTableName(), result.getTableState(), readUnits, writeUnits, storageGB));
    }

    @Override
    public void close() {
        logger.info("[NoSQL] closing table...");
        final TableRequest request = new TableRequest().setStatement(stmtDrop);
        final TableResult result = handle.doTableRequest(request, 15 * 1000,  3 * 1000);
        logger.info(String.format("[NoSQL] table \"%s\" is %s", result.getTableName(), result.getTableState()));
        handle.close();
        System.err.println("[NoSQL] closed.");
    }

    @Override
    public void clear() {
        final QueryRequest request = new QueryRequest().setStatement(queryDelete);
        do{
            /*final QueryResult queryResult =*/ handle.query(request);
        }while(!request.isDone());
    }

    @Override
    public RackInfo[] getAllRackInfo() {
        final List<RackInfo> rackList = new ArrayList<>();
        final QueryRequest request = new QueryRequest().setStatement(querySelect);
        do{
            final QueryResult queryResult = handle.query(request);
            final List<MapValue> results = queryResult.getResults();
            for(final MapValue result : results){
                final String reading = result.getString("reading"); // json string
                System.out.println("Reading: " + reading);
                rackList.add(RackInfo.fromJson(reading));
            }
        }while(!request.isDone());

        return rackList.toArray(new RackInfo[rackList.size()]);
    }

    @Override
    public RackInfo getRackInfo(final String id) {
        final GetRequest request = new GetRequest()
            .setTableName(tableName).setKey(new MapValue().put("id", id));
        final GetResult result = handle.get(request);
        final MapValue value = result.getValue();
        Optional.ofNullable(value).orElseThrow(
            () -> new RuntimeException("Failed to get - key: " + id));
        final String reading = value.getString("reading");
        return RackInfo.fromJson(reading);
    }

    @Override
    public RackInfo updateRackInfo(final String id, final RackInfo rackInfo) {
        if(!Optional.ofNullable(rackInfo.getTimestamp()).isPresent()){
            rackInfo.setTimestamp(new Date());
        }
        final PutRequest request = new PutRequest()
            .setTableName(tableName)
            .setValue(new MapValue().put("id", id).put("reading", rackInfo.toJson()));
        final PutResult result = handle.put(request);
        Optional.ofNullable(result.getVersion()).orElseThrow(
            () -> new RuntimeException("Failed to update: " + rackInfo.toString()));
        return null; // not capable to return old data
    }
    
}