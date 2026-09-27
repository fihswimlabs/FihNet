package net.fihnet.client.util;

import java.util.*;

public class JsonObject {
    private final Map<String, Object> data = new HashMap<>();
    
    public void set(String key, Object value) { data.put(key, value); }
    public Object get(String key) { return data.get(key); }
    public boolean containsKey(String key) { return data.containsKey(key); }
    
    public String getString(String key) {
        Object v = data.get(key);
        return (v instanceof String) ? (String) v : null;
    }
    
    public String getString(String key, String def) {
        Object v = data.get(key);
        return (v instanceof String) ? (String) v : def;
    }
    
    public int getInt(String key) {
        Object v = data.get(key);
        return (v instanceof Number) ? ((Number)v).intValue() : 0;
    }
    
    public long getLong(String key) {
        Object v = data.get(key);
        return (v instanceof Number) ? ((Number)v).longValue() : 0L;
    }
    
    public long getLong(String key, long def) {
        Object v = data.get(key);
        return (v instanceof Number) ? ((Number)v).longValue() : def;
    }
    
    public boolean getBoolean(String key) {
        Object v = data.get(key);
        return (v instanceof Boolean) ? (Boolean)v : false;
    }
    
    public Map<String, Object> getData() { return new HashMap<>(data); }
    
        @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : data.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(e.getKey()).append("\": ");
            Object v = e.getValue();
            if (v instanceof String s) { sb.append("\"").append(esc(s)).append("\""); }
            else if (v instanceof Boolean || v instanceof Number) { sb.append(v); }
            else if (v instanceof Map) { sb.append(nest((Map<?,?>)v)); }
            else if (v instanceof List) { sb.append(lst((List<?>)v)); }
            else { sb.append("null"); }
            first = false;
        }
        return sb.append("}").toString();
    }
    
    private String nest(Map<?,?> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean f = true;
        for (var e : m.entrySet()) {
            if (!f) sb.append(", ");
            sb.append("\"").append(e.getKey()).append("\": ");
            Object v = e.getValue();
            if (v instanceof String s) { sb.append("\"").append(esc(s)).append("\""); }
            else if (v instanceof Boolean || v instanceof Number) { sb.append(v); }
            else { sb.append("null"); }
            f = false;
        }
        return sb.append("}").toString();
    }
    
    private String lst(List<?> l) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(", ");
            Object v = l.get(i);
            if (v instanceof String s) { sb.append("\"").append(esc(s)).append("\""); }
            else if (v instanceof Map) { sb.append(nest((Map<?,?>)v)); }
            else { sb.append(v); }
        }
        return sb.append("]").toString();
    }
    
        private String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    public static JsonObject fromString(String json) {
        JsonObject obj = new JsonObject();
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) return obj;
        json = json.substring(1, json.length() - 1).trim();
        if (json.isEmpty()) return obj;
        
        for (String pair : splitPairs(json)) {
            int colon = -1;
            boolean inStr = false;
            for (int i = 0; i < pair.length(); i++) {
                char c = pair.charAt(i);
                if (c == '"') inStr = !inStr;
                if (!inStr && c == ':') { colon = i; break; }
            }
            if (colon < 0) continue;
            String key = pair.substring(0, colon).trim();
            key = key.substring(1, key.length() - 1);
            String val = pair.substring(colon + 1).trim();
            obj.data.put(key, parseVal(val));
        }
        return obj;
    }
    
    private static String[] splitPairs(String json) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inStr = false;
        int bd = 0, brd = 0;
        for (char c : json.toCharArray()) {
            if (c == '"') inStr = !inStr;
            if (!inStr) {
                if (c == '{') bd++;
                if (c == '}') bd--;
                if (c == '[') brd++;
                if (c == ']') brd--;
            }
            if (c == ',' && !inStr && bd == 0 && brd == 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }
    
    private static Object parseVal(String v) {
        v = v.trim();
        if (v.isEmpty()) return null;
        if (v.startsWith("\"") && v.endsWith("\"")) return v.substring(1, v.length() - 1);
        if (v.equals("true")) return Boolean.TRUE;
        if (v.equals("false")) return Boolean.FALSE;
        if (v.equals("null")) return null;
        if (v.startsWith("[") && v.endsWith("]")) {
            List<Object> list = new ArrayList<>();
            String inner = v.substring(1, v.length() - 1).trim();
            if (!inner.isEmpty()) {
                for (String item : splitPairs(inner)) {
                    Object p = parseVal(item);
                    if (p != null) list.add(p);
                }
            }
            return list;
        }
        if (v.startsWith("{") && v.endsWith("}")) return fromString(v).getData();
        try {
            if (v.contains(".")) return Double.parseDouble(v);
            return Long.parseLong(v);
        } catch (NumberFormatException e) { return v; }
    }
}

