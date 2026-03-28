package edu.ucsf.rbvi.proteostasisApp;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.work.AbstractTaskFactory;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.service.util.CyServiceRegistrar;

/**
 * Registered as a TaskFactory OSGi service.
 * Adds Apps > Proteostasis > Solve Network to the Cytoscape menu.
 * The menu item is greyed out when no network is currently loaded.
 */
public class SolveNetworkTaskFactory extends AbstractTaskFactory {

    static final String SOLVE_URL =
            "https://www.rbvi.ucsf.edu/cc-tpr/api/v1/solve-network";

		final CyServiceRegistrar registrar;

    public SolveNetworkTaskFactory(CyServiceRegistrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public TaskIterator createTaskIterator() {
        return new TaskIterator(new SolveNetworkTask(registrar, SOLVE_URL));
    }

    @Override
    public boolean isReady() {
        return registrar.getService(CyApplicationManager.class).getCurrentNetwork() != null;
    }
}
