/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp.tasks;

import java.util.Collection;
import java.util.List;

import javax.swing.SwingUtilities;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.CyRow;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.view.model.View;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.vizmap.VisualMappingManager;
import org.cytoscape.view.vizmap.VisualStyle;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

import edu.ucsf.rbvi.proteostasisApp.Columns;
import edu.ucsf.rbvi.proteostasisApp.DataManager;
import edu.ucsf.rbvi.proteostasisApp.utils.Utils;

/**
 * Adds a new cc_tpr node to the current network, connected to the currently
 * selected node by a new edge.
 *
 * Tunables collected before execution:
 *   - nodeName   — display name / ID for the new node
 *   - totalNm    — total_nM concentration for the new node
 *   - kdNm       — kd_nM affinity constant for the connecting edge
 *   - edgeClass  — edge_class for the new edge (default "binding")
 *
 * The new node is placed outside the existing network bounding box:
 *   - If the anchor node is in the right half  → place new node further right
 *   - If in the left half                       → further left
 *   - If in the top half                        → further above
 *   - If in the bottom half                     → further below
 *
 * The visual style is re-applied after adding the node/edge so the new
 * elements pick up the correct style mappings.
 */
public class AddNodeTask extends AbstractTask {
    static String INTERACTOR_PIE = "piechart: attributelist=\""+Utils.mkCol(Columns.COL_BOUND)+
                                   "\" colorlist=\"#e6c75f,#a06dc7\" arcstart=90";

    // ── Tunables ──────────────────────────────────────────────────────────────

    @Tunable(description = "Node name / ID", groups = "New Node")
    public String nodeName = "NewProtein";

    @Tunable(description = "Total concentration (nM)", groups = "New Node")
    public double totalNm = 1000.0;

    @Tunable(description = "Kd (nM) for the connecting edge", groups = "New Edge")
    public double kdNm = 500.0;

    @Tunable(description = "Edge class (e.g. binding)", groups = "New Edge")
    public String edgeClass = "binding";

    // ── Layout padding ────────────────────────────────────────────────────────
    /** Distance from network bounding box to place the new node. */
    private static final double PLACEMENT_GAP = 150.0;

    private final CyServiceRegistrar registrar;

    AddNodeTask(CyServiceRegistrar registrar) {
        this.registrar = registrar;
    }

    @Override
    public void run(TaskMonitor monitor) throws Exception {
        monitor.setTitle("Add Node");

        // ── 1. Get current network and selected node ──────────────────────────
        CyApplicationManager appMgr  = registrar.getService(CyApplicationManager.class);
        CyNetwork            network = appMgr.getCurrentNetwork();

        if (network == null) {
            monitor.showMessage(TaskMonitor.Level.WARN, "No current network.");
            return;
        }

        List<CyNode> selected = network.getNodeList();
        selected.removeIf(n -> !Boolean.TRUE.equals(
                network.getRow(n).get(CyNetwork.SELECTED, Boolean.class)));

        if (selected.size() != 1) {
            monitor.showMessage(TaskMonitor.Level.WARN,
                    "Please select exactly one node to connect the new node to.");
            return;
        }

        CyNode anchor = selected.get(0);
        monitor.setProgress(0.1);

        // ── 2. Ensure columns exist on the node and edge tables ───────────────
        CyTable nodeTable = network.getDefaultNodeTable();
        CyTable edgeTable = network.getDefaultEdgeTable();

        Utils.ensureColumn(nodeTable, Columns.COL_NODE_CLASS,    String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_DISPLAY_NAME,  String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_TOTAL_NM,      Double.class);
        Utils.ensureColumn(nodeTable, Columns.COL_HAS_TOTAL,     Boolean.class);
        Utils.ensureColumn(nodeTable, Columns.COL_FREE,          Double.class);
        Utils.ensureColumn(nodeTable, Columns.COL_TOOLTIP,       String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_PIECHART,      String.class);
        Utils.ensureColumn(edgeTable, Columns.COL_EDGE_CLASS,    String.class);
        Utils.ensureColumn(edgeTable, Columns.COL_KD_U_NM,       Double.class);
        Utils.ensureColumn(edgeTable, Columns.COL_KD_P_NM,       Double.class);
        Utils.ensureColumn(edgeTable, Columns.COL_HAS_KD,        Boolean.class);
        Utils.ensureColumn(edgeTable, Columns.COL_BOUND,         Double.class);
        Utils.ensureColumn(edgeTable, Columns.COL_FRAC_BOUND,    Double.class);
        Utils.ensureColumn(edgeTable, Columns.COL_SOURCE,        String.class);
        Utils.ensureColumn(edgeTable, Columns.COL_TARGET,        String.class);
        Utils.ensureColumn(edgeTable, Columns.COL_TOOLTIP,       String.class);

        // ── 3. Create the new node ────────────────────────────────────────────
        CyNode newNode = network.addNode();
        CyRow  nRow    = network.getRow(newNode);
        nRow.set(CyNetwork.NAME, nodeName);
        Utils.setStr(nRow, Columns.COL_NODE_CLASS,   "cc_tpr");
        Utils.setStr(nRow, Columns.COL_DISPLAY_NAME, nodeName);
        Utils.setDbl(nRow, Columns.COL_TOTAL_NM,     totalNm);
        nRow.set(Utils.mkCol(Columns.COL_HAS_TOTAL), true);
        Utils.setStr(nRow, Columns.COL_TOOLTIP, DataManager.makeNodeTooltip(nRow, nodeName, null));
        Utils.setStr(nRow, Columns.COL_PIECHART, INTERACTOR_PIE);

        monitor.setProgress(0.3);

        // ── 4. Create the connecting edge ─────────────────────────────────────
        String anchorName = network.getRow(anchor).get(CyNetwork.NAME, String.class);
        CyEdge newEdge    = network.addEdge(newNode, anchor, true);
        CyRow  eRow       = network.getRow(newEdge);
        eRow.set(CyNetwork.NAME, nodeName + " \u2192 " + anchorName);
        Utils.setStr(eRow, Columns.COL_EDGE_CLASS, edgeClass);
        Utils.setDbl(eRow, Columns.COL_KD_U_NM,    kdNm);
        eRow.set(Utils.mkCol(Columns.COL_HAS_KD), true);
        Utils.setStr(eRow, Columns.COL_SOURCE,     nodeName);
        Utils.setStr(eRow, Columns.COL_TARGET,     anchorName);
        Utils.setStr(eRow, Columns.COL_TOOLTIP, DataManager.makeEdgeTooltip(eRow, nodeName+"->"+anchorName));

        monitor.setProgress(0.5);

        // ── 5. Position the new node in the view ──────────────────────────────
        Collection<CyNetworkView> views =
                registrar.getService(CyNetworkViewManager.class).getNetworkViews(network);

        if (!views.isEmpty()) {
            CyNetworkView view = views.iterator().next();

            // Compute bounding box of all existing nodes (excluding the new one)
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            double anchorX = 0, anchorY = 0;

            for (CyNode n : network.getNodeList()) {
                if (n.equals(newNode)) continue;
                View<CyNode> nv = view.getNodeView(n);
                if (nv == null) continue;
                double x = nv.getVisualProperty(BasicVisualLexicon.NODE_X_LOCATION);
                double y = nv.getVisualProperty(BasicVisualLexicon.NODE_Y_LOCATION);
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                if (n.equals(anchor)) { anchorX = x; anchorY = y; }
            }

            // Determine placement quadrant based on anchor's position relative to centre
            double cx = (minX + maxX) / 2.0;
            double cy = (minY + maxY) / 2.0;

            double newX, newY;
            // Favour the axis with the larger displacement from centre
            double dx = anchorX - cx;
            double dy = anchorY - cy;

            if (Math.abs(dx) >= Math.abs(dy)) {
                // Left-right placement
                newY = anchorY;
                newX = (dx >= 0)
                        ? maxX + PLACEMENT_GAP   // anchor is right → place further right
                        : minX - PLACEMENT_GAP;  // anchor is left  → place further left
            } else {
                // Top-bottom placement  (Y increases downward in Cytoscape)
                newX = anchorX;
                newY = (dy >= 0)
                        ? maxY + PLACEMENT_GAP   // anchor is below centre → place further down
                        : minY - PLACEMENT_GAP;  // anchor is above centre → place further up
            }

            final double fx = newX, fy = newY;
            Utils.setDbl(nRow, Columns.COL_X, fx);
            Utils.setDbl(nRow, Columns.COL_Y, fy);
            SwingUtilities.invokeLater(() -> {
                // Re-apply the visual style so the new node/edge are styled correctly
                VisualMappingManager vmm  = registrar.getService(VisualMappingManager.class);
                VisualStyle          vs   = vmm.getVisualStyle(view);
                if (vs != null) vs.apply(view);
                view.updateView();
            });
        }

        monitor.setProgress(1.0);
        monitor.setStatusMessage("Added node '" + nodeName + "' connected to '" + anchorName + "'.");
    }
}
