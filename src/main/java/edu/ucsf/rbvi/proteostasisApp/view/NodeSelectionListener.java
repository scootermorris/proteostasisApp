/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp.view;

import java.util.List;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.application.events.SetCurrentNetworkEvent;
import org.cytoscape.application.events.SetCurrentNetworkListener;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.model.events.RowsSetListener;
import org.cytoscape.service.util.CyServiceRegistrar;

/**
 * Listens for both node and edge selection changes in the active network
 * and routes them to the appropriate tab in {@link ProteostasisResultsPanel}.
 *
 * Registered for:
 *   - RowsSetEvent              — fires when any table row value changes
 *                                 (including the CyNetwork.SELECTED column)
 *   - SetCurrentNetworkEvent    — fires when the user switches networks
 */
public class NodeSelectionListener implements RowsSetListener, SetCurrentNetworkListener {

    private final CyServiceRegistrar      registrar;
    private final ProteostasisResultsPanel panel;

    public NodeSelectionListener(CyServiceRegistrar registrar,
                                 ProteostasisResultsPanel panel) {
        this.registrar = registrar;
        this.panel     = panel;
    }

    // ── RowsSetListener ───────────────────────────────────────────────────────

    @Override
    public void handleEvent(RowsSetEvent event) {
        if (!event.containsColumn(CyNetwork.SELECTED)) return;

        CyApplicationManager appMgr  = registrar.getService(CyApplicationManager.class);
        CyNetwork            network = appMgr.getCurrentNetwork();
        if (network == null) return;

        // ── Node selection ────────────────────────────────────────────────────
        if (event.getSource().equals(network.getDefaultNodeTable())) {
            List<CyNode> selected = network.getNodeList();
            selected.removeIf(n -> !Boolean.TRUE.equals(
                    network.getRow(n).get(CyNetwork.SELECTED, Boolean.class)));

            if (selected.size() == 1) {
                panel.showNode(network, selected.get(0));
            } else if (selected.isEmpty()) {
                // Only clear if edges are also deselected
                List<CyEdge> selEdges = network.getEdgeList();
                selEdges.removeIf(e -> !Boolean.TRUE.equals(
                        network.getRow(e).get(CyNetwork.SELECTED, Boolean.class)));
                if (selEdges.isEmpty()) panel.clearSelection();
            }
            return;
        }

        // ── Edge selection ────────────────────────────────────────────────────
        if (event.getSource().equals(network.getDefaultEdgeTable())) {
            List<CyEdge> selected = network.getEdgeList();
            selected.removeIf(e -> !Boolean.TRUE.equals(
                    network.getRow(e).get(CyNetwork.SELECTED, Boolean.class)));

            if (selected.size() == 1) {
                panel.showEdge(network, selected.get(0));
            } else if (selected.isEmpty()) {
                // Only clear if nodes are also deselected
                List<CyNode> selNodes = network.getNodeList();
                selNodes.removeIf(n -> !Boolean.TRUE.equals(
                        network.getRow(n).get(CyNetwork.SELECTED, Boolean.class)));
                if (selNodes.isEmpty()) panel.clearSelection();
            }
        }
    }

    // ── SetCurrentNetworkListener ─────────────────────────────────────────────

    @Override
    public void handleEvent(SetCurrentNetworkEvent event) {
        panel.clearSelection();
    }
}
