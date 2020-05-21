package oracle.demo.tempmon.store;

import java.util.ArrayList;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;

import oracle.demo.tempmon.RackInfo;

public class CoherenceMonitorStore implements MonitorStore {

    private final NamedCache<String, RackInfo> cache = CacheFactory.getCache("monitor-store");

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public RackInfo[] getAllRackInfo() {
        return new ArrayList<RackInfo>(cache.values()).toArray(new RackInfo[]{});
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