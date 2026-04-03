/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp.tasks;

import com.google.gson.*;
import org.cytoscape.group.CyGroup;
import org.cytoscape.group.CyGroupFactory;
import org.cytoscape.group.CyGroupManager;
import org.cytoscape.group.CyGroupSettingsManager;
import org.cytoscape.group.CyGroupSettingsManager.GroupViewType;
import org.cytoscape.model.*;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.view.layout.CyLayoutAlgorithm;
import org.cytoscape.view.layout.CyLayoutAlgorithmManager;
import org.cytoscape.view.model.*;
import org.cytoscape.view.model.VisualLexicon;

import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

import edu.ucsf.rbvi.proteostasisApp.Columns;
import edu.ucsf.rbvi.proteostasisApp.DataManager;
import edu.ucsf.rbvi.proteostasisApp.StyleManager;
import edu.ucsf.rbvi.proteostasisApp.utils.Utils;

import javax.swing.*;
import java.awt.Color;
import java.awt.Paint;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Fetches the proteostasis JSON from a URL, creates a CyNetwork,
 * populates all node/edge attributes, groups cc_tpr nodes into their
 * cluster groups via the CyGroup API, applies a visual style, and
 * runs the CoSE layout.
 *
 * Grouping strategy:
 *   - Each cluster node from the JSON becomes a CyGroup (the group node
 *     IS the cluster node already in the network).
 *   - All cc_tpr nodes whose cluster_id matches a cluster node are added
 *     as members of that group.
 *   - Groups are left expanded so CoSE can see and lay out the members.
 *
 * All Cytoscape services are injected via the constructor.
 */
public class LoadNetworkTask extends AbstractTask {

    private final CyNetworkFactory             networkFactory;
    private final CyNetworkManager             networkManager;
    private final CyGroupFactory               groupFactory;
    private final CyGroupManager               groupManager;
    private final CyGroupSettingsManager        groupSettingsManager;
    private final CyNetworkViewFactory         viewFactory;
    private final CyNetworkViewManager         viewManager;
    private final CyServiceRegistrar           registrar;
    private final CyLayoutAlgorithmManager     layoutManager;
    private final String                       jsonNetworkUrl;
    private final String                       jsonDataUrl;

    private final StyleManager                 styleManager;

    // Registered name of the CoSE layout algorithm in Cytoscape
    private static final String COSE_LAYOUT_NAME  = "cose";

    LoadNetworkTask(
            CyServiceRegistrar           registrar,
            String                       jsonNetworkUrl,
            String                       jsonDataUrl) {
        this.registrar              = registrar;
        this.jsonNetworkUrl         = jsonNetworkUrl;
        this.jsonDataUrl            = jsonDataUrl;

        // Core network services
        networkFactory  = registrar.getService(CyNetworkFactory.class);
        networkManager  = registrar.getService(CyNetworkManager.class);
        viewFactory     = registrar.getService(CyNetworkViewFactory.class);
        viewManager     = registrar.getService(CyNetworkViewManager.class);

        // Group API services
        groupFactory         = registrar.getService(CyGroupFactory.class);
        groupManager         = registrar.getService(CyGroupManager.class);
        groupSettingsManager = registrar.getService(CyGroupSettingsManager.class);

        // Style services
        styleManager         = new StyleManager(registrar);

        // Layout services
        layoutManager   = registrar.getService(CyLayoutAlgorithmManager.class);


    }

    @Override
    public void run(TaskMonitor monitor) throws Exception {
        monitor.setTitle("Proteostasis Network Loader");
        monitor.setStatusMessage("Fetching JSON Network from " + jsonNetworkUrl);
        monitor.setProgress(0.0);

        // 1. Download JSONs
        String jsonNetworkText = Utils.fetchJson(jsonNetworkUrl);
        if (cancelled) return;

        String jsonDataText = Utils.fetchJson(jsonDataUrl);
        if (cancelled) return;

        monitor.setStatusMessage("Parsing JSON…");
        monitor.setProgress(0.2);

        // Get the network
        JsonObject netRoot      = JsonParser.parseString(jsonNetworkText).getAsJsonObject();
        JsonArray  nodesJson    = netRoot.getAsJsonArray("nodes");
        JsonArray  edgesJson    = netRoot.getAsJsonArray("edges");

        // 2. Create network
        CyNetwork network = networkFactory.createNetwork();
        network.getRow(network).set(CyNetwork.NAME, "Proteostasis Core Network");

        // 3. Ensure node columns exist
        CyTable nodeTable = network.getDefaultNodeTable();
        Utils.ensureColumn(nodeTable, Columns.COL_NODE_CLASS,    String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_DISPLAY_NAME,  String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_LABEL,         String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_FAMILY,        String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_GENE_SYMBOL,   String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_UNIPROT_ID,    String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_REP_UNIPROT,   String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_TOTAL_NM,      Double.class);
        Utils.ensureColumn(nodeTable, Columns.COL_HAS_TOTAL,     Boolean.class);
        Utils.ensureColumn(nodeTable, Columns.COL_PROTEIN_CLASS, String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_CLUSTER_ID,    String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_CLUSTER_LABEL, String.class);
        Utils.ensureColumn(nodeTable, Columns.COL_FREE,          Double.class);
        Utils.ensureColumn(nodeTable, Columns.COL_PIECHART,      String.class);
        Utils.ensureListColumn(nodeTable, Columns.COL_BOUND,     Double.class);
        Utils.ensureColumn(nodeTable, Columns.COL_X,             Double.class);
        Utils.ensureColumn(nodeTable, Columns.COL_Y,             Double.class);
        Utils.ensureColumn(nodeTable, Columns.COL_TOOLTIP,       String.class);

        // 4. Ensure edge columns exist
        CyTable edgeTable = network.getDefaultEdgeTable();
        Utils.ensureColumn(edgeTable, Columns.COL_EDGE_CLASS, String.class);
        Utils.ensureColumn(edgeTable, Columns.COL_KD_U_NM,    Double.class);
        Utils.ensureColumn(edgeTable, Columns.COL_KD_P_NM,    Double.class);
        Utils.ensureColumn(edgeTable, Columns.COL_HAS_KD,     Boolean.class);
        Utils.ensureColumn(edgeTable, Columns.COL_BOUND,      Double.class);
        Utils.ensureColumn(edgeTable, Columns.COL_FRAC_BOUND, Double.class);
        Utils.ensureColumn(edgeTable, Columns.COL_SOURCE,     String.class);
        Utils.ensureColumn(edgeTable, Columns.COL_TARGET,     String.class);
        Utils.ensureColumn(edgeTable, Columns.COL_TOOLTIP,    String.class);

        // 5. Add all nodes
        monitor.setStatusMessage("Creating nodes…");
        monitor.setProgress(0.35);

        Map<String, CyNode> nodeMap    = new HashMap<>();
        // cluster_id → list of member nodes (non-cluster nodes)
        Map<String, List<CyNode>> clusterMembers = new LinkedHashMap<>();

        for (JsonElement el : nodesJson) {
            if (cancelled) return;
            JsonObject data = el.getAsJsonObject().getAsJsonObject("data");
            String id        = data.get("id").getAsString();
            String nodeClass = data.has(Columns.COL_NODE_CLASS) ? data.get(Columns.COL_NODE_CLASS).getAsString() : "";

            CyNode node = network.addNode();
            nodeMap.put(id, node);

            CyRow row = network.getRow(node);
            row.set(CyNetwork.NAME, id);
            Utils.setStr(row, data, Columns.COL_NODE_CLASS);
            Utils.setStr(row, data, Columns.COL_DISPLAY_NAME);
            Utils.setStr(row, data, Columns.COL_LABEL);
            Utils.setStr(row, data, Columns.COL_FAMILY);
            Utils.setStr(row, data, Columns.COL_GENE_SYMBOL);
            Utils.setStr(row, data, Columns.COL_UNIPROT_ID);
            Utils.setStr(row, data, Columns.COL_REP_UNIPROT);
            Utils.setDbl(row, data, Columns.COL_TOTAL_NM);
            Utils.setDbl(row, data, Columns.COL_X);
            Utils.setDbl(row, data, Columns.COL_Y);
            Utils.setBool(row, data, Columns.COL_HAS_TOTAL);
            Utils.setStr(row, data, Columns.COL_PROTEIN_CLASS);
            Utils.setStr(row, data, Columns.COL_CLUSTER_ID);
            Utils.setStr(row, data, Columns.COL_CLUSTER_LABEL);

            // Accumulate members for each cluster (skip cluster nodes themselves)
            if (!"cluster".equals(nodeClass)
                    && data.has(Columns.COL_CLUSTER_ID)
                    && !data.get(Columns.COL_CLUSTER_ID).isJsonNull()) {
                String clusterId = data.get(Columns.COL_CLUSTER_ID).getAsString();
                clusterMembers.computeIfAbsent(clusterId, k -> new ArrayList<>()).add(node);
            }
        }

        // 6. Add edges
        monitor.setStatusMessage("Creating edges…");
        monitor.setProgress(0.50);

        for (JsonElement el : edgesJson) {
            if (cancelled) return;
            JsonObject data     = el.getAsJsonObject().getAsJsonObject("data");
            String     sourceId = data.get("source").getAsString();
            String     targetId = data.get("target").getAsString();

            CyNode source = nodeMap.get(sourceId);
            CyNode target = nodeMap.get(targetId);
            if (source == null || target == null) continue;

            CyEdge edge = network.addEdge(source, target, true);
            CyRow  row  = network.getRow(edge);
            row.set(CyNetwork.NAME, sourceId + " \u2192 " + targetId);
            Utils.setStr(row, data, Columns.COL_EDGE_CLASS);
            Utils.setDbl(row, data, Columns.COL_KD_U_NM);
            Utils.setDbl(row, data, Columns.COL_KD_P_NM);
            Utils.setBool(row, data, Columns.COL_HAS_KD);
            Utils.setStr(row, Columns.COL_SOURCE, sourceId);
            Utils.setStr(row, Columns.COL_TARGET, targetId);
        }

        // 7. Add the data
        //    Note that we already created the rows for the data above, and
        //    we've already fetched the data in step 1
        monitor.setStatusMessage("Adding data…");
        monitor.setProgress(0.60);
        DataManager.addData(network, nodeMap, jsonDataText, false);

        // 8. Register network
        //    (must happen before createNetworkView and before CyGroupFactory.createGroup)
        monitor.setStatusMessage("Registering network…");
        monitor.setProgress(0.65);
        networkManager.addNetwork(network);

        // 9. Create view and apply visual style
        //    (must exist before groups are created so compound node views render correctly)
        monitor.setStatusMessage("Applying visual style…");
        monitor.setProgress(0.75);

        CyNetworkView view = viewFactory.createNetworkView(network);
        styleManager.applyStyle(view);
        viewManager.addNetworkView(view);

        // 10. Create groups — one CyGroup per cluster node.
        //     Done after the view exists so Cytoscape can immediately render each
        //     group as a compound node in the network view.
        monitor.setStatusMessage("Creating groups…");
        monitor.setProgress(0.82);

        // Set the default view type to COMPOUND NODE before creating the group
        // so Cytoscape renders the group node as a compound container.
        groupSettingsManager.setGroupViewType(GroupViewType.COMPOUND);

        for (Map.Entry<String, List<CyNode>> entry : clusterMembers.entrySet()) {
            if (cancelled) return;
            String       clusterId = entry.getKey();
            List<CyNode> members   = entry.getValue();
            CyNode       groupNode = nodeMap.get(clusterId);

            if (groupNode == null || members.isEmpty()) continue;

            // Collect internal edges: edges whose both endpoints are members of this group
            List<CyEdge> internalEdges = new ArrayList<>();
            for (CyEdge edge : network.getEdgeList()) {
                boolean srcIn = members.contains(edge.getSource());
                boolean tgtIn = members.contains(edge.getTarget());
                if (srcIn && tgtIn) internalEdges.add(edge);
            }

            // The existing cluster CyNode becomes the group node.
            CyGroup group = groupFactory.createGroup(network, groupNode, members, null, true);

            // Collapse the network to establish the meta edges
            group.collapse(network);

            // Expand so all members are visible for the CoSE layout
            group.expand(network);

            styleManager.applyClusterStyle(view, groupNode);
        }

        // 10. Run CoSE layout on the EDT
        monitor.setStatusMessage("Running CoSE layout…");
        monitor.setProgress(0.90);

        /*
        SwingUtilities.invokeLater(() -> {
            try {
                CyLayoutAlgorithm layout = layoutManager.getLayout(COSE_LAYOUT_NAME);
                if (layout == null) {
                    System.err.println("[ProteostasisApp] CoSE layout not found, using default");
                    layout = layoutManager.getDefaultLayout();
                }
                layout.createTaskIterator(
                        view,
                        layout.createLayoutContext(),
                        CyLayoutAlgorithm.ALL_NODE_VIEWS,
                        null)
                      .next()
                      .run(monitor);
                view.fitContent();
                view.updateView();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        */

        monitor.setProgress(1.0);
        monitor.setStatusMessage("Done — "
                + nodesJson.size() + " nodes, "
                + edgesJson.size() + " edges loaded.");
    }

}
