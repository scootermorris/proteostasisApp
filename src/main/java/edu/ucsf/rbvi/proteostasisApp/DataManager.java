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

public class DataManager {

    static String HSP70_PIE = "piechart: attributelist=\"prtsts::bound\" colorlist=\"#e6c75f,#155efd\" arcstart=90";
    static String HSP90_PIE = "piechart: attributelist=\"prtsts::bound\" colorlist=\"#e6c75f,#fd3636\" arcstart=90";
    static String CCTPR_PIE = "piechart: attributelist=\"prtsts::bound\" colorlist=\"#e6c75f,#155efd,#fd3636\" arcstart=90";

    public static void addData(CyNetwork network, Map<String, CyNode> nodeNameMap, String jsonDataText, boolean update) {
        // 1. Get the data
        JsonObject dataRoot                   = JsonParser.parseString(jsonDataText).getAsJsonObject();
        Map<String, JsonElement> freeMap      = dataRoot.getAsJsonObject("freeById").asMap();
        Map<String, JsonElement> complexMap   = dataRoot.getAsJsonObject("complexByEdgeId").asMap();
        Map<String, JsonElement> fracBoundMap = dataRoot.getAsJsonObject("fracBoundByEdgeId").asMap();

        // 2. Process the complexMap to index by source then target
        Map<String, Map<String, Double>> edgeMap = new HashMap<>();
        for (String edge: complexMap.keySet()) {
            Double bound = complexMap.get(edge).getAsDouble();
            String[] st = edge.split("->");
            if (!edgeMap.containsKey(st[0]))
                edgeMap.put(st[0], new LinkedHashMap<String, Double>());
            edgeMap.get(st[0]).put(st[1], bound);
        }

        // 3. Update the node data and create a map of all of the node names
        for (String nodeName: nodeNameMap.keySet()) {
            CyRow row = network.getRow(nodeNameMap.get(nodeName));

            // Set the unbound (free) value (note that clusters don't have free values)
            if (!freeMap.containsKey(nodeName))
                continue;  // Skip over the clusters
            Double free = freeMap.get(nodeName).getAsDouble();
            Utils.setDbl(row, Columns.COL_FREE, free);

            // OK, we need to watch out for our chaperone's
            if (isChaperone(row)) {
                // Bound is total - free
                List<Double> boundList = new ArrayList<>();
                Double total = Utils.getDbl(row, Columns.COL_TOTAL_NM);
                Double bound = total - free;
                boundList.add(free);
                boundList.add(bound);
                Utils.setList(row, Columns.COL_BOUND, boundList);
                if (!update) {
                    if (nodeName.equals("HSP70"))
                        Utils.setStr(row, Columns.COL_PIECHART, HSP70_PIE);
                    else if (nodeName.equals("HSP90"))
                        Utils.setStr(row, Columns.COL_PIECHART, HSP90_PIE);
                }
            }
        }

        // 4. Update the edge data (and node bound list).
        for (String nodeName: nodeNameMap.keySet()) {
            CyNode source = nodeNameMap.get(nodeName);
            CyRow row = network.getRow(source);

            // Set the bound values
            Map<String, Double> neMap = edgeMap.get(nodeName);
            if (neMap == null)
                continue;

            // This is the boundList we'll use to create the pie chart
            List<Double> boundList = new ArrayList<>();
            boundList.add(freeMap.get(nodeName).getAsDouble());
            for (String partner: neMap.keySet()) {
                Double bound = neMap.get(partner);
                boundList.add(bound);
                CyNode target = nodeNameMap.get(partner);
                CyEdge edge = network.getConnectingEdgeList(source, target, CyEdge.Type.ANY).get(0);
                CyRow eRow = network.getRow(edge);
                Utils.setDbl(eRow, Columns.COL_BOUND, bound);

                String key = nodeName+"->"+partner; // this avoids having to create yet another map
                Double fracBound = fracBoundMap.get(key).getAsDouble();
                Utils.setDbl(eRow, Columns.COL_FRAC_BOUND, fracBound);
            }

            Utils.setList(row, Columns.COL_BOUND, boundList);
            String tooltip = makeTooltip(row, nodeName, boundList);
            Utils.setStr(row, Columns.COL_TOOLTIP, tooltip);
            if (!update)
                Utils.setStr(row, Columns.COL_PIECHART, CCTPR_PIE);
        }
    }

    static String makeTooltip(CyRow row, String nodeName, List<Double>boundList) {
        String ttString = "<html><h1>"+nodeName+"</h1><dl>";
        Double totalNm = Utils.getDbl(row, Columns.COL_TOTAL_NM);
        ttString = addRow(ttString, "total (nM)", totalNm);
        ttString = addRow(ttString, "free (nM)", boundList.get(0));
        ttString = addRow(ttString, "bound to HSP70", boundList.get(1));
        ttString = addRow(ttString, "bound to HSP90", boundList.get(2));
        ttString += "</dl></html>";
        return ttString;
    }

    static String addRow(String ttString, String term, Double data) {
        ttString += "<dt>"+term+"</dt>";
        ttString += "<dd><b>"+data.toString()+"</b></dd>";
        return ttString;
    }

    static boolean isChaperone(CyRow row) {
        String nodeClass = Utils.getStr(row, Columns.COL_NODE_CLASS);
        if (nodeClass == null) return false;

        if (nodeClass.equals("chaperone"))
            return true;
        return false;
    }

}

