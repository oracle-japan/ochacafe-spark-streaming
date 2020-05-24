package oracle.demo.tempmon.store;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import com.tangosol.io.WriteBuffer.BufferOutput;
import com.tangosol.io.pof.PofReader;
import com.tangosol.io.pof.PofWriter;
import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;

import oracle.demo.tempmon.RackInfo;

public class CoherenceMonitorStore implements MonitorStore {

    private final NamedCache<String, RackInfo> cache = CacheFactory.getCache("monitor-store");

    public CoherenceMonitorStore(){
        System.out.println(String.format("[CoherenceMonitorStore] %s", cache.isActive() ? "ACTIVE" : "NOT ACTIVE"));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public RackInfo[] getAllRackInfo() {
        return new ArrayList<RackInfo>(cache.values()).toArray(new RackInfo[] {});
    }

    @Override
    public RackInfo getRackInfo(String id) {
        return cache.get(id);
    }

    @Override
    public RackInfo updateRackInfo(String id, RackInfo rackInfo) {
        return cache.put(id, rackInfo);
    }

    @Override
    public void close() {
        cache.close();
    }

    /* POF serializer for RackInfo - see pof-config.xml 
     *   private String rackId;
     *   private double temperature;
     *   private Date timestamp;
    */
    public static class RackInfoPofSerializer implements com.tangosol.io.pof.PofSerializer {

        @Override
        public Object deserialize(PofReader reader) throws IOException {
            final String rackId = reader.readString(0);
            final double temperature = reader.readDouble(1);
            final Date timestamp = reader.readDate(2);
            reader.readRemainder();

            return new RackInfo(rackId, temperature, timestamp);
        }

        @Override
        public void serialize(PofWriter writer, Object obj) throws IOException {
            RackInfo rackInfo = (RackInfo)obj;
            writer.writeString(0, rackInfo.getRackId());
            writer.writeDouble(1, rackInfo.getTemperature());
            writer.writeDate(2, rackInfo.getTimestamp());
            writer.writeRemainder(null);
        }

    }

}