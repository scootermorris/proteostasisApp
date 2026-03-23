package edu.ucsf.rbvi.proteostasisApp;

import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;

/**
 * Registered as an OSGi TaskFactory service.
 * Cytoscape discovers it from the service properties and adds the menu entry.
 * All required Cytoscape services are injected by CyActivator.
 */
public class LoadNetworkTaskFactory extends AbstractTaskFactory {

		final CyServiceRegistrar registrar;

    static final String JSON_NETWORK_URL =
            "https://www.cgl.ucsf.edu/home/scooter/proteostasis_initial_network.json";

    static final String JSON_DATA_URL =
            "https://www.cgl.ucsf.edu/home/scooter/proteostasis_initial_data.json";

    public LoadNetworkTaskFactory(CyServiceRegistrar registrar) {
				this.registrar       = registrar;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new LoadNetworkTask(registrar, JSON_NETWORK_URL, JSON_DATA_URL));
    }

    @Override
    public boolean isReady() {
        return true;
    }
}
