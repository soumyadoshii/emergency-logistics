import java.util.ArrayList;

/**
 * The decision engine. It answers: "several centers want the same limited stock over roads that can fail
 * or fill up - who gets what, from where, by which route?"
 *
 * Policy (implemented by the URGENCY HEAP):
 *   1. higher center severity first (hospital 5 ... camp 1)
 *   2. then more critical resource first (MEDICINE, WATER, FOOD)
 *   3. then earlier request first
 * Each popped request is served from the NEAREST warehouse that has stock (Dijkstra + heap, on the graph),
 * limited by stock and by the free capacity of the weakest road on the route. Any remainder goes back
 * into the heap and is served from the next-nearest warehouse / another road.
 */
public class ReliefSystem {
    /** One pending request (center needs `qty` more units of a resource). */
    public static class Request {
        int center, res, qty, severity, seq;
    }

    public static class Shipment {
        int wave, qty, time, bottleneck;
        String resource, warehouse, center, route;
        String[] roads;
    }

    final RoadGraph g = new RoadGraph();
    final MinHeap<Request> pending = new MinHeap<>((x, y) -> {
        if (x.severity != y.severity) return y.severity - x.severity;   // higher severity first
        if (x.res != y.res) return x.res - y.res;                       // lower index = more critical
        return x.seq - y.seq;                                           // earlier request first
    });
    final ArrayList<String> log = new ArrayList<>();          // plain text lines for the UI only
    final ArrayList<Shipment> lastWave = new ArrayList<>();
    int seq = 0, wave = 0, delivered = 0, shipmentsTotal = 0, dijkstraRuns = 0, dijkstraPops = 0;

    // ---------------- sample map ----------------
    public static ReliefSystem demo() {
        ReliefSystem s = new ReliefSystem();
        RoadGraph m = s.g;
        m.addNode("W1", 'W', 70, 90, 0);    m.addNode("W2", 'W', 70, 400, 0);   m.addNode("W3", 'W', 400, 50, 0);
        m.addNode("J1", 'J', 250, 150, 0);  m.addNode("J2", 'J', 250, 330, 0);  m.addNode("J3", 'J', 470, 230, 0);
        m.addNode("C1", 'C', 660, 110, 3);  m.addNode("C2", 'C', 710, 260, 5);   // C2 = hospital
        m.addNode("C3", 'C', 620, 400, 2);  m.addNode("C4", 'C', 400, 430, 4);
        //            from  to   minutes capacity-per-wave
        m.addRoad("W1", "J1", 12, 40); m.addRoad("W1", "J2", 20, 30); m.addRoad("W2", "J2", 10, 40);
        m.addRoad("W2", "C4", 18, 25); m.addRoad("W3", "J1", 14, 30); m.addRoad("W3", "J3", 16, 40);
        m.addRoad("J1", "J2", 8, 30);  m.addRoad("J1", "J3", 15, 30); m.addRoad("J2", "J3", 12, 30);
        m.addRoad("J2", "C4", 14, 25); m.addRoad("J3", "C1", 13, 30); m.addRoad("J3", "C2", 14, 30);
        m.addRoad("J3", "C4", 11, 25); m.addRoad("C1", "C2", 9, 20);  m.addRoad("C2", "C3", 10, 25);
        m.addRoad("C4", "C3", 12, 25);
        int[][] st = {{20, 80, 50}, {40, 30, 100}, {30, 60, 20}};     // MEDICINE, WATER, FOOD
        for (int w = 0; w < 3; w++) m.stock[w] = st[w];
        return s;
    }

    /** Load a sample set of simultaneous requests (all arrive "at the same time"). */
    public void loadSampleRequests() {
        request("C3", "FOOD", 60);     request("C4", "MEDICINE", 25);  request("C1", "WATER", 40);
        request("C2", "WATER", 50);    request("C1", "FOOD", 30);      request("C2", "MEDICINE", 40);
        request("C4", "WATER", 30);
    }

    // ---------------- events ----------------
    public void request(String center, String resource, int qty) {
        int c = g.indexOf(center);
        if (g.type[c] != 'C') throw new IllegalArgumentException(center + " is not a relief center");
        if (qty <= 0) throw new IllegalArgumentException("quantity must be positive");
        Request r = new Request();
        r.center = c; r.res = g.resourceIndex(resource); r.qty = qty; r.severity = g.severity[c]; r.seq = seq++;
        pending.push(r);
        log.add("REQUEST queued: " + center + " needs " + qty + " " + resource + " (severity " + r.severity
                + ") -> pushed into urgency heap, size " + pending.size());
    }

    public void toggleRoad(String roadName) {
        RoadGraph.Road r = g.findRoad(roadName);
        r.blocked = !r.blocked;
        log.add("ROAD " + r.name + (r.blocked ? " BLOCKED (edge disabled in the graph)" : " reopened"));
    }

    public void restock(String w, String resource, int qty) {
        int i = g.indexOf(w);
        if (g.type[i] != 'W') throw new IllegalArgumentException(w + " is not a warehouse");
        g.stock[i][g.resourceIndex(resource)] += qty;
        log.add("RESTOCK " + w + " +" + qty + " " + resource);
    }

    // ---------------- the dispatch wave ----------------
    public void dispatchWave() {
        wave++;
        g.resetCapacities();                       // a new convoy cycle: every road's capacity is available again
        lastWave.clear();
        log.add("=== WAVE " + wave + " : " + pending.size() + " request(s) in the urgency heap ===");
        ArrayList<Request> unmet = new ArrayList<>();
        int units = 0, count = 0;

        while (!pending.isEmpty()) {
            Request r = pending.pop();             // most urgent request (heap root)
            String cName = g.name[r.center], rName = RoadGraph.RESOURCES[r.res];
            log.add("POP urgency heap -> " + cName + " " + rName + " need " + r.qty + " (severity " + r.severity + ")");

            RoadGraph.Search s = g.search(r.center, -1, r.res);      // Dijkstra from the center, using the live graph
            dijkstraRuns++; dijkstraPops += s.pops;
            StringBuilder order = new StringBuilder();
            for (int i = 0; i < s.orderCount; i++) order.append(i > 0 ? " " : "").append(g.name[s.order[i]]).append("(").append(s.dist[s.order[i]]).append(")");
            log.add("   Dijkstra heap pop order: " + order);

            if (s.found < 0) {
                log.add("   UNMET: no reachable warehouse with stock (roads blocked/full or stock empty) -> stays pending");
                unmet.add(r);
                continue;
            }
            int w = s.found;
            RoadGraph.Road[] roads = g.pathRoads(s, w);              // warehouse -> center order
            int bottleneck = Integer.MAX_VALUE;
            for (RoadGraph.Road rd : roads) bottleneck = Math.min(bottleneck, rd.residual);
            int take = Math.min(r.qty, Math.min(g.stock[w][r.res], bottleneck));

            for (RoadGraph.Road rd : roads) rd.residual -= take;     // use road capacity
            g.stock[w][r.res] -= take;                               // use warehouse stock
            r.qty -= take;

            Shipment sh = new Shipment();
            sh.wave = wave; sh.qty = take; sh.time = s.dist[w]; sh.bottleneck = bottleneck;
            sh.resource = rName; sh.warehouse = g.name[w]; sh.center = cName;
            int[] nodes = g.pathNodes(s, w);
            StringBuilder route = new StringBuilder();
            for (int i = 0; i < nodes.length; i++) route.append(i > 0 ? " - " : "").append(g.name[nodes[i]]);
            sh.route = route.toString();
            sh.roads = new String[roads.length];
            for (int i = 0; i < roads.length; i++) sh.roads[i] = roads[i].name;
            lastWave.add(sh);
            units += take; count++; delivered += take; shipmentsTotal++;

            log.add("   SHIP " + take + " " + rName + " from " + sh.warehouse + " via " + sh.route + " (" + sh.time
                    + " min). Limited by min(need, stock, weakest road " + bottleneck + ")");
            if (r.qty > 0) {
                log.add("   Remainder " + r.qty + " pushed BACK into urgency heap (next-nearest warehouse / other road will be tried)");
                pending.push(r);
            }
        }
        for (Request r : unmet) pending.push(r);                     // keep for the next wave
        log.add("=== wave " + wave + " done: " + count + " shipment(s), " + units + " units, "
                + pending.size() + " request(s) still pending ===");
    }

    public int pendingUnits() {
        int n = 0;
        for (int i = 0; i < pending.size(); i++) n += pending.get(i).qty;
        return n;
    }
}
