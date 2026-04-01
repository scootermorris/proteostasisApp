/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp.view;

import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.events.SetCurrentNetworkEvent;
import org.cytoscape.application.events.SetCurrentNetworkListener;
import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelState;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.model.events.RowsSetListener;
import org.cytoscape.service.util.CyServiceRegistrar;

/**
 * Listens for node selection changes in the active Cytoscape network and
 * updates the {@link ProteostasisResultsPanel} accordingly.
 *
 * It implements two Cytoscape event listener interfaces:
 *   - RowsSetListener  — fires when any CyTable rows are set (including
 *                        the "selected" boolean column on nodes).
 *   - SetCurrentNetworkListener — fires when the user switches networks,
 *                        so we can reset the panel.
 */
public class NodeSelectionListener implements RowsSetListener, SetCurrentNetworkListener {

    private final CyServiceRegistrar         registrar;
    private final ProteostasisResultsPanel    panel;

    public NodeSelectionListener(CyServiceRegistrar registrar,
                                 ProteostasisResultsPanel panel) {
        this.registrar = registrar;
        this.panel     = panel;
    }

    // ── RowsSetListener ───────────────────────────────────────────────────────

    @Override
    public void handleEvent(RowsSetEvent event) {
        // Only react to changes in the "selected" column
        if (!event.containsColumn(CyNetwork.SELECTED)) return;

        CyApplicationManager appMgr  = registrar.getService(CyApplicationManager.class);
        CyNetwork            network = appMgr.getCurrentNetwork();
        if (network == null) return;

        // Check that this event is for the current network's node table
        if (!event.getSource().equals(network.getDefaultNodeTable())) return;

        // Collect all selected nodes
        List<CyNode> selected = network.getNodeList();
        selected.removeIf(n -> !Boolean.TRUE.equals(network.getRow(n).get(CyNetwork.SELECTED, Boolean.class)));

        if (selected.size() == 1) {
            panel.showNode(network, selected.get(0));
        } else {
            panel.clearSelection();
        }
    }

    // ── SetCurrentNetworkListener ─────────────────────────────────────────────

    @Override
    public void handleEvent(SetCurrentNetworkEvent event) {
		CyNetwork network = event.getNetwork();
        panel.clearSelection();

		// See if this is a proteostasis network
		if (network.getDefaultNetworkTable().getRow(network.getSUID()).get(CyNetwork.NAME, String.class) != "Proteostasis Core Network") {
			// No, disable the results panel
            registrar.unregisterService(panel, CytoPanelComponent.class);
		} else {
			// Yes, enable the results panel
            registrar.registerService(panel, CytoPanelComponent.class, new Properties());
		}
    }
}
