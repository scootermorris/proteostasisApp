/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp;

import org.cytoscape.application.events.SetCurrentNetworkListener;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.model.events.RowsSetListener;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskFactory;
import org.osgi.framework.BundleContext;

import edu.ucsf.rbvi.proteostasisApp.tasks.AddNodeTaskFactory;
import edu.ucsf.rbvi.proteostasisApp.tasks.LoadNetworkTaskFactory;
import edu.ucsf.rbvi.proteostasisApp.tasks.SolveNetworkTaskFactory;
import edu.ucsf.rbvi.proteostasisApp.view.NodeSelectionListener;
import edu.ucsf.rbvi.proteostasisApp.view.ProteostasisResultsPanel;

import java.util.Properties;

/**
 * OSGi bundle activator — entry point for the Proteostasis Cytoscape App.
 *
 * Registers:
 *   1. LoadNetworkTaskFactory   → Apps > Proteostasis > Load Proteostasis Network
 *   2. SolveNetworkTaskFactory  → Apps > Proteostasis > Solve Proteostasis Network
 *   3. AddNodeTaskFactory       → "Add Interactor" button in Node Details panel
 *   4. ProteostasisResultsPanel → Cytoscape EAST (Results) panel
 *   5. NodeSelectionListener    → RowsSetEvent + SetCurrentNetworkEvent listeners
 */
public class CyActivator extends AbstractCyActivator {

    @Override
    public void start(BundleContext ctx) throws Exception {

        CyServiceRegistrar registrar = getService(ctx, CyServiceRegistrar.class);

        // ── 1. Load network ───────────────────────────────────────────────────
        Properties loadProps = new Properties();
        loadProps.setProperty("title",         "Load Proteostasis Network");
        loadProps.setProperty("preferredMenu", "Apps.Proteostasis");
        loadProps.setProperty("menuGravity",   "1.0");
        loadProps.setProperty("inMenuBar",     "true");
        loadProps.setProperty("inToolBar",     "false");
        registerService(ctx, new LoadNetworkTaskFactory(registrar),
                        TaskFactory.class, loadProps);

        // ── 2. Solve network ──────────────────────────────────────────────────
        Properties solveProps = new Properties();
        solveProps.setProperty("title",         "Solve Proteostasis Network");
        solveProps.setProperty("preferredMenu", "Apps.Proteostasis");
        solveProps.setProperty("menuGravity",   "2.0");
        solveProps.setProperty("inMenuBar",     "true");
        solveProps.setProperty("inToolBar",     "false");
        registerService(ctx, new SolveNetworkTaskFactory(registrar),
                        TaskFactory.class, solveProps);

        // ── 4. Results panel ──────────────────────────────────────────────────
        ProteostasisResultsPanel panel = new ProteostasisResultsPanel(registrar);
        registerService(ctx, panel, CytoPanelComponent.class, new Properties());

        // ── 5. Selection listeners ────────────────────────────────────────────
        NodeSelectionListener listener = new NodeSelectionListener(registrar, panel);
        registerService(ctx, listener, RowsSetListener.class,           new Properties());
        registerService(ctx, listener, SetCurrentNetworkListener.class, new Properties());

        System.out.println("[ProteostasisApp] v2.0.0 started — Load + Solve + panel registered");
    }
}
