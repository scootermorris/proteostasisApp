package edu.ucsf.rbvi.proteostasisApp;

import org.cytoscape.application.events.SetCurrentNetworkListener;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelState;
import org.cytoscape.model.events.RowsSetListener;
import org.cytoscape.service.util.AbstractCyActivator;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskFactory;
import org.osgi.framework.BundleContext;

import java.util.Properties;

import edu.ucsf.rbvi.proteostasisApp.tasks.LoadNetworkTaskFactory;
import edu.ucsf.rbvi.proteostasisApp.tasks.SolveNetworkTaskFactory;
import edu.ucsf.rbvi.proteostasisApp.view.ProteostasisResultsPanel;
import edu.ucsf.rbvi.proteostasisApp.view.NodeSelectionListener;

/**
 * OSGi bundle activator — entry point for Cytoscape 3 apps.
 *
 * On start() it:
 *   1. Registers the Load / Solve menu TaskFactory services
 *   2. Creates the {@link ProteostasisResultsPanel} and registers it as a
 *      CytoPanelComponent so Cytoscape adds it to the EAST (Results) panel.
 *   3. Creates the {@link NodeSelectionListener} and registers it for both
 *      RowsSetEvent and SetCurrentNetworkEvent so the panel updates whenever
 *      a node is selected or the active network changes.
 */
public class CyActivator extends AbstractCyActivator {

    @Override
    public void start(BundleContext ctx) throws Exception {

        CyServiceRegistrar registrar = getService(ctx, CyServiceRegistrar.class);

        // ── 1. Task factories (menu items) ────────────────────────────────────
        LoadNetworkTaskFactory loadFactory = new LoadNetworkTaskFactory(registrar);

        Properties loadProps = new Properties();
        loadProps.setProperty("title",         "Load Proteostasis Network");
        loadProps.setProperty("preferredMenu", "Apps.Proteostasis");
        loadProps.setProperty("menuGravity",   "1.0");
        loadProps.setProperty("inMenuBar",     "true");
        loadProps.setProperty("inToolBar",     "false");

        registerService(ctx, loadFactory, TaskFactory.class, loadProps);

        SolveNetworkTaskFactory solveFactory = new SolveNetworkTaskFactory(registrar);

        Properties solveProps = new Properties();
        solveProps.setProperty("title",         "Solve Proteostasis Network");
        solveProps.setProperty("preferredMenu", "Apps.Proteostasis");
        solveProps.setProperty("menuGravity",   "2.0");
        solveProps.setProperty("inMenuBar",     "true");
        solveProps.setProperty("inToolBar",     "false");

        registerService(ctx, solveFactory, TaskFactory.class, solveProps);

        // ── 2. Results panel ──────────────────────────────────────────────────
        ProteostasisResultsPanel resultsPanel = new ProteostasisResultsPanel(registrar);
        // registerService(ctx, resultsPanel, CytoPanelComponent.class, new Properties());
				// resultsPanel.setState(CytoPanelState.HIDE);

        // ── 3. Node-selection listener ────────────────────────────────────────
        NodeSelectionListener selectionListener = new NodeSelectionListener(registrar, resultsPanel);
        registerService(ctx, selectionListener, RowsSetListener.class,              new Properties());
        registerService(ctx, selectionListener, SetCurrentNetworkListener.class,    new Properties());

        System.out.println("[ProteostasisApp] started — panel + listener registered");
    }
}

