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

import edu.ucsf.rbvi.proteostasisApp.utils.Utils;

/**
 * Applies solver response data (free concentrations, complex concentrations,
 * fraction bound) to the current network's node and edge tables.
 *
 * NEW behavior:
 * - understands internal solver pool nodes HSP70_u/HSP70_p/HSP90_u/HSP90_p
 * - collapses pool-resolved results back onto visible aggregate nodes
 */
public class DataManager {

    // NEW: solver-only pool node ids
    static final String HSP70_U = "HSP70_u";
    static final String HSP70_P = "HSP70_p";
    static final String HSP90_U = "HSP90_u";
    static final String HSP90_P = "HSP90_p";

    // CHANGED: richer pies
    static String HSP70_PIE = "piechart: attributelist=\"" + Utils.mkCol(Columns.COL_BOUND)
            + "\" colorlist=\"#155efd,#2563eb,#7c3aed,#6d28d9\" arcstart=90";
    static String HSP90_PIE = "piechart: attributelist=\"" + Utils.mkCol(Columns.COL_BOUND)
            + "\" colorlist=\"#dc2626,#b91c1c,#991b1b,#7f1d1d\" arcstart=90";
    static String CCTPR_PIE = "piechart: attributelist=\"" + Utils.mkCol(Columns.COL_BOUND)
            + "\" colorlist=\"#a17800,#155efd,#7c3aed,#dc2626,#991b1b\" arcstart=90";

    public static void addData(CyNetwork network, Map<String, CyNode> nodeNameMap,
                        String jsonDataText, boolean update) {

        JsonObject dataRoot = JsonParser.parseString(jsonDataText).getAsJsonObject();

        JsonObject freeObj      = dataRoot.has("freeById") ? dataRoot.getAsJsonObject("freeById") : new JsonObject();
        JsonObject complexObj   = dataRoot.has("complexByEdgeId") ? dataRoot.getAsJsonObject("complexByEdgeId") : new JsonObject();
        JsonObject fracBoundObj = dataRoot.has("fracBoundByEdgeId") ? dataRoot.getAsJsonObject("fracBoundByEdgeId") : new JsonObject();

        Map<String, JsonElement> freeMap      = freeObj.asMap();
        Map<String, JsonElement> complexMap   = complexObj.asMap();
        Map<String, JsonElement> fracBoundMap = fracBoundObj.asMap();

        // source -> target -> bound
        Map<String, Map<String, Double>> edgeMap = new HashMap<>();
        for (String edge : complexMap.keySet()) {
            Double bound = complexMap.get(edge).getAsDouble();
            String[] st  = edge.split("->");
            if (st.length != 2) continue;
            edgeMap.computeIfAbsent(st[0], k -> new LinkedHashMap<>()).put(st[1], bound);
        }

        // NEW: pool free values
        double free70u = getDouble(freeMap, HSP70_U);
        double free70p = getDouble(freeMap, HSP70_P);
        double free90u = getDouble(freeMap, HSP90_U);
        double free90p = getDouble(freeMap, HSP90_P);

        // NEW: total pool-bound values from complex map
        double bound70u = totalIncomingTo(edgeMap, HSP70_U);
        double bound70p = totalIncomingTo(edgeMap, HSP70_P);
        double bound90u = totalIncomingTo(edgeMap, HSP90_U);
        double bound90p = totalIncomingTo(edgeMap, HSP90_P);

        // ── 1. Update visible node free values and pie charts ─────────────────
        for (String nodeName : nodeNameMap.keySet()) {
            CyNode node = nodeNameMap.get(nodeName);
            CyRow row = network.getRow(node);

            if (isChaperoneByName(nodeName)) {
                // CHANGED: visible chaperones are collapsed from pool values
                double free = 0.0;
                List<Double> boundList = new ArrayList<>();

                if ("HSP70".equals(nodeName)) {
                    free = free70u + free70p;
                    boundList.add(free70u);
                    boundList.add(free70p);
                    boundList.add(bound70u);
                    boundList.add(bound70p);
                    Utils.setList(row, Columns.COL_BOUND, boundList);
                    Utils.setDbl(row, Columns.COL_FREE, free);
                    Utils.setStr(row, Columns.COL_TOOLTIP, makeChaperoneTooltip(row, nodeName, boundList));
                    if (!update) Utils.setStr(row, Columns.COL_PIECHART, HSP70_PIE);
                } else if ("HSP90".equals(nodeName)) {
                    free = free90u + free90p;
                    boundList.add(free90u);
                    boundList.add(free90p);
                    boundList.add(bound90u);
                    boundList.add(bound90p);
                    Utils.setList(row, Columns.COL_BOUND, boundList);
                    Utils.setDbl(row, Columns.COL_FREE, free);
                    Utils.setStr(row, Columns.COL_TOOLTIP, makeChaperoneTooltip(row, nodeName, boundList));
                    if (!update) Utils.setStr(row, Columns.COL_PIECHART, HSP90_PIE);
                }

                continue;
            }

            // Non-chaperone visible nodes
            if (!freeMap.containsKey(nodeName)) continue;

            double free = freeMap.get(nodeName).getAsDouble();
            Utils.setDbl(row, Columns.COL_FREE, free);

            double h70u = getBound(edgeMap, nodeName, HSP70_U);
            double h70p = getBound(edgeMap, nodeName, HSP70_P);
            double h90u = getBound(edgeMap, nodeName, HSP90_U);
            double h90p = getBound(edgeMap, nodeName, HSP90_P);

            List<Double> boundList = new ArrayList<>();
            boundList.add(free);
            boundList.add(h70u);
            boundList.add(h70p);
            boundList.add(h90u);
            boundList.add(h90p);

            Utils.setList(row, Columns.COL_BOUND, boundList);
            Utils.setStr(row, Columns.COL_TOOLTIP, makeNodeTooltip(row, nodeName, boundList));

            if (!update) Utils.setStr(row, Columns.COL_PIECHART, CCTPR_PIE);
        }

        // ── 2. Update visible edges from collapsed pool data ──────────────────
        for (CyEdge edge : network.getEdgeList()) {
            CyRow eRow = network.getRow(edge);
            String sourceName = network.getRow(edge.getSource()).get(CyNetwork.NAME, String.class);
            String targetName = network.getRow(edge.getTarget()).get(CyNetwork.NAME, String.class);

            if (sourceName == null || targetName == null) continue;

            String left = sourceName;
            String right = targetName;

            Double bound = null;
            Double frac = null;

            // CHANGED: collapse pool-specific edge contributions back to visible HSP edge
            if ("HSP70".equals(left)) {
                bound = getBound(edgeMap, right, HSP70_U) + getBound(edgeMap, right, HSP70_P);
                frac  = getFracForCollapsedEdge(fracBoundMap, right, HSP70_U, HSP70_P);
            } else if ("HSP70".equals(right)) {
                bound = getBound(edgeMap, left, HSP70_U) + getBound(edgeMap, left, HSP70_P);
                frac  = getFracForCollapsedEdge(fracBoundMap, left, HSP70_U, HSP70_P);
            } else if ("HSP90".equals(left)) {
                bound = getBound(edgeMap, right, HSP90_U) + getBound(edgeMap, right, HSP90_P);
                frac  = getFracForCollapsedEdge(fracBoundMap, right, HSP90_U, HSP90_P);
            } else if ("HSP90".equals(right)) {
                bound = getBound(edgeMap, left, HSP90_U) + getBound(edgeMap, left, HSP90_P);
                frac  = getFracForCollapsedEdge(fracBoundMap, left, HSP90_U, HSP90_P);
            } else {
                // passthrough for non-HSP edges if any plain result exists
                String key1 = left + "->" + right;
                String key2 = right + "->" + left;
                if (complexMap.containsKey(key1)) bound = complexMap.get(key1).getAsDouble();
                else if (complexMap.containsKey(key2)) bound = complexMap.get(key2).getAsDouble();

                if (fracBoundMap.containsKey(key1)) frac = fracBoundMap.get(key1).getAsDouble();
                else if (fracBoundMap.containsKey(key2)) frac = fracBoundMap.get(key2).getAsDouble();
            }

            if (bound != null) Utils.setDbl(eRow, Columns.COL_BOUND, bound);
            if (frac != null) Utils.setDbl(eRow, Columns.COL_FRAC_BOUND, frac);

            Utils.setStr(eRow, Columns.COL_TOOLTIP,
                    makeEdgeTooltip(eRow, left + "->" + right));
        }
    }

    // NEW
    static double getDouble(Map<String, JsonElement> m, String key) {
        JsonElement el = m.get(key);
        return el == null ? 0.0 : el.getAsDouble();
    }

    // NEW
    static double getBound(Map<String, Map<String, Double>> edgeMap, String source, String target) {
        Map<String, Double> inner = edgeMap.get(source);
        if (inner == null) return 0.0;
        Double v = inner.get(target);
        return v == null ? 0.0 : v;
    }

    // NEW
    static double totalIncomingTo(Map<String, Map<String, Double>> edgeMap, String target) {
        double total = 0.0;
        for (Map<String, Double> inner : edgeMap.values()) {
            Double v = inner.get(target);
            if (v != null) total += v;
        }
        return total;
    }

    // NEW
    static Double getFracForCollapsedEdge(Map<String, JsonElement> fracBoundMap,
                                          String source,
                                          String target1,
                                          String target2) {
        double total = 0.0;
        boolean found = false;

        JsonElement e1 = fracBoundMap.get(source + "->" + target1);
        if (e1 != null) {
            total += e1.getAsDouble();
            found = true;
        }

        JsonElement e2 = fracBoundMap.get(source + "->" + target2);
        if (e2 != null) {
            total += e2.getAsDouble();
            found = true;
        }

        return found ? total : null;
    }

    // NEW
    static boolean isChaperoneByName(String nodeName) {
        return "HSP70".equals(nodeName) || "HSP90".equals(nodeName);
    }

    public static String makeNodeTooltip(CyRow row, String nodeName, List<Double> boundList) {
        String ttString = "<html><h1>" + safeText(nodeName) + "</h1><dl>";
        Double totalNm = Utils.getDbl(row, Columns.COL_TOTAL_NM);
        Double free = Utils.getDbl(row, Columns.COL_FREE);

        ttString = addRow(ttString, "total (nM)", totalNm);
        ttString = addRow(ttString, "free (nM)", free);

        // CHANGED: pool-resolved CC-TPR tooltip
        if (boundList != null && boundList.size() == 5) {
            ttString = addRow(ttString, "bound to HSP70_u", boundList.get(1));
            ttString = addRow(ttString, "bound to HSP70_p", boundList.get(2));
            ttString = addRow(ttString, "bound to HSP90_u", boundList.get(3));
            ttString = addRow(ttString, "bound to HSP90_p", boundList.get(4));
        } else if (boundList != null && boundList.size() == 3) {
            ttString = addRow(ttString, "bound to HSP70", boundList.get(1));
            ttString = addRow(ttString, "bound to HSP90", boundList.get(2));
        } else if (boundList != null && boundList.size() == 2) {
            ttString = addRow(ttString, "bound", boundList.get(1));
        }

        ttString += "</dl></html>";
        return ttString;
    }

    // NEW
    public static String makeChaperoneTooltip(CyRow row, String nodeName, List<Double> boundList) {
        String ttString = "<html><h1>" + safeText(nodeName) + "</h1><dl>";
        Double totalNm = Utils.getDbl(row, Columns.COL_TOTAL_NM);
        Double free = Utils.getDbl(row, Columns.COL_FREE);

        ttString = addRow(ttString, "total (nM)", totalNm);
        ttString = addRow(ttString, "free total (nM)", free);

        if (boundList != null && boundList.size() == 4) {
            ttString = addRow(ttString, "free u", boundList.get(0));
            ttString = addRow(ttString, "free p", boundList.get(1));
            ttString = addRow(ttString, "bound u", boundList.get(2));
            ttString = addRow(ttString, "bound p", boundList.get(3));
        }

        ttString += "</dl></html>";
        return ttString;
    }

    public static String makeEdgeTooltip(CyRow row, String edgeName) {
        String ttString = "<html><h1>" + safeText(edgeName) + "</h1><dl>";
        ttString = addRow(ttString, "kD (unphosphorylated)", Utils.getDbl(row, Columns.COL_KD_U_NM));
        ttString = addRow(ttString, "kD (phosphorylated)", Utils.getDbl(row, Columns.COL_KD_P_NM));
        ttString = addRow(ttString, "bound", Utils.getDbl(row, Columns.COL_BOUND));
        ttString = addRow(ttString, "fraction bound", Utils.getDbl(row, Columns.COL_FRAC_BOUND));
        ttString += "</dl></html>";
        return ttString;
    }

    static String addRow(String ttString, String term, Double data) {
        ttString += "<dt>" + safeText(term) + "</dt>";
        if (data == null) {
            ttString += "<dd><b>—</b></dd>";
        } else {
            ttString += "<dd><b>" + String.format("%.2f", data) + "</b></dd>";
        }
        return ttString;
    }

    static String safeText(String text) {
        return text == null ? "—" : text;
    }
}