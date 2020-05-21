package oracle.demo.tempmon.store;

import java.util.ArrayList;
import java.util.List;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;

import oracle.demo.tempmon.RackInfo;

public class CoherenceMonitorStore implements MonitorStore {

    //private static final Logger logger = Logger.getLogger(MapMonitorStore.class.getName());
   
    private final NamedCache<String, RackInfo> cache = CacheFactory.getCache("monitor-store");

    public CoherenceMonitorStore(){
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public RackInfo[] getAllRackInfo() {
        final List<RackInfo> rackList = new ArrayList<>();
        cache.values().forEach(e -> rackList.add(e));
        return rackList.toArray(new RackInfo[rackList.size()]);
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
  

}