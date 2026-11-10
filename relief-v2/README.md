# Emergency Logistics & Resource Allocation Network (Java)

Two hand-written data structures do all the work:
- Graph (adjacency list)  -> roads: travel time, capacity, failures
- Min-Heap (priority queue) -> (1) urgency queue of competing requests, (2) Dijkstra's frontier

## Run (JDK 11+), from this folder
    javac -d out backend/*.java
    java -cp out Main
Open http://localhost:8080

## Demo flow
1. "Add 7 sample requests"  -> watch the urgency heap (array view + serve order)
2. "Dispatch wave"          -> read the decision log; click a shipment row to see its route
3. Click a road to block it, add a request, dispatch again -> automatic reroute
4. Dispatch more waves until nothing is pending

## Files
backend/MinHeap.java       generic min-heap
backend/RoadGraph.java     graph + Dijkstra
backend/ReliefSystem.java  urgency heap + dispatch wave (who gets what)
backend/Main.java          tiny web server
frontend/index.html        single-page UI
Project_Report.pdf         report (fill in cover page)
