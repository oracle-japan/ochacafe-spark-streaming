package oracle.demo.tempmon.store;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.amazonaws.services.dynamodbv2.model.DeleteTableResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;

import oracle.demo.tempmon.RackInfo;

public class DynamoDBStore implements MonitorStore {

    private final AmazonDynamoDB client; // AmazonDynamoDB is thread-safe
    private final DynamoDBMapper mapper; // DynamoDBMapper is thread-safe
    private String tableName = "RackInfo";

    public DynamoDBStore(){
        final io.helidon.config.Config appConfig = io.helidon.config.Config.create().get("dynamodb");
        final boolean isLocal = appConfig.get("islocal").asBoolean().orElse(false);
        final String regions = appConfig.get("regions").asString().orElse(null);
        final String endpoint = appConfig.get("endpoint").asString().orElse("http://localhost:8000");
        final long readCapacity = appConfig.get("capacity.read").asLong().orElse(1L);
        final long writeCapacity = appConfig.get("capacity.write").asLong().orElse(1L);

        // AmazonDynamoDBClientBuilder is NOT thread-safe
        final AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard();
        if(isLocal){
            builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, regions));
        }else{
            if(Optional.ofNullable(regions).isPresent()){
                builder.withRegion(Regions.fromName(regions));
            }
        }
        client = builder.build();
        mapper = new DynamoDBMapper(client);

        if(isTableExists(tableName)) return;

        // create table
        try{
            CreateTableResult result = client.createTable(
                Arrays.asList(
                    new AttributeDefinition("rackId", ScalarAttributeType.S),
                    new AttributeDefinition("temperature", ScalarAttributeType.N)
                    ),
                tableName,
                Arrays.asList(
                    new KeySchemaElement("rackId", KeyType.HASH),
                    new KeySchemaElement("temperature", KeyType.RANGE)
                    ),
                new ProvisionedThroughput(readCapacity, writeCapacity)
                );
            String status = result.getTableDescription().getTableStatus();
            System.out.println(String.format("Table %s, status = %s", tableName, status));
            while(!status.equalsIgnoreCase("ACTIVE")){ // wait until it turns to be active
                try{
                    Thread.sleep(1000);
                }catch(InterruptedException ignore){}
                status = client.describeTable(tableName).getTable().getTableStatus();
                System.out.println(String.format("Table %s, status = %s", tableName, status));
            }
        }catch(Exception e){
            throw new RuntimeException("Couldn't create the table: " + e.getMessage(), e);
        }
    }

    private boolean isTableExists(String tableName){
        try{
            client.describeTable(tableName).getTable().getTableStatus();
        }catch(ResourceNotFoundException e){
            return false;
        }
        return true;
    }

    @Override
    public void clear() {
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        List<DynamoRackInfo> latestRackInfo = mapper.scan(DynamoRackInfo.class, scanExpression);
        mapper.batchDelete(latestRackInfo);
    }

    @Override
    public RackInfo[] getAllRackInfo() {
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression();
        List<DynamoRackInfo> latestRackInfo = mapper.scan(DynamoRackInfo.class, scanExpression);

        final List<RackInfo> rackList = new ArrayList<>();
        latestRackInfo.forEach(info -> {
            rackList.add(new RackInfo(info.getRackId(), info.getTemperature(), info.getTimestamp()));
        });
        return rackList.toArray(new RackInfo[rackList.size()]);
    }

    @Override
    public RackInfo getRackInfo(String id) {
        DynamoDBMapperConfig config = DynamoDBMapperConfig.builder()
            .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
            .build();
        return ((DynamoRackInfo)mapper.load(DynamoRackInfo.class, id, config)).toRackInfo();
    }

    @Override
    public RackInfo updateRackInfo(String id, RackInfo rackInfo) {
        mapper.save(new DynamoRackInfo(rackInfo));
        return null;
    }

    @Override
    public void close() {
        if(isTableExists(tableName)){
            DeleteTableResult deleteTableResult = client.deleteTable(tableName);
            final String status = deleteTableResult.getTableDescription().getTableStatus();
            System.out.println(String.format("Table %s, status = %s", tableName, status));
        }
    }


    /**
     *  Java Object mapping class
     */
    @DynamoDBTable(tableName="RackInfo") 
    public static class DynamoRackInfo{
        private String rackId;
        private double temperature;
        private Date timestamp;
    
        public DynamoRackInfo(){} // DO NOT DELETE !!

        public DynamoRackInfo(RackInfo rackInfo) {
            this(rackInfo.getRackId(), rackInfo.getTemperature(), rackInfo.getTimestamp());
        }

        public DynamoRackInfo(String rackId, double temperature) {
            this(rackId, temperature, new Date());
        }
    
        public DynamoRackInfo(String rackId, double temperature, Date timestamp) {
            if(!Optional.ofNullable(rackId).isPresent()) throw new IllegalArgumentException("rackId must not be null.");
            this.rackId = rackId;
            this.temperature = temperature;
            this.timestamp = timestamp;
        }

        @DynamoDBHashKey(attributeName = "rackId")
        public String getRackId() {
            return rackId;
        }
        public void setRackId(String rackId) {
            if(!Optional.ofNullable(rackId).isPresent()) throw new IllegalArgumentException("rackId must not be null.");
            this.rackId = rackId;
        }
    
        @DynamoDBRangeKey(attributeName="temperature")
        public double getTemperature() {
            return temperature;
        }
        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    
        @DynamoDBAttribute(attributeName="timestamp")
        public Date getTimestamp(){
            return timestamp;
        }
        public void setTimestamp(Date timestamp){
            this.timestamp = timestamp;
        }

        public RackInfo toRackInfo(){
            return new RackInfo(this.rackId, this.temperature, this.timestamp);
        }

    }


}

/*
CREATING - The table is being created.
UPDATING - The table is being updated.
DELETING - The table is being deleted.
ACTIVE - The table is ready for use.
INACCESSIBLE_ENCRYPTION_CREDENTIALS - The AWS KMS key used to encrypt the table in inaccessible. Table operations may fail due to failure to use the AWS KMS key. DynamoDB will initiate the table archival process when a table's AWS KMS key remains inaccessible for more than seven days.
ARCHIVING - The table is being archived. Operations are not allowed until archival is complete.
ARCHIVED - The table has been archived. See the ArchivalReason for more information.
*/