package oracle.demo.tempmon;

import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import io.helidon.common.CollectionsHelper;

/**
 * Simple Application that produces a greeting message.
 */
@ApplicationScoped
@ApplicationPath("/")
public class App extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        return CollectionsHelper.setOf(TempMonitorResource.class);
    }
}
