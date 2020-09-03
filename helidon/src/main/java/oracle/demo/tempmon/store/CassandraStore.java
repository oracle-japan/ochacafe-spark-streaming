package oracle.demo.tempmon.store;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import oracle.demo.tempmon.RackInfo;

public class CassandraStore implements MonitorStore {

    private static final String StmtCreateKeyspace = "CREATE KEYSPACE IF NOT EXISTS tempmon WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1}";
    private static final String StmtCreateTable = "CREATE TABLE IF NOT EXISTS tempmon.monitor_store (id varchar PRIMARY KEY, temperature double, ts timestamp)";
    private static final String StmtUpdate = "UPDATE tempmon.monitor_store SET temperature = %f, ts = '%s' where id = '%s'";
    private static final String StmtSelectAll = "SELECT * FROM tempmon.monitor_store";
    private static final String StmtSelect = "SELECT * FROM tempmon.monitor_store where id = '%s'";
    private static final String StmtDelete = "DELETE FROM tempmon.monitor_store where id = '%s'";
    private static final String StmtTruncate = "TRUNCATE TABLE tempmon.monitor_store";

    private static final String StmtDropTable = "DROP TABLE tempmon.monitor_store";
    private static final String StmtDropKeyspace = "DROP KEYSPACE tempmon";

    private final CqlSession session; // CqlSession is thread-safe !!

    public CassandraStore() {
        // you can update config by application.conf located in the root of classpath 
        session = CqlSession.builder().build();
        session.execute(StmtCreateKeyspace);
        session.execute(StmtCreateTable);
    }

    @Override
    public void clear() {
        session.execute(StmtTruncate);
        // or if you like...
        //session.execute(StmtDropTable);
        //session.execute(StmtDropKeyspace);
    }

    @Override
    public RackInfo[] getAllRackInfo() {
        final List<RackInfo> rackList = new ArrayList<>();
        ResultSet rs = session.execute(StmtSelectAll);
        rs.forEach(row -> {
            rackList.add(new RackInfo(row.getString("id"), row.getDouble("temperature"), Date.from(row.getInstant("ts"))));
        });
        return rackList.toArray(new RackInfo[rackList.size()]);
    }

    @Override
    public RackInfo getRackInfo(String id) {
        Row row = session.execute(String.format(StmtSelect, id)).one();
        return new RackInfo(row.getString("id"), row.getDouble("temperature"), Date.from(row.getInstant("ts")));
    }

    @Override
    public RackInfo updateRackInfo(String id, RackInfo rackInfo) {
        if(!Optional.ofNullable(rackInfo.getTimestamp()).isPresent()) rackInfo.setTimestamp(new Date());
        // this is actually an upsert operation
        session.execute(String.format(StmtUpdate, rackInfo.getTemperature(), rackInfo.getTimestampStr(), id));
        return null;
    }

    @Override
    public void close() {
        if(Optional.ofNullable(session).isPresent())    session.close();
    }


}

