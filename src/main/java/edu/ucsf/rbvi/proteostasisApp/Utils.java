/* vim: set ts=4 sw=4 et: */
package edu.ucsf.rbvi.proteostasisApp;

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

class Utils {
    static String fetchJson(String urlStr) throws IOException {
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

    static <T> void ensureColumn(CyTable table, String name, Class<T> type) {
        if (table.getColumn(name) == null)
            table.createColumn(name, type, false);
    }

    static <T> void ensureListColumn(CyTable table, String name, Class<T> type) {
        if (table.getColumn(name) == null)
            table.createListColumn(name, type, false);
    }

    static void setStr(CyRow row, JsonObject data, String key) {
        if (data.has(key) && !data.get(key).isJsonNull())
            row.set(key, data.get(key).getAsString());
    }

    static void setDbl(CyRow row, JsonObject data, String key) {
        if (data.has(key) && !data.get(key).isJsonNull())
            row.set(key, data.get(key).getAsDouble());
    }

    static void setBool(CyRow row, JsonObject data, String key) {
        if (data.has(key) && !data.get(key).isJsonNull())
            row.set(key, data.get(key).getAsBoolean());
    }

}

