/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp.utils;

import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;

import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import java.util.List;

public class Utils {

    // Define a namespace for our columns
    protected static final String NAMESPACE         = "prtsts::";

    public static String fetchJson(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    public static <T> void ensureColumn(CyTable table, String name, Class<T> type) {
        ensureColumn(table, name, type, true);
    }


    public static <T> void ensureColumn(CyTable table, String name, Class<T> type, boolean includeNamespace) {
        if (includeNamespace) {
            if (table.getColumn(NAMESPACE+name) == null)
                table.createColumn(NAMESPACE+name, type, false);
        } else {
            if (table.getColumn(name) == null)
                table.createColumn(name, type, false);
        }
    }

    public static <T> void ensureListColumn(CyTable table, String name, Class<T> type) {
        ensureListColumn(table, name, type, true);
    }

    public static <T> void ensureListColumn(CyTable table, String name, Class<T> type, boolean includeNamespace) {
        if (includeNamespace) {
            if (table.getColumn(NAMESPACE+name) == null)
                table.createListColumn(NAMESPACE+name, type, false);
        } else {
            if (table.getColumn(name) == null)
                table.createListColumn(name, type, false);
        }
    }

    public static void setStr(CyRow row, JsonObject data, String key) {
        if (data.has(key) && !data.get(key).isJsonNull())
            row.set(NAMESPACE+key, data.get(key).getAsString());
    }

    public static void setStr(CyRow row, String col, String data) {
        row.set(NAMESPACE+col, data);
    }

    public static String getStr(CyRow row, String col) {
        return row.get(NAMESPACE+col, String.class);
    }

    public static void setDbl(CyRow row, JsonObject data, String key) {
        if (data.has(key) && !data.get(key).isJsonNull())
            row.set(NAMESPACE+key, data.get(key).getAsDouble());
    }

    public static void setDbl(CyRow row, String col, Double data) {
        row.set(NAMESPACE+col, data);
    }

    public static Double getDbl(CyRow row, String col) {
        return row.get(NAMESPACE+col, Double.class);
    }

    public static void setBool(CyRow row, JsonObject data, String key) {
        if (data.has(key) && !data.get(key).isJsonNull())
            row.set(NAMESPACE+key, data.get(key).getAsBoolean());
    }

    public static void setList(CyRow row, String col, List<?> data) {
        row.set(NAMESPACE+col, data);
    }

    public static <T> List<T> getList(CyRow row, String col, Class<T> listClass) {
        return row.getList(NAMESPACE+col, listClass);
    }

    public static String mkCol(String col) {
        return NAMESPACE+col;
    }

}

