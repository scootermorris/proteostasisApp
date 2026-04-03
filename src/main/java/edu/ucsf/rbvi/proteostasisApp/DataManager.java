/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;

import edu.ucsf.rbvi.proteostasisApp.utils.Utils;

/**
 * Applies solver response data (free concentrations, complex concentrations,
 * fraction bound) to the current network's node and edge tables.
 *
 * All column writes go through {@link Utils} so they land in the
 * {@code prtsts::} namespace.
 */
public class DataManager {

    static String HSP70_PIE = "piechart: attributelist=\"" + Utils.mkCol(Columns.COL_BOUND)
            + "\" colorlist=\"grey,blue\" arcstart=90";
    static String HSP90_PIE = "piechart: attributelist=\"" + Utils.mkCol(Columns.COL_BOUND)
            + "\" colorlist=\"grey,red\" arcstart=90";
    static String CCTPR_PIE = "piechart: attributelist=\"" + Utils.mkCol(Columns.COL_BOUND)
            + "\" colorlist=\"grey,blue,red\" arcstart=90";

    public static void addData(CyNetwork network, Map<String, CyNode> nodeNameMap,
                        String jsonDataText, boolean update) {

        JsonObject dataRoot                   = JsonParser.parseString(jsonDataText).getAsJsonObject();
        Map<String, JsonElement> freeMap      = dataRoot.getAsJsonObject("freeById").asMap();
        Map<String, JsonElement> complexMap   = dataRoot.getAsJsonObject("complexByEdgeId").asMap();
        Map<String, JsonElement> fracBoundMap = dataRoot.getAsJsonObject("fracBoundByEdgeId").asMap();

        // Index complexMap by source → target → bound
        Map<String, Map<String, Double>> edgeMap = new HashMap<>();
        for (String edge : complexMap.keySet()) {
            Double bound = complexMap.get(edge).getAsDouble();
            String[] st  = edge.split("->");
            edgeMap.computeIfAbsent(st[0], k -> new LinkedHashMap<>()).put(st[1], bound);
        }

        // ── 1. Update free concentrations and chaperone pie charts ────────────
        for (String nodeName : nodeNameMap.keySet()) {
            CyRow row = network.getRow(nodeNameMap.get(nodeName));
            if (!freeMap.containsKey(nodeName)) continue; // clusters have no free value

            Double free = freeMap.get(nodeName).getAsDouble();
            Utils.setDbl(row, Columns.COL_FREE, free);

            if (isChaperone(row)) {
                Double total = Utils.getDbl(row, Columns.COL_TOTAL_NM);
                if (total == null) continue;
                Double bound = total - free;
                List<Double> boundList = new ArrayList<>();
                boundList.add(free);
                boundList.add(bound);
                Utils.setList(row, Columns.COL_BOUND, boundList);
                String tooltip = makeNodeTooltip(row, nodeName, boundList);
                Utils.setStr(row, Columns.COL_TOOLTIP, tooltip);
                if (!update) {
                    if ("HSP70".equals(nodeName)) Utils.setStr(row, Columns.COL_PIECHART, HSP70_PIE);
                    else if ("HSP90".equals(nodeName)) Utils.setStr(row, Columns.COL_PIECHART, HSP90_PIE);
                }
            }
        }

        // ── 2. Update edge bound / frac_bound, and cc_tpr node pie charts ─────
        for (String nodeName : nodeNameMap.keySet()) {
            CyNode source = nodeNameMap.get(nodeName);
            CyRow  row    = network.getRow(source);

            Map<String, Double> neMap = edgeMap.get(nodeName);
            if (neMap == null) continue;

            if (!freeMap.containsKey(nodeName)) continue;
            List<Double> boundList = new ArrayList<>();
            boundList.add(freeMap.get(nodeName).getAsDouble());

            for (String partner : neMap.keySet()) {
                Double bound  = neMap.get(partner);
                boundList.add(bound);

                CyNode target = nodeNameMap.get(partner);
                if (target == null) continue;
                List<CyEdge> edges = network.getConnectingEdgeList(source, target, CyEdge.Type.ANY);
                if (edges.isEmpty()) continue;
                CyRow eRow = network.getRow(edges.get(0));
                Utils.setDbl(eRow, Columns.COL_BOUND, bound);

                String key = nodeName + "->" + partner;
                JsonElement fracEl = fracBoundMap.get(key);
                if (fracEl != null) Utils.setDbl(eRow, Columns.COL_FRAC_BOUND, fracEl.getAsDouble());
                String tt = makeEdgeTooltip(eRow, key);
                Utils.setStr(eRow, Columns.COL_TOOLTIP, tt);
            }

            Utils.setList(row, Columns.COL_BOUND, boundList);
            if (!update) Utils.setStr(row, Columns.COL_PIECHART, CCTPR_PIE);
        }
    }

    public static String makeNodeTooltip(CyRow row, String nodeName, List<Double>boundList) {
        String ttString = "<html><h1>"+nodeName+"</h1><dl>";
        Double totalNm = Utils.getDbl(row, Columns.COL_TOTAL_NM);
        Double free = Utils.getDbl(row, Columns.COL_FREE);
        ttString = addRow(ttString, "total (nM)", totalNm);
        ttString = addRow(ttString, "free (nM)", free);
        if (boundList != null && boundList.size() > 2) {
            ttString = addRow(ttString, "bound to HSP70", boundList.get(1));
            ttString = addRow(ttString, "bound to HSP90", boundList.get(2));
        }
        ttString += "</dl></html>";
        return ttString;
    }

    public static String makeEdgeTooltip(CyRow row, String edgeName) {
        String ttString = "<html><h1>"+edgeName+"</h1><dl>";
        ttString = addRow(ttString, "kD (unphosphorylated)", Utils.getDbl(row, Columns.COL_KD_U_NM));
        ttString = addRow(ttString, "kD (phosphorylated)", Utils.getDbl(row, Columns.COL_KD_P_NM));
        ttString = addRow(ttString, "bound", Utils.getDbl(row, Columns.COL_BOUND));
        ttString = addRow(ttString, "fraction bound", Utils.getDbl(row, Columns.COL_FRAC_BOUND));
        ttString += "</dl></html>";
        return ttString;
    }

    static String addRow(String ttString, String term, Double data) {
        ttString += "<dt>"+term+"</dt>";
        ttString += "<dd><b>"+String.format("%.2f",data)+"</b></dd>";
        return ttString;
    }

    static boolean isChaperone(CyRow row) {
        String nodeClass = Utils.getStr(row, Columns.COL_NODE_CLASS);
        return "chaperone".equals(nodeClass);
    }
}
