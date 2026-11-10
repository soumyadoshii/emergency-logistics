import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Tiny web server (JDK only). No logic here: it calls ReliefSystem and prints the state as JSON. */
public class Main {
    static ReliefSystem sys = ReliefSystem.demo();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", Main::handle);
        server.start();
        System.out.println("Open http://localhost:8080");
    }

    private static synchronized void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath(), q = ex.getRequestURI().getRawQuery();
        String body, type = "application/json";
        int status = 200;
        try {
            switch (path) {
                case "/":
                    type = "text/html; charset=utf-8";
                    Path f = Files.exists(Paths.get("frontend/index.html")) ? Paths.get("frontend/index.html") : Paths.get("../frontend/index.html");
                    body = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                    break;
                case "/api/state":    body = state(); break;
                case "/api/request":  sys.request(param(q, "center"), param(q, "resource"), Integer.parseInt(param(q, "qty"))); body = state(); break;
                case "/api/demo":     sys.loadSampleRequests(); body = state(); break;
                case "/api/dispatch": sys.dispatchWave(); body = state(); break;
                case "/api/road":     sys.toggleRoad(param(q, "name")); body = state(); break;
                case "/api/restock":  sys.restock(param(q, "w"), param(q, "r"), Integer.parseInt(param(q, "q"))); body = state(); break;
                case "/api/reset":    sys = ReliefSystem.demo(); body = state(); break;
                default: status = 404; body = "{\"error\":\"not found\"}";
            }
        } catch (Exception e) {
            status = 400;
            body = "{\"error\":" + quote(String.valueOf(e.getMessage())) + "}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    static String quote(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    static String reqJson(ReliefSystem.Request r, int index, int parent) {
        return "{\"i\":" + index + ",\"parent\":" + parent + ",\"center\":" + quote(sys.g.name[r.center])
             + ",\"resource\":" + quote(RoadGraph.RESOURCES[r.res]) + ",\"qty\":" + r.qty + ",\"severity\":" + r.severity + "}";
    }

    static String state() {
        RoadGraph g = sys.g;
        StringBuilder b = new StringBuilder("{");
        b.append("\"wave\":").append(sys.wave)
         .append(",\"stats\":{\"urgencyPushes\":").append(sys.pending.pushes).append(",\"urgencyPops\":").append(sys.pending.pops)
         .append(",\"dijkstraRuns\":").append(sys.dijkstraRuns).append(",\"dijkstraPops\":").append(sys.dijkstraPops)
         .append(",\"shipments\":").append(sys.shipmentsTotal).append(",\"delivered\":").append(sys.delivered)
         .append(",\"pendingUnits\":").append(sys.pendingUnits()).append("}");

        b.append(",\"resources\":[");
        for (int i = 0; i < RoadGraph.RESOURCES.length; i++) b.append(i > 0 ? "," : "").append(quote(RoadGraph.RESOURCES[i]));
        b.append("],\"nodes\":[");
        for (int i = 0; i < g.nodeCount; i++) {
            b.append(i > 0 ? "," : "").append("{\"name\":").append(quote(g.name[i])).append(",\"type\":\"").append(g.type[i])
             .append("\",\"x\":").append(g.x[i]).append(",\"y\":").append(g.y[i]).append(",\"severity\":").append(g.severity[i]).append(",\"stock\":[");
            for (int k = 0; k < g.stock[i].length; k++) b.append(k > 0 ? "," : "").append(g.stock[i][k]);
            b.append("]}");
        }
        b.append("],\"roads\":[");
        for (int i = 0; i < g.roadCount; i++) {
            RoadGraph.Road r = g.roads[i];
            b.append(i > 0 ? "," : "").append("{\"name\":").append(quote(r.name)).append(",\"a\":").append(quote(g.name[r.a]))
             .append(",\"b\":").append(quote(g.name[r.b])).append(",\"time\":").append(r.time).append(",\"capacity\":").append(r.capacity)
             .append(",\"residual\":").append(r.residual).append(",\"blocked\":").append(r.blocked).append("}");
        }
        b.append("],\"adjacency\":[");
        for (int i = 0; i < g.nodeCount; i++) {
            b.append(i > 0 ? "," : "").append("{\"node\":").append(quote(g.name[i])).append(",\"roads\":[");
            RoadGraph.Road[] rs = g.roadsOf(i);
            for (int k = 0; k < rs.length; k++) b.append(k > 0 ? "," : "").append(quote(rs[k].name));
            b.append("]}");
        }
        // urgency heap as the raw array (index, parent) and the order in which it would be served
        b.append("],\"heap\":[");
        for (int i = 0; i < sys.pending.size(); i++) b.append(i > 0 ? "," : "").append(reqJson(sys.pending.get(i), i, i == 0 ? -1 : (i - 1) / 2));
        b.append("],\"serveOrder\":[");
        MinHeap<ReliefSystem.Request> copy = sys.pending.copy();
        boolean first = true;
        while (!copy.isEmpty()) { b.append(first ? "" : ",").append(reqJson(copy.pop(), 0, 0)); first = false; }
        b.append("],\"shipments\":[");
        for (int i = 0; i < sys.lastWave.size(); i++) {
            ReliefSystem.Shipment s = sys.lastWave.get(i);
            b.append(i > 0 ? "," : "").append("{\"resource\":").append(quote(s.resource)).append(",\"from\":").append(quote(s.warehouse))
             .append(",\"to\":").append(quote(s.center)).append(",\"qty\":").append(s.qty).append(",\"time\":").append(s.time)
             .append(",\"bottleneck\":").append(s.bottleneck).append(",\"route\":").append(quote(s.route)).append(",\"roads\":[");
            for (int k = 0; k < s.roads.length; k++) b.append(k > 0 ? "," : "").append(quote(s.roads[k]));
            b.append("]}");
        }
        b.append("],\"log\":[");
        int from = Math.max(0, sys.log.size() - 80);
        for (int i = from; i < sys.log.size(); i++) b.append(i > from ? "," : "").append(quote(sys.log.get(i)));
        return b.append("]}").toString();
    }

    private static String param(String q, String key) {
        if (q != null)
            for (String pair : q.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && pair.substring(0, eq).equals(key)) return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        throw new IllegalArgumentException("missing parameter " + key);
    }
}
