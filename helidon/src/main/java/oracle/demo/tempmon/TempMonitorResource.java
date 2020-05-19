package oracle.demo.tempmon;

import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

// import org.eclipse.microprofile.config.inject.ConfigProperty;
// import javax.inject.Inject;

import org.jboss.weld.exceptions.IllegalArgumentException;

@Path("/tempmon")
@ApplicationScoped
public class TempMonitorResource {

    private static final Logger logger = Logger.getLogger(TempMonitorResource.class.getName());

    private TempMonitor monitor = TempMonitor.getInstance();

    //@Inject @ConfigProperty(name = "tempmon.pollingInterval", defaultValue = "5000")
    //private Long pollingInterval;

    public TempMonitorResource(){
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/")
    public RackInfo[] getAllRackInfo() {
        return monitor.getAllRackInfo();
    }

    @Path("/{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public RackInfo getRackInfo(@PathParam("id") String rackId) {
        return monitor.getRackInfo(rackId);
    }

    @Path("/")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public void postRackInfo(RackInfo rackInfo) {
        monitor.updateRackInfo(rackInfo);
    }

    @Path("/control")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public void controlPublisher(@QueryParam("op") String operation) {
        if(operation.equalsIgnoreCase("resume")){
            logger.info("Monitor will be resuming.");
            monitor.setPending(false);
        }else if(operation.equalsIgnoreCase("pause")){
            logger.info("Monitor will be pending.");
            monitor.setPending(true);
        }else if(operation.equalsIgnoreCase("clear")){
            logger.info("Monitor will be cleared.");
            monitor.clear();
        }else{
            throw new IllegalArgumentException("Unknown operation command: " + operation);
        }
    }


}
