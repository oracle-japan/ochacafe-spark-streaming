package oracle.demo.tempmon.store;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import oracle.demo.tempmon.RackInfo;

public class MapMonitorStore implements MonitorStore {

    //private static final Logger logger = Logger.getLogger(MapMonitorStore.class.getName());
   
    private final static ConcurrentHashMap<String, RackInfo> racks = new ConcurrentHashMap<>();

    @Override
    public void clear() {
        racks.clear();
    }

    @Override
    public RackInfo[] getAllRackInfo() {
        final List<RackInfo> rackList = new ArrayList<>();
        racks.forEachValue(1, (rackInfo) -> rackList.add(copy(rackInfo)));
        return rackList.toArray(new RackInfo[rackList.size()]);
    }

    @Override
    public RackInfo getRackInfo(String id) {
        return copy(racks.get(id));
    }

    @Override
    public RackInfo updateRackInfo(String id, RackInfo rackInfo) {
        final RackInfo info = Optional.ofNullable(rackInfo.getTimestamp()).isPresent() 
            ? rackInfo : new RackInfo(id, rackInfo.getTemperature(), new Date());
        return racks.put(id, info);
    }

    private RackInfo copy(RackInfo rackInfo) {
        return new RackInfo(rackInfo.getRackId(), rackInfo.getTemperature(), rackInfo.getTimestamp());
    }

    @Override
    public void close() {
        // do nothing
    }
  

}