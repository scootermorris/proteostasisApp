/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.Tunable;

import edu.ucsf.rbvi.proteostasisApp.Columns;
import edu.ucsf.rbvi.proteostasisApp.DataManager;
import edu.ucsf.rbvi.proteostasisApp.utils.Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Builds a solver request from the visible Cytoscape network.
 *
 * NEW behavior:
 * - HSP70/HSP90 are expanded internally into pool nodes:
 *     HSP70_u, HSP70_p, HSP90_u, HSP90_p
 * - visible edges to HSP70/HSP90 are expanded into pool-specific solver edges
 *   using kd_u_nM and kd_p_nM
 * - visible graph stays unchanged; only solver request is expanded
 */
public class SolveNetworkTask extends AbstractTask {

    private static final String SCHEMA_VERSION = "core-1.0";
    private static final String CONTRACT_TYPE  = "proteostasis_core_solver_request";

    // NEW: internal solver-only pool node ids
    private static final String HSP70_U = "HSP70_u";
    private static final String HSP70_P = "HSP70_p";
    private static final String HSP90_U = "HSP90_u";
    private static final String HSP90_P = "HSP90_p";

    // NEW: network-level phospho fraction fields
    private static final String COL_PCT_P_HSP70 = "pct_p_hsp70";
    private static final String COL_PCT_P_HSP90 = "pct_p_hsp90";

    @Tunable(description = "Maximum number of iterations")
    public int MAX_ITER = 400;

    @Tunable(description = "Tolerance")
    public double TOL = 1e-8;

    @Tunable(description = "Damping")
    public double DAMPING = 0.35;

    private final CyServiceRegistrar registrar;
    private final String solveUrl;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    SolveNetworkTask(CyServiceRegistrar registrar, String solveUrl) {
        this.registrar = registrar;
        this.solveUrl = solveUrl;
    }

    @Override
    public void run(TaskMonitor monitor) throws Exception {
        monitor.setTitle("Solve Proteostasis Network");

        CyNetwork network = registrar.getService(CyApplicationManager.class).getCurrentNetwork();
        if (network == null) {
            monitor.showMessage(TaskMonitor.Level.WARN, "No current network.");
            return;
        }

        String networkName = network.getRow(network).get(CyNetwork.NAME, String.class);
        monitor.setStatusMessage("Building request for: " + networkName);
        monitor.setProgress(0.1);

        Map<String, CyNode> nodeNameMap = new HashMap<>();
        JsonObject request = buildRequest(network, nodeNameMap);
        String requestBody = gson.toJson(request);

        System.out.println("Solver request: " + requestBody);

        if (cancelled) return;
        monitor.setStatusMessage("Sending request to " + solveUrl + " …");
        monitor.setProgress(0.3);

        String responseBody = post(solveUrl, requestBody);

        if (cancelled) return;
        monitor.setStatusMessage("Parsing response…");
        monitor.setProgress(0.8);

        DataManager.addData(network, nodeNameMap, responseBody, true);

        monitor.setProgress(1.0);
        monitor.setStatusMessage("Done.");
    }

    // NEW
    private double clamp01(Double x) {
        if (x == null) return 0.0;
        return Math.max(0.0, Math.min(1.0, x));
    }

    // NEW
    private Double getNetworkPctP(CyNetwork network, String colName) {
        try {
            return network.getRow(network).get(colName, Double.class);
        } catch (Exception e) {
            return null;
        }
    }

    // NEW
    private void addNode(JsonArray nodesArray, String id, String nodeClass, Double totalNm) {
        JsonObject data = new JsonObject();
        data.addProperty("id", id);
        if (nodeClass != null) data.addProperty("node_class", nodeClass);
        if (totalNm != null) data.addProperty("total_nM", totalNm);

        JsonObject nodeObj = new JsonObject();
        nodeObj.add("data", data);
        nodesArray.add(nodeObj);
    }

    // NEW
    private void addEdge(JsonArray edgesArray, String source, String target, String edgeClass, Double kdNm) {
        if (source == null || target == null || kdNm == null) return;

        JsonObject data = new JsonObject();
        data.addProperty("source", source);
        data.addProperty("target", target);
        if (edgeClass != null) data.addProperty("edge_class", edgeClass);
        data.addProperty("kd_nM", kdNm);

        JsonObject edgeObj = new JsonObject();
        edgeObj.add("data", data);
        edgesArray.add(edgeObj);
    }

    private JsonObject buildRequest(CyNetwork network, Map<String, CyNode> nodeNameMap) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("contract_type", CONTRACT_TYPE);

        JsonObject opts = new JsonObject();
        opts.addProperty("maxIter", MAX_ITER);
        opts.addProperty("tol", TOL);
        opts.addProperty("damping", DAMPING);
        root.add("solver_options", opts);

        JsonArray nodesArray = new JsonArray();
        JsonArray edgesArray = new JsonArray();

        // NEW: phospho fractions from network settings; default = 0
        double pctPHsp70 = clamp01(getNetworkPctP(network, COL_PCT_P_HSP70));
        double pctPHsp90 = clamp01(getNetworkPctP(network, COL_PCT_P_HSP90));

        // NEW: gather visible node totals first
        Double hsp70Total = null;
        Double hsp90Total = null;

        Set<String> addedNodeIds = new HashSet<>();

        for (CyNode cyNode : network.getNodeList()) {
            CyRow row = network.getRow(cyNode);
            String nodeClass = Utils.getStr(row, Columns.COL_NODE_CLASS);
            if ("cluster".equals(nodeClass)) continue;

            String id = row.get(CyNetwork.NAME, String.class);
            if (id == null) continue;

            nodeNameMap.put(id, cyNode);

            Double totalNm = Utils.getDbl(row, Columns.COL_TOTAL_NM);

            if ("HSP70".equals(id)) {
                hsp70Total = totalNm;
                continue; // CHANGED: do not send aggregate HSP70 directly
            }
            if ("HSP90".equals(id)) {
                hsp90Total = totalNm;
                continue; // CHANGED: do not send aggregate HSP90 directly
            }

            addNode(nodesArray, id, nodeClass, totalNm);
            addedNodeIds.add(id);
        }

        // NEW: add pool-resolved chaperone nodes
        double h70Total = hsp70Total != null ? hsp70Total : 0.0;
        double h90Total = hsp90Total != null ? hsp90Total : 0.0;

        double h70p = h70Total * pctPHsp70;
        double h70u = h70Total - h70p;
        double h90p = h90Total * pctPHsp90;
        double h90u = h90Total - h90p;

        addNode(nodesArray, HSP70_U, "chaperone_pool", h70u);
        addNode(nodesArray, HSP70_P, "chaperone_pool", h70p);
        addNode(nodesArray, HSP90_U, "chaperone_pool", h90u);
        addNode(nodesArray, HSP90_P, "chaperone_pool", h90p);

        // CHANGED: expand visible edges into pool-specific solver edges
        for (CyEdge cyEdge : network.getEdgeList()) {
            String sourceId = network.getRow(cyEdge.getSource()).get(CyNetwork.NAME, String.class);
            String targetId = network.getRow(cyEdge.getTarget()).get(CyNetwork.NAME, String.class);
            if (sourceId == null || targetId == null) continue;

            String srcClass = Utils.getStr(network.getRow(cyEdge.getSource()), Columns.COL_NODE_CLASS);
            String tgtClass = Utils.getStr(network.getRow(cyEdge.getTarget()), Columns.COL_NODE_CLASS);
            if ("cluster".equals(srcClass) || "cluster".equals(tgtClass)) continue;

            CyRow edgeRow = network.getRow(cyEdge);
            String edgeClass = Utils.getStr(edgeRow, Columns.COL_EDGE_CLASS);

            Double kdU = Utils.getDbl(edgeRow, Columns.COL_KD_U_NM);
            Double kdP = Utils.getDbl(edgeRow, Columns.COL_KD_P_NM);

            // visible edge involving HSP70 -> expand to HSP70_u and HSP70_p
            if ("HSP70".equals(sourceId) || "HSP70".equals(targetId)) {
                String other = "HSP70".equals(sourceId) ? targetId : sourceId;
                if (other == null || "HSP70".equals(other)) continue;

                addEdge(edgesArray, other, HSP70_U, edgeClass, kdU);
                addEdge(edgesArray, other, HSP70_P, edgeClass, kdP != null ? kdP : kdU);
                continue;
            }

            // visible edge involving HSP90 -> expand to HSP90_u and HSP90_p
            if ("HSP90".equals(sourceId) || "HSP90".equals(targetId)) {
                String other = "HSP90".equals(sourceId) ? targetId : sourceId;
                if (other == null || "HSP90".equals(other)) continue;

                addEdge(edgesArray, other, HSP90_U, edgeClass, kdU);
                addEdge(edgesArray, other, HSP90_P, edgeClass, kdP != null ? kdP : kdU);
                continue;
            }

            // CHANGED: non-HSP edges pass through only if explicit plain kd exists
            Double kdNm = Utils.getDbl(edgeRow, Columns.COL_KD_NM);
            if (kdNm != null) {
                addEdge(edgesArray, sourceId, targetId, edgeClass, kdNm);
            }
        }

        root.add("nodes", nodesArray);
        root.add("edges", edgesArray);

        return root;
    }

    private String post(String urlStr, String body) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(60_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        boolean ok = (status >= 200 && status < 300);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                ok ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            String responseBody = sb.toString();
            if (!ok) {
                throw new IOException("HTTP " + status + " from solver: " + responseBody);
            }
            System.out.println("Response: " + responseBody);
            return responseBody;
        }
    }
}