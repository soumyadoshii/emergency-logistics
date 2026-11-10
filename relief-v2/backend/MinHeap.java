import java.util.Comparator;

/**
 * DATA STRUCTURE 2: Min-Heap (priority queue), written from scratch.
 * One generic class, TWO unrelated jobs in this project:
 *   (1) Dijkstra's frontier  -> ordered by distance      (RoadGraph.search)
 *   (2) Urgency request queue -> ordered by severity, resource criticality, arrival (ReliefSystem)
 * The Comparator tells the heap what "smallest" (= served first) means.
 * Stored in an array: parent of i = (i-1)/2, children = 2i+1 and 2i+2.
 */
public class MinHeap<T> {
    private Object[] data = new Object[16];
    private int size = 0;
    private final Comparator<T> cmp;
    public int pushes = 0, pops = 0;          // counters, shown in the UI as proof of use

    public MinHeap(Comparator<T> cmp) { this.cmp = cmp; }

    public boolean isEmpty() { return size == 0; }
    public int size() { return size; }
    @SuppressWarnings("unchecked")
    public T get(int i) { return (T) data[i]; }   // raw array access, used only to DISPLAY the heap

    /** Add at the end, then bubble up while smaller than the parent.  O(log n) */
    public void push(T x) {
        if (size == data.length) grow();
        data[size] = x;
        siftUp(size);
        size++;
        pushes++;
    }

    /** Remove and return the root (the most urgent / closest item).  O(log n) */
    public T pop() {
        if (size == 0) throw new IllegalStateException("heap is empty");
        T top = get(0);
        size--;
        data[0] = data[size];                     // last element goes to the root...
        data[size] = null;
        if (size > 0) siftDown(0);                // ...and sinks to its correct place
        pops++;
        return top;
    }

    /** Independent copy, so the UI can show the serving order without disturbing the real queue. */
    public MinHeap<T> copy() {
        MinHeap<T> c = new MinHeap<>(cmp);
        c.data = data.clone();
        c.size = size;
        return c;
    }

    private boolean less(int i, int j) { return cmp.compare(get(i), get(j)) < 0; }
    private void swap(int i, int j) { Object t = data[i]; data[i] = data[j]; data[j] = t; }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (!less(i, parent)) break;
            swap(i, parent);
            i = parent;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int left = 2 * i + 1, right = left + 1, smallest = i;
            if (left < size && less(left, smallest)) smallest = left;
            if (right < size && less(right, smallest)) smallest = right;
            if (smallest == i) return;
            swap(i, smallest);
            i = smallest;
        }
    }

    private void grow() {
        Object[] bigger = new Object[data.length * 2];
        for (int i = 0; i < size; i++) bigger[i] = data[i];
        data = bigger;
    }
}
