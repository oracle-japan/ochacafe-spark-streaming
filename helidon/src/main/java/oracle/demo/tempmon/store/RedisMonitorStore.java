package oracle.demo.tempmon.store;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import oracle.demo.tempmon.RackInfo;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisMonitorStore implements MonitorStore {

    //private static final Logger logger = Logger.getLogger(RedisMonitorStore.class.getName());
   
    private final JedisPool jedisPool;

    public RedisMonitorStore(){
        jedisPool = new JedisPool();
    }

    @Override
    public void clear() {
        try(Jedis jedis = jedisPool.getResource()){
            jedis.flushAll();
        }
    }

    @Override
    public RackInfo[] getAllRackInfo() {
        try(Jedis jedis = jedisPool.getResource()){
            Set<String> keys = jedis.keys("*");
            final List<RackInfo> rackList = new ArrayList<>();
            keys.forEach(key -> {
                final String json = jedis.get(key);
                rackList.add(RackInfo.fromJson(json));
            });
            return rackList.toArray(new RackInfo[rackList.size()]);
        }
    }

    @Override
    public RackInfo getRackInfo(String id) {
        try(Jedis jedis = jedisPool.getResource()){
            return RackInfo.fromJson(jedis.get(id));
        }
    }

    @Override
    public RackInfo updateRackInfo(String id, RackInfo rackInfo) {
        try(Jedis jedis = jedisPool.getResource()){
            if(!Optional.ofNullable(rackInfo.getTimestamp()).isPresent()){
                rackInfo.setTimestamp(new Date());
            }
            String ret = jedis.set(rackInfo.getRackId(), rackInfo.toJson());
            if(!ret.equals("OK")){
                throw new RuntimeException("Couldn't update - " + ret);
            }
            return null;
        }
    }

    @Override
    public void close() {
        if(Optional.ofNullable(jedisPool).isPresent()){
            jedisPool.close();
        }
    }
  

}