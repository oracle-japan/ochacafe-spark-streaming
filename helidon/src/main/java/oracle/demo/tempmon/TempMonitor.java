package oracle.demo.tempmon;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class TempMonitor {

    private static final Logger logger = Logger.getLogger(TempMonitor.class.getName());

    private static TempMonitor monitor;

    public static synchronized TempMonitor getInstance() {
        monitor = Optional.ofNullable(monitor).orElse(new TempMonitor());
        return monitor;
    }

    /////

    private final AtomicBoolean pending = new AtomicBoolean(false);

    private final ConcurrentHashMap<String, RackInfo> racks = new ConcurrentHashMap<>();

    private TempMonitor() {
    }

    public boolean isPending(){
        return pending.get();
    }

    public void setPending(boolean pending){
        this.pending.set(pending);
    }

    public void clear(){
        racks.clear();
    }

    public RackInfo[] getAllRackInfo(){
        final List<RackInfo> rackList = new ArrayList<>();
        racks.forEachValue(1, (rackInfo) -> rackList.add(copy(rackInfo)));
        return rackList.toArray(new RackInfo[rackList.size()]);
    }

    public RackInfo getRackInfo(String id){
        return copy(racks.get(id));
    }

    public RackInfo updateRackInfo(RackInfo rackInfo){
        if(!Optional.ofNullable(rackInfo.getTimestamp()).isPresent()){
            rackInfo.setTimestamp(new Date());
        }
        logger.finer("Updating: " + rackInfo);
        return racks.put(rackInfo.getRackId(), rackInfo);
    }

    private RackInfo copy(RackInfo rackInfo) {
        return new RackInfo(rackInfo.getRackId(), rackInfo.getTemperature(), rackInfo.getTimestamp());
    }

}