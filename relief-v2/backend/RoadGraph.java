/**
 * DATA STRUCTURE 1: Graph stored as an adjacency list (a linked list of roads per location).
 * Nodes = warehouses / junctions / relief centers.  Edges = roads with travel time and capacity.
 * A Road is ONE object shared by the lists of both its end points, so blocking it blocks both directions,
 * and using its capacity from either side reduces the same counter.
 * Dijkstra (driven by MinHeap) lives here because it works directly on this structure.
 */
public class RoadGraph {
    /** Index = criticality rank: 0 is the most critical resource. */
    public static final String[] RESOURCES = {"MEDICINE", "WATER", "FOOD"};
    private static final int INF = Integer.MAX_VALUE;

    public static class Road {
        int id, a, b, time, capacity;
        int residual;                 // capacity still free in the current wave
        boolean blocked = false;      // road failure
        String name;
        boolean usable() { return !blocked && residual > 0; }
    }

    private static class Link {       // one cell of a linked list
        Road road;
        Link next;
    }

    /** Result of one Dijkstra run. */
    public static class Search {
        int[] dist, prev;
        Road[] prevRoad;
        int[] order;                  // locations in the order they left the heap
        int orderCount = 0;
        int found = -1;
        int pushes = 0, pops = 0;     // heap operations used by this run
    }

    String[] name = new String[20];
    char[] type = new char[20];       // 'W' warehouse, 'J' junction, 'C' relief center
    int[] x = new int[20], y = new int[20], severity = new int[20];
    int[][] stock = new int[20][RESOURCES.length];
    Link[] head = new Link[20];
    Road[] roads = new Road[40];
    int nodeCount = 0, roadCount = 0;

    public void addNode(String n, char t, int px, int py, int sev) {
        name[nodeCount] = n; type[nodeCount] = t; x[nodeCount] = px; y[nodeCount] = py; severity[nodeCount] = sev;
        nodeCount++;
    }

    public void addRoad(String u, String v, int time, int capacity) {
        Road r = new Road();
        r.id = roadCount; r.a = indexOf(u); r.b = indexOf(v);
        r.time = time; r.capacity = capacity; r.residual = capacity; r.name = u + "-" + v;
        roads[roadCount++] = r;
        link(r.a, r);
        link(r.b, r);
    }

    private void link(int node, Road r) {          // insert at head of the list: O(1)
        Link l = new Link();
        l.road = r; l.next = head[node]; head[node] = l;
    }

    public int indexOf(String n) {
        for (int i = 0; i < nodeCount; i++) if (name[i].equals(n)) return i;
        throw new IllegalArgumentException("unknown location " + n);
    }

    public Road findRoad(String n) {
        for (int i = 0; i < roadCount; i++) {
            String[] p = roads[i].name.split("-");
            if (n.equals(roads[i].name) || n.equals(p[1] + "-" + p[0])) return roads[i];
        }
        throw new IllegalArgumentException("unknown road " + n);
    }

    public int resourceIndex(String r) {
        for (int i = 0; i < RESOURCES.length; i++) if (RESOURCES[i].equals(r)) return i;
        throw new IllegalArgumentException("unknown resource " + r);
    }

    public void resetCapacities() { for (int i = 0; i < roadCount; i++) roads[i].residual = roads[i].capacity; }

    /** The roads leaving node u, in linked-list order (used to display the adjacency list). */
    public Road[] roadsOf(int u) {
        int n = 0;
        for (Link l = head[u]; l != null; l = l.next) n++;
        Road[] out = new Road[n];
        int i = 0;
        for (Link l = head[u]; l != null; l = l.next) out[i++] = l.road;
        return out;
    }

    /**
     * Dijkstra from src on the LIVE graph: blocked roads and roads with no free capacity are skipped.
     * Stops early when the popped location is
     *   - dst (if dst >= 0), or
     *   - a warehouse that still holds some of resource `res` (if res >= 0)  -> "nearest warehouse with stock".
     * The heap pops locations in increasing distance, so the first match is the closest one.
     */
    public Search search(int src, int dst, int res) {
        Search s = new Search();
        s.dist = new int[nodeCount];
        s.prev = new int[nodeCount];
        s.prevRoad = new Road[nodeCount];
        s.order = new int[nodeCount];
        boolean[] done = new boolean[nodeCount];
        for (int i = 0; i < nodeCount; i++) { s.dist[i] = INF; s.prev[i] = -1; }

        MinHeap<int[]> heap = new MinHeap<>((p, q) -> Integer.compare(p[1], q[1]));   // {node, distance}
        s.dist[src] = 0;
        heap.push(new int[]{src, 0});

        while (!heap.isEmpty()) {
            int u = heap.pop()[0];
            if (done[u]) continue;                 // outdated duplicate entry
            done[u] = true;
            s.order[s.orderCount++] = u;

            if (u == dst) { s.found = u; break; }
            if (res >= 0 && type[u] == 'W' && stock[u][res] > 0) { s.found = u; break; }

            for (Link l = head[u]; l != null; l = l.next) {
                Road r = l.road;
                if (!r.usable()) continue;         // failed or already full this wave
                int v = (r.a == u) ? r.b : r.a;
                if (!done[v] && s.dist[u] + r.time < s.dist[v]) {
                    s.dist[v] = s.dist[u] + r.time;
                    s.prev[v] = u;
                    s.prevRoad[v] = r;
                    heap.push(new int[]{v, s.dist[v]});
                }
            }
        }
        s.pushes = heap.pushes;
        s.pops = heap.pops;
        return s;
    }

    /** Roads on the route, listed from `target` back to the search source (= warehouse -> center when src is a center). */
    public Road[] pathRoads(Search s, int target) {
        int n = 0;
        for (int c = target; s.prev[c] != -1; c = s.prev[c]) n++;
        Road[] out = new Road[n];
        int i = 0;
        for (int c = target; s.prev[c] != -1; c = s.prev[c]) out[i++] = s.prevRoad[c];
        return out;
    }

    public int[] pathNodes(Search s, int target) {
        int n = 1;
        for (int c = target; s.prev[c] != -1; c = s.prev[c]) n++;
        int[] out = new int[n];
        int i = 0, c = target;
        out[i++] = c;
        while (s.prev[c] != -1) { c = s.prev[c]; out[i++] = c; }
        return out;
    }
}
