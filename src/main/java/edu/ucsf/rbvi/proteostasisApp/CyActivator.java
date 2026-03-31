package edu.ucsf.rbvi.proteostasisApp;

import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskFactory;
import org.osgi.framework.BundleContext;

import edu.ucsf.rbvi.proteostasisApp.tasks.LoadNetworkTaskFactory;
import edu.ucsf.rbvi.proteostasisApp.tasks.SolveNetworkTaskFactory;

import java.util.Properties;

/**
 * OSGi bundle activator — entry point for Cytoscape 3 apps.
 *
 * Extends AbstractCyActivator. On start() it:
 *   1. Retrieves required Cytoscape services from the OSGi context
 *   2. Instantiates LoadNetworkTaskFactory with those services
 *   3. Registers it as a TaskFactory OSGi service with menu properties
 *      so Cytoscape automatically adds Apps > Proteostasis > Load Network
 */
public class CyActivator extends AbstractCyActivator {

    @Override
    public void start(BundleContext ctx) throws Exception {

        CyServiceRegistrar registrar   = getService(ctx, CyServiceRegistrar.class);

        // Build the task factory
        LoadNetworkTaskFactory factory = new LoadNetworkTaskFactory(registrar);

        // Service properties tell Cytoscape where to put the menu item
        Properties props = new Properties();
        props.setProperty("title",         "Load Proteostasis Network");
        props.setProperty("preferredMenu", "Apps.Proteostasis");
        props.setProperty("menuGravity",   "1.0");
        props.setProperty("inMenuBar",     "true");
        props.setProperty("inToolBar",     "false");

        registerService(ctx, factory, TaskFactory.class, props);

				// Register Solve Network menu item
        SolveNetworkTaskFactory solveFactory = new SolveNetworkTaskFactory(registrar);
        Properties solveProps = new Properties();
        solveProps.setProperty("title",         "Solve Network");
        solveProps.setProperty("preferredMenu", "Apps.Proteostasis");
        solveProps.setProperty("menuGravity",   "2.0");
        solveProps.setProperty("inMenuBar",     "true");
        solveProps.setProperty("inToolBar",     "false");
        registerService(ctx, solveFactory, TaskFactory.class, solveProps);

        System.out.println("[ProteostasisApp] started — Apps > Proteostasis > Load Proteostasis Network");
    }
}
