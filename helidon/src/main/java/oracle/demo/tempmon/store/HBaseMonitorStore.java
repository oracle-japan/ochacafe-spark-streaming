package oracle.demo.tempmon.store;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;

import oracle.demo.tempmon.RackInfo;

public class HBaseMonitorStore implements MonitorStore {

    private static final Logger logger = Logger.getLogger(HBaseMonitorStore.class.getName());
   
    private final String hbaseServer;
    private final String hbasePort;
    private final String hbaseTable;
    private final String family = "tempmonFamily";

    private Connection connection;

    public HBaseMonitorStore(){
        final io.helidon.config.Config appConfig = io.helidon.config.Config.create().get("hbase");
        hbaseServer = appConfig.get("server").asString().orElse("localhost");
        hbasePort = appConfig.get("port").asString().orElse("2181");
        hbaseTable = appConfig.get("table").asString().orElse("tempmon");
        createTable();
    }

    private synchronized Connection getConnection(){
        connection = Optional.ofNullable(connection).orElseGet(() -> {
            try{
                org.apache.hadoop.conf.Configuration conf = HBaseConfiguration.create();
                conf.set("hbase.zookeeper.quorum", hbaseServer);
                conf.set("hbase.zookeeper.property.clientPort", hbasePort);
                return ConnectionFactory.createConnection(conf);
            }catch(IOException e){
                throw new RuntimeException("Couldn't get connection: " + e.getMessage(), e);
            }
        });
        return connection;
    }

    private void createTable(){
        try{
            final Admin admin = getConnection().getAdmin();
            final TableName tableName = TableName.valueOf(hbaseTable);
            if(!admin.tableExists(tableName)){
                TableDescriptorBuilder tableDescBuilder = TableDescriptorBuilder.newBuilder(tableName);
                ColumnFamilyDescriptorBuilder columnDescBuilder = ColumnFamilyDescriptorBuilder
                        .newBuilder(Bytes.toBytes(family));
                        //.setBlocksize(32 * 1024)
                        //.setCompressionType(Compression.Algorithm.SNAPPY)
                        //.setDataBlockEncoding(DataBlockEncoding.NONE);
                tableDescBuilder.setColumnFamily(columnDescBuilder.build());
                admin.createTable(tableDescBuilder.build());
                logger.info("Table created.");
            }
        }catch(Exception e){
            throw new RuntimeException("Couldn't careate table: " + e.getMessage(), e);
        }
    }

    private void dropTable() throws IOException{
        final Admin admin = getConnection().getAdmin();
        final TableName tableName = TableName.valueOf(hbaseTable);
        if(admin.tableExists(tableName)){
            admin.disableTable(tableName);
            admin.deleteTable(tableName);
            logger.info("Table dropped.");
        }
    }

    @Override
    public void clear() {
        try(Table table = getConnection().getTable(TableName.valueOf(hbaseTable))){
            Scan scan = new Scan();
            ResultScanner rs = table.getScanner(scan);
            rs.forEach(result -> {
                try{
                    final byte[] id = result.getRow();
                    Delete delete = new Delete(id);
                    table.delete(delete);
                }catch(IOException e){
                    throw new RuntimeException("Couldn't delete row: " + e.getMessage(), e);
                }
            });
        }catch(Exception e){
            throw new RuntimeException("DELETE error: " + e.getMessage(), e);
        }
    }

    @Override
    public RackInfo[] getAllRackInfo() {
        final List<RackInfo> rackList = new ArrayList<>();
        try(Table table = getConnection().getTable(TableName.valueOf(hbaseTable))){
            Scan scan = new Scan();
            ResultScanner rs = table.getScanner(scan);
            rs.forEach(result -> {
                final byte[] id = result.getRow();
                final byte[] temperature = result.getValue(Bytes.toBytes(family), Bytes.toBytes("temperature"));
                final byte[] timestamp = result.getValue(Bytes.toBytes(family), Bytes.toBytes("timestamp"));
                rackList.add(new RackInfo(Bytes.toString(id), Bytes.toDouble(temperature), new Date(Bytes.toLong(timestamp))));
            });
        }catch(Exception e){
            throw new RuntimeException("SCAN error: " + e.getMessage(), e);
        }
        return rackList.toArray(new RackInfo[rackList.size()]);
    }

    @Override
    public RackInfo getRackInfo(String id) {
        try(Table table = getConnection().getTable(TableName.valueOf(hbaseTable))){
            final Get get = new Get(Bytes.toBytes(id));
            final Result result = table.get(get);
            final byte[] temperature = result.getValue(Bytes.toBytes(family), Bytes.toBytes("temperature"));
            final byte[] timestamp = result.getValue(Bytes.toBytes(family), Bytes.toBytes("timestamp"));
            return new RackInfo(id, Bytes.toDouble(temperature), new Date(Bytes.toLong(timestamp)));
        }catch(Exception e){
            throw new RuntimeException("GET error: " + e.getMessage(), e);
        }
    }

    @Override
    public RackInfo updateRackInfo(String id, RackInfo rackInfo) {
        final RackInfo info = Optional.ofNullable(rackInfo.getTimestamp()).isPresent() 
            ? rackInfo : new RackInfo(id, rackInfo.getTemperature(), new Date());
        try(Table table = getConnection().getTable(TableName.valueOf(hbaseTable))){
            final Put put = new Put(Bytes.toBytes(id));
            put.addColumn(Bytes.toBytes(family), Bytes.toBytes("temperature"), Bytes.toBytes(info.getTemperature()));
            put.addColumn(Bytes.toBytes(family), Bytes.toBytes("timestamp"), Bytes.toBytes(info.getTimestamp().getTime()));
            table.put(put);
        }catch(Exception e){
            throw new RuntimeException("PUT error: " + e.getMessage(), e);
        }
        return null;
    }

    @Override
    public void close() {
        try{
            dropTable();
        }catch(Exception e){
            logger.log(Level.WARNING, "Couldn't drop table: " + e.getMessage(), e);
        }
    }
  

}