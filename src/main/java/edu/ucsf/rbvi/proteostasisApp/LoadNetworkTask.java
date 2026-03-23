/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp;

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

import org.cytoscape.view.presentation.RenderingEngineManager;
import org.cytoscape.view.presentation.property.ArrowShapeVisualProperty;
import org.cytoscape.view.presentation.property.BasicVisualLexicon;
import org.cytoscape.view.presentation.property.LabelBackgroundShapeVisualProperty;
import org.cytoscape.view.presentation.property.NodeShapeVisualProperty;
import org.cytoscape.view.presentation.property.values.Justification;
import org.cytoscape.view.presentation.property.values.NodeShape;
import org.cytoscape.view.presentation.property.values.ObjectPosition;
import org.cytoscape.view.presentation.property.values.Position;
import org.cytoscape.view.vizmap.*;
import org.cytoscape.view.vizmap.mappings.BoundaryRangeValues;
import org.cytoscape.view.vizmap.mappings.ContinuousMapping;
import org.cytoscape.view.vizmap.mappings.DiscreteMapping;
import org.cytoscape.view.vizmap.mappings.PassthroughMapping;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;

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
    private final VisualMappingManager         vmm;
    private final VisualStyleFactory           vsf;
    private final VisualMappingFunctionFactory continuousFactory;
    private final VisualMappingFunctionFactory discreteFactory;
    private final VisualMappingFunctionFactory passthroughFactory;
    private final CyLayoutAlgorithmManager     layoutManager;
    private final String                       jsonNetworkUrl;
    private final String                       jsonDataUrl;

    // Column names — nodes
    protected static final String COL_NODE_CLASS    = "node_class";
    protected static final String COL_DISPLAY_NAME  = "display_name";
    protected static final String COL_LABEL         = "label";
    protected static final String COL_FAMILY        = "family";
    protected static final String COL_GENE_SYMBOL   = "gene_symbol";
    protected static final String COL_UNIPROT_ID    = "uniprot_id";
    protected static final String COL_REP_UNIPROT   = "representative_uniprot_id";
    protected static final String COL_TOTAL_NM      = "total_nM";
    protected static final String COL_BOUND         = "bound"; // Also an edge attribute
    protected static final String COL_FREE          = "free";
    protected static final String COL_HAS_TOTAL     = "has_total";
    protected static final String COL_PROTEIN_CLASS = "protein_class";
    protected static final String COL_CLUSTER_ID    = "cluster_id";
    protected static final String COL_CLUSTER_LABEL = "cluster_label";
    protected static final String COL_PIECHART      = "PieChart";

    // This may or may not already be there
    protected static final String COL_SOURCE        = "node::Source_name";
    protected static final String COL_TARGET        = "node::Target_name";

    // Column names — edges
    protected static final String COL_EDGE_CLASS    = "edge_class";
    protected static final String COL_KD_NM         = "kd_nM";
    protected static final String COL_HAS_KD        = "has_kd";
    protected static final String COL_FRAC_BOUND    = "frac_bound";

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

        // Visual mapping services
        vmm                    = registrar.getService(VisualMappingManager.class);
        vsf                    = registrar.getService(VisualStyleFactory.class);
        continuousFactory      = registrar.getService(VisualMappingFunctionFactory.class, "(mapping.type=continuous)");
        discreteFactory        = registrar.getService(VisualMappingFunctionFactory.class, "(mapping.type=discrete)");
        passthroughFactory     = registrar.getService(VisualMappingFunctionFactory.class, "(mapping.type=passthrough)");

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
        Utils.ensureColumn(nodeTable, COL_NODE_CLASS,    String.class);
        Utils.ensureColumn(nodeTable, COL_DISPLAY_NAME,  String.class);
        Utils.ensureColumn(nodeTable, COL_LABEL,         String.class);
        Utils.ensureColumn(nodeTable, COL_FAMILY,        String.class);
        Utils.ensureColumn(nodeTable, COL_GENE_SYMBOL,   String.class);
        Utils.ensureColumn(nodeTable, COL_UNIPROT_ID,    String.class);
        Utils.ensureColumn(nodeTable, COL_REP_UNIPROT,   String.class);
        Utils.ensureColumn(nodeTable, COL_TOTAL_NM,      Double.class);
        Utils.ensureColumn(nodeTable, COL_HAS_TOTAL,     Boolean.class);
        Utils.ensureColumn(nodeTable, COL_PROTEIN_CLASS, String.class);
        Utils.ensureColumn(nodeTable, COL_CLUSTER_ID,    String.class);
        Utils.ensureColumn(nodeTable, COL_CLUSTER_LABEL, String.class);
        Utils.ensureColumn(nodeTable, COL_FREE,          Double.class);
        Utils.ensureColumn(nodeTable, COL_PIECHART,      String.class);
        Utils.ensureListColumn(nodeTable, COL_BOUND,     Double.class);

        // 4. Ensure edge columns exist
        CyTable edgeTable = network.getDefaultEdgeTable();
        Utils.ensureColumn(edgeTable, COL_EDGE_CLASS, String.class);
        Utils.ensureColumn(edgeTable, COL_KD_NM,      Double.class);
        Utils.ensureColumn(edgeTable, COL_HAS_KD,     Boolean.class);
        Utils.ensureColumn(edgeTable, COL_BOUND,      Double.class);
        Utils.ensureColumn(edgeTable, COL_FRAC_BOUND, Double.class);
        Utils.ensureColumn(edgeTable, COL_SOURCE,     String.class);
        Utils.ensureColumn(edgeTable, COL_TARGET,     String.class);

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
            String nodeClass = data.has(COL_NODE_CLASS) ? data.get(COL_NODE_CLASS).getAsString() : "";

            CyNode node = network.addNode();
            nodeMap.put(id, node);

            CyRow row = network.getRow(node);
            row.set(CyNetwork.NAME, id);
            Utils.setStr(row, data, COL_NODE_CLASS);
            Utils.setStr(row, data, COL_DISPLAY_NAME);
            Utils.setStr(row, data, COL_LABEL);
            Utils.setStr(row, data, COL_FAMILY);
            Utils.setStr(row, data, COL_GENE_SYMBOL);
            Utils.setStr(row, data, COL_UNIPROT_ID);
            Utils.setStr(row, data, COL_REP_UNIPROT);
            Utils.setDbl(row, data, COL_TOTAL_NM);
            Utils.setBool(row, data, COL_HAS_TOTAL);
            Utils.setStr(row, data, COL_PROTEIN_CLASS);
            Utils.setStr(row, data, COL_CLUSTER_ID);
            Utils.setStr(row, data, COL_CLUSTER_LABEL);

            // Accumulate members for each cluster (skip cluster nodes themselves)
            if (!"cluster".equals(nodeClass)
                    && data.has(COL_CLUSTER_ID)
                    && !data.get(COL_CLUSTER_ID).isJsonNull()) {
                String clusterId = data.get(COL_CLUSTER_ID).getAsString();
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
            Utils.setStr(row, data, COL_EDGE_CLASS);
            Utils.setDbl(row, data, COL_KD_NM);
            Utils.setBool(row, data, COL_HAS_KD);
            row.set(COL_SOURCE, sourceId);
            row.set(COL_TARGET, targetId);
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
        applyStyle(view);
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

            applyClusterStyle(view, groupNode);
        }

        // 10. Run CoSE layout on the EDT
        monitor.setStatusMessage("Running CoSE layout…");
        monitor.setProgress(0.90);

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

        monitor.setProgress(1.0);
        monitor.setStatusMessage("Done — "
                + nodesJson.size() + " nodes, "
                + edgesJson.size() + " edges loaded.");
    }

    // ─── Visual style ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void applyStyle(CyNetworkView view) {
        VisualStyle style = vsf.createVisualStyle("Proteostasis");

        // Default node appearance
        style.setDefaultValue(BasicVisualLexicon.NODE_FILL_COLOR,      new Color(26, 37, 64));
        style.setDefaultValue(BasicVisualLexicon.NODE_BORDER_PAINT,    new Color(42, 74, 127));
        style.setDefaultValue(BasicVisualLexicon.NODE_BORDER_WIDTH,    2.0);
        style.setDefaultValue(BasicVisualLexicon.NODE_SIZE,            50.0);
        style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_COLOR,     Color.BLACK);
        style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_FONT_SIZE, 10);
        style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_BACKGROUND_COLOR,            new Color(200, 212, 232));
        style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_BACKGROUND_TRANSPARENCY,     120);
        style.setDefaultValue(BasicVisualLexicon.NODE_LABEL_BACKGROUND_SHAPE,            LabelBackgroundShapeVisualProperty.ROUND_RECTANGLE);

        // Passthrough mapping for node label
        PassthroughMapping<String, String> labelMapping = (PassthroughMapping<String, String>) passthroughFactory
          .createVisualMappingFunction(CyNetwork.NAME, String.class,
              BasicVisualLexicon.NODE_LABEL);
        style.addVisualMappingFunction(labelMapping);

        // Fill colour — discrete mapping on node_class
        DiscreteMapping<String, Paint> fillMap =
                (DiscreteMapping<String, Paint>) discreteFactory.createVisualMappingFunction(
                        COL_NODE_CLASS, String.class, BasicVisualLexicon.NODE_FILL_COLOR);
        fillMap.putMapValue("chaperone", new Color(60, 20,  10));
        fillMap.putMapValue("cc_tpr",    new Color(10, 40,  55));
        fillMap.putMapValue("cluster",   new Color(13, 31,  58));
        style.addVisualMappingFunction(fillMap);

        // Border colour — discrete mapping on node_class
        DiscreteMapping<String, Paint> borderMap =
                (DiscreteMapping<String, Paint>) discreteFactory.createVisualMappingFunction(
                        COL_NODE_CLASS, String.class, BasicVisualLexicon.NODE_BORDER_PAINT);
        borderMap.putMapValue("chaperone", new Color(249, 115,  22));
        borderMap.putMapValue("cc_tpr",    new Color( 34, 211, 238));
        borderMap.putMapValue("cluster",   new Color( 42,  90, 159));
        style.addVisualMappingFunction(borderMap);

        // Size — discrete mapping on node_class
        DiscreteMapping<String, Double> sizeMap =
                (DiscreteMapping<String, Double>) discreteFactory.createVisualMappingFunction(
                        COL_NODE_CLASS, String.class, BasicVisualLexicon.NODE_SIZE);
        sizeMap.putMapValue("chaperone", 80.0);
        sizeMap.putMapValue("cc_tpr",    48.0);
        style.addVisualMappingFunction(sizeMap);

        // Shape — discrete mapping on node_class
        DiscreteMapping<String, NodeShape> shapeMap =
                (DiscreteMapping<String, NodeShape>) discreteFactory.createVisualMappingFunction(
                        COL_NODE_CLASS, String.class, BasicVisualLexicon.NODE_SHAPE);
        shapeMap.putMapValue("chaperone", NodeShapeVisualProperty.ELLIPSE);
        shapeMap.putMapValue("cc_tpr",    NodeShapeVisualProperty.ELLIPSE);
        shapeMap.putMapValue("cluster",   NodeShapeVisualProperty.ROUND_RECTANGLE);
        style.addVisualMappingFunction(shapeMap);

        // Default edge appearance
        style.setDefaultValue(BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT, new Color(30, 58, 110));
        style.setDefaultValue(BasicVisualLexicon.EDGE_WIDTH,                   1.5);
        style.setDefaultValue(BasicVisualLexicon.EDGE_TARGET_ARROW_SHAPE,      ArrowShapeVisualProperty.NONE);

        // Edge color -- discrete mapping on edge_class
        DiscreteMapping<String, Paint> edgeColorMap =
                (DiscreteMapping<String, Paint>) discreteFactory.createVisualMappingFunction(
                        "node::Target_name", String.class, BasicVisualLexicon.EDGE_STROKE_UNSELECTED_PAINT);
        edgeColorMap.putMapValue("HSP70", Color.BLUE);
        edgeColorMap.putMapValue("HSP90", Color.RED);
        style.addVisualMappingFunction(edgeColorMap);

        // Edge width -- continuous mapping on frac_bound
        ContinuousMapping<Double, Double> edgeWidthMap =
            (ContinuousMapping<Double, Double>) continuousFactory.createVisualMappingFunction(
                    COL_FRAC_BOUND, Double.class, BasicVisualLexicon.EDGE_WIDTH);
        edgeWidthMap.addPoint(0.0, new BoundaryRangeValues<Double>(1.0,1.0,1.0));
        edgeWidthMap.addPoint(1.0, new BoundaryRangeValues<Double>(20.0,20.0,20.0));
        style.addVisualMappingFunction(edgeWidthMap);

        // Passthrough mapping for node pie charts
        VisualLexicon lex = registrar.getService(RenderingEngineManager.class).getDefaultVisualLexicon();
        VisualProperty customGraphics = lex.lookup(CyNode.class, "NODE_CUSTOMGRAPHICS_1");
        PassthroughMapping<String, String> chartMapping = (PassthroughMapping<String, String>) passthroughFactory
          .createVisualMappingFunction(COL_PIECHART, String.class, customGraphics);
        style.addVisualMappingFunction(chartMapping);

        vmm.addVisualStyle(style);
        vmm.setVisualStyle(style, view);
        style.apply(view);
    }

    private void applyClusterStyle(CyNetworkView view, CyNode clusterNode) {
        View<CyNode> nodeView = view.getNodeView(clusterNode);
        nodeView.setLockedValue(BasicVisualLexicon.NODE_LABEL_COLOR,     Color.BLACK);

        ObjectPosition clusterLabelPosition = new ObjectPosition(Position.NORTH, Position.SOUTH, Justification.JUSTIFY_CENTER, 0, 0);
        nodeView.setLockedValue(BasicVisualLexicon.NODE_LABEL_POSITION,  clusterLabelPosition);
    }

}
