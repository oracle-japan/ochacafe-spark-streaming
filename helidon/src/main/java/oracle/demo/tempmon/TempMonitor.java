package oracle.demo.tempmon;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import oracle.demo.tempmon.store.MonitorStore;
import oracle.demo.tempmon.store.MonitorStoreFactory;

public class TempMonitor {

    private static TempMonitor monitor;

    public static synchronized TempMonitor getInstance() {
        monitor = Optional.ofNullable(monitor).orElse(new TempMonitor());
        return monitor;
    }

    /////

    private final AtomicBoolean pending = new AtomicBoolean(true);

    private final MonitorStore store = MonitorStoreFactory.create();

    private TempMonitor() {
        store.clear();
        pending.set(false);
    }

    public void close(){
        store.close();
    }

    public boolean isPending(){
        return pending.get();
    }

    public void setPending(boolean pending){
        this.pending.set(pending);
    }

    public void clear(){
        store.clear();
    }

    public RackInfo[] getAllRackInfo(){
        return store.getAllRackInfo();
    }

    public RackInfo getRackInfo(String id){

        return store.getRackInfo(id);
    }

    public RackInfo updateRackInfo(RackInfo rackInfo){
        return store.updateRackInfo(rackInfo.getRackId(), rackInfo);
    }

}