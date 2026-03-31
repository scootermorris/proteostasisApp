package edu.ucsf.rbvi.proteostasisApp.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads all nodes and edges from the current network, builds the
 * proteostasis solver request JSON, POSTs it to the remote solver,
 * and prints the response (freeById, complexByEdgeId, fracBoundByEdgeId)
 * to the Cytoscape Task Console.
 *
 * Request shape:
 * {
 *   "schema_version": "core-1.0",
 *   "contract_type": "proteostasis_core_solver_request",
 *   "solver_options": { "maxIter": 400, "tol": 1e-8, "damping": 0.35 },
 *   "nodes": [ { "data": { "id": "...", "node_class": "...", "total_nM": ... } } ],
 *   "edges": [ { "data": { "source": "...", "target": "...",
 *                           "edge_class": "...", "kd_nM": ... } } ]
 * }
 *
 * Response shape (proteostasis_core_solver_response):
 * {
 *   "freeById":        { "HSP70": 7955.0, ... },
 *   "complexByEdgeId": { "HOP->HSP70": 210.0, ... },
 *   "fracBoundByEdgeId":{ "HOP->HSP70": 0.42, ... },
 *   "meta":            { "status": "success", ... }
 * }
 */
public class SolveNetworkTask extends AbstractTask {

    // ── Solver request constants ──────────────────────────────────────────────
    private static final String SCHEMA_VERSION  = "core-1.0";
    private static final String CONTRACT_TYPE   = "proteostasis_core_solver_request";


		@Tunable(description="Maximum number of iterations")
    public int    MAX_ITER        = 400;

		@Tunable(description="Tolerance")
    public double TOL             = 1e-8;

		@Tunable(description="Damping")
    public double DAMPING         = 0.35;

    private final CyServiceRegistrar   registrar;
    private final String               solveUrl;
    private final Gson                 gson = new GsonBuilder().setPrettyPrinting().create();

    SolveNetworkTask(CyServiceRegistrar registrar, String solveUrl) {
        this.registrar = registrar;
        this.solveUrl   = solveUrl;
    }

    @Override
    public void run(TaskMonitor monitor) throws Exception {
        monitor.setTitle("Solve Proteostasis Network");

        // ── 1. Get current network ────────────────────────────────────────────
        CyNetwork network = registrar.getService(CyApplicationManager.class).getCurrentNetwork();
        if (network == null) {
            monitor.showMessage(TaskMonitor.Level.WARN, "No current network.");
            return;
        }
        String networkName = network.getRow(network).get(CyNetwork.NAME, String.class);
        monitor.setStatusMessage("Building request for: " + networkName);
        monitor.setProgress(0.1);

        // ── 2. Build request JSON ─────────────────────────────────────────────
				Map<String, CyNode> nodeNameMap = new HashMap<>();
        JsonObject request = buildRequest(network, nodeNameMap);
        String requestBody = gson.toJson(request);

        if (cancelled) return;
        monitor.setStatusMessage("Sending request to " + solveUrl + " …");
        monitor.setProgress(0.3);

        // ── 3. POST ───────────────────────────────────────────────────────────
        String responseBody = post(solveUrl, requestBody);

        if (cancelled) return;
        monitor.setStatusMessage("Parsing response…");
        monitor.setProgress(0.8);

        // ── 4. Parse and display response ─────────────────────────────────────
        // JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();
        // printResponse(response, monitor);
        DataManager.addData(network, nodeNameMap, responseBody, true);


        monitor.setProgress(1.0);
        monitor.setStatusMessage("Done.");
    }

    // ─── Request builder ──────────────────────────────────────────────────────

    private JsonObject buildRequest(CyNetwork network, Map<String, CyNode> nodeNameMap) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("contract_type",  CONTRACT_TYPE);

        // solver_options
        JsonObject opts = new JsonObject();
        opts.addProperty("maxIter",  MAX_ITER);
        opts.addProperty("tol",      TOL);
        opts.addProperty("damping",  DAMPING);
        root.add("solver_options", opts);

        // nodes — skip cluster nodes (node_class == "cluster"), include everything else
        JsonArray nodesArray = new JsonArray();
        for (CyNode cyNode : network.getNodeList()) {
            CyRow row       = network.getRow(cyNode);
            String nodeClass = Utils.getStr(row,Columns.COL_NODE_CLASS);
            if ("cluster".equals(nodeClass)) continue;

            String id = row.get(CyNetwork.NAME, String.class);
            if (id == null) continue;
						nodeNameMap.put(id, cyNode);

            JsonObject data = new JsonObject();
            data.addProperty("id", id);
            if (nodeClass != null) data.addProperty("node_class", nodeClass);

            Double totalNm = Utils.getDbl(row, Columns.COL_TOTAL_NM);
            if (totalNm != null) data.addProperty("total_nM", totalNm);

            JsonObject nodeObj = new JsonObject();
            nodeObj.add("data", data);
            nodesArray.add(nodeObj);
        }
        root.add("nodes", nodesArray);

        // edges
        JsonArray edgesArray = new JsonArray();
        for (CyEdge cyEdge : network.getEdgeList()) {
            String sourceId = network.getRow(cyEdge.getSource()).get(CyNetwork.NAME, String.class);
            String targetId = network.getRow(cyEdge.getTarget()).get(CyNetwork.NAME, String.class);
            if (sourceId == null || targetId == null) continue;

            // Skip group-internal edges (cluster nodes are excluded from solve)
            String srcClass = Utils.getStr(network.getRow(cyEdge.getSource()),Columns.COL_NODE_CLASS);
            String tgtClass = Utils.getStr(network.getRow(cyEdge.getTarget()),Columns.COL_NODE_CLASS);
            if ("cluster".equals(srcClass) || "cluster".equals(tgtClass)) continue;

            CyRow edgeRow   = network.getRow(cyEdge);
            String edgeClass = Utils.getStr(edgeRow,Columns.COL_EDGE_CLASS);
            Double kdNm      = Utils.getDbl(edgeRow,Columns.COL_KD_NM);

            JsonObject data = new JsonObject();
            data.addProperty("source", sourceId);
            data.addProperty("target", targetId);
            if (edgeClass != null) data.addProperty("edge_class", edgeClass);
            if (kdNm      != null) data.addProperty("kd_nM",      kdNm);

            JsonObject edgeObj = new JsonObject();
            edgeObj.add("data", data);
            edgesArray.add(edgeObj);
        }
        root.add("edges", edgesArray);

        return root;
    }

    // ─── HTTP POST ────────────────────────────────────────────────────────────

    private String post(String urlStr, String body) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept",       "application/json");
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
            return responseBody;
        }
    }

    // ─── Response printer ─────────────────────────────────────────────────────

    private void printResponse(JsonObject response, TaskMonitor monitor) {
        // meta / status
        if (response.has("meta")) {
            JsonObject meta = response.getAsJsonObject("meta");
            String status = meta.has("status") ? meta.get("status").getAsString() : "unknown";
            String note   = meta.has("note")   ? meta.get("note").getAsString()   : "";
            monitor.showMessage(TaskMonitor.Level.INFO,
                    "Solver status: " + status + (note.isEmpty() ? "" : " — " + note));
        }

        // freeById
        if (response.has("freeById")) {
            System.out.println("\n── Free concentrations (nM) ──────────────────────");
            System.out.printf("%-20s  %12s%n", "Node", "Free (nM)");
            System.out.println("-".repeat(36));
            response.getAsJsonObject("freeById").entrySet().forEach(e -> {
                String line = String.format("%-20s  %12.3f", e.getKey(), e.getValue().getAsDouble());
                System.out.println(line);
                monitor.showMessage(TaskMonitor.Level.INFO, "free  " + line);
            });
        }

        // complexByEdgeId
        if (response.has("complexByEdgeId")) {
            System.out.println("\n── Complex concentrations (nM) ───────────────────");
            System.out.printf("%-30s  %12s%n", "Edge", "Complex (nM)");
            System.out.println("-".repeat(46));
            response.getAsJsonObject("complexByEdgeId").entrySet().forEach(e -> {
                String line = String.format("%-30s  %12.3f", e.getKey(), e.getValue().getAsDouble());
                System.out.println(line);
                monitor.showMessage(TaskMonitor.Level.INFO, "complex  " + line);
            });
        }

        // fracBoundByEdgeId
        if (response.has("fracBoundByEdgeId")) {
            System.out.println("\n── Fraction bound ────────────────────────────────");
            System.out.printf("%-30s  %12s%n", "Edge", "Frac bound");
            System.out.println("-".repeat(46));
            response.getAsJsonObject("fracBoundByEdgeId").entrySet().forEach(e -> {
                String line = String.format("%-30s  %12.4f", e.getKey(), e.getValue().getAsDouble());
                System.out.println(line);
                monitor.showMessage(TaskMonitor.Level.INFO, "frac  " + line);
            });
        }

        System.out.println("=".repeat(48));
    }
}
