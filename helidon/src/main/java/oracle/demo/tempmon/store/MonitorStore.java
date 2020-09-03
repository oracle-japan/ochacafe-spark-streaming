package oracle.demo.tempmon.store;

import oracle.demo.tempmon.RackInfo;

public interface MonitorStore {

    public void close();
    public void clear();
    public RackInfo[] getAllRackInfo();
    public RackInfo getRackInfo(String id);
    public RackInfo updateRackInfo(String id, RackInfo rackInfo);
    
}