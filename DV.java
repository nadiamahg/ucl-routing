import java.lang.Math;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.Iterator;

public class DV implements RoutingAlgorithm {

    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;

    private Router router;
    private int update_interval;
    private boolean poison_reverse = false;
    private boolean allow_expire = false;

    private Map<Integer, DVRoutingTableEntry> entries;

    public DV() {
        entries = new HashMap<Integer, DVRoutingTableEntry>();
    }

    public void setRouterObject(Router obj) {
        router = obj;
    }

    public void setUpdateInterval(int u) {
        update_interval = u;
    }

    public void setAllowPReverse(boolean flag) {
        poison_reverse = flag;
    }

    public void setAllowExpire(boolean flag) {
        allow_expire = flag;
    }

    public void initalise() {
        DVRoutingTableEntry initialEntry = new DVRoutingTableEntry(router.getId(), LOCAL, 0, 0);
        entries.put(router.getId(), initialEntry);
    }

    public int getNextHop(int destination) {
        DVRoutingTableEntry entry = entries.get(destination);
        if (entry != null && entry.getMetric() != INFINITY) {
            return entry.getDestination();
        } else {
            return UNKNOWN;
        }
    }

    public void tidyTable() {

    }

    public Packet generateRoutingPacket(int iface) {

        Payload payload = new Payload();
        for (DVRoutingTableEntry routingEntry : entries.values()) {
            PayloadEntry entry;
            if (poison_reverse && routingEntry.getInterface() == iface) {
                entry = new PayloadEntry(routingEntry.getDestination(), INFINITY);
            } else {
                entry = new PayloadEntry(routingEntry.getDestination(), routingEntry.getMetric());
            }
            payload.addEntry(entry);
        }

        int src = router.getId();
        int dst = Packet.BROADCAST;
        Packet packet = new RoutingPacket(src, dst);
        packet.setPayload(payload);

        return packet;
    }

    public void processRoutingPacket(Packet p, int iface) {

        Payload payload = p.getPayload();
        Vector<Object> data = payload.getData();

        int weight = router.getInterfaceWeight(iface);

        for (Object obj : data) {
            PayloadEntry payloadEntry = (PayloadEntry)obj;
            int destination = payloadEntry.getDestination();
            int metric = payloadEntry.getMetric() + weight;
            processRoutingEntry(destination, iface, metric);
        }

    }

    private void processRoutingEntry(int d, int i, int m) {
        DVRoutingTableEntry entry = entries.get(d);
        if (entry != null) {
            if (entry.getInterface() == i) {
                entry.setMetric(m);
            } else if (entry.getMetric() > m) {
                entry.setInterface(i);
                entry.setMetric(m);
            }
        } else {
            DVRoutingTableEntry routingEntry = new DVRoutingTableEntry(d, i, m, 0);
            entries.put(d, routingEntry);
        }
    }

    public void showRoutes() {
        System.out.println("Router " + router.getId());
        for (DVRoutingTableEntry routingEntry : entries.values()) {
            System.out.println(routingEntry.toString());
        }
    }
}

class PayloadEntry {

    private int destid;
    private int metric;

    public PayloadEntry(int d, int m) {
        destid = d;
        metric = m;
    }

    public PayloadEntry(RoutingTableEntry entry) {
        destid = entry.getDestination();
        metric = entry.getMetric();
    }

    public int getDestination() {
        return destid;
    }

    public int getMetric() {
        return metric;
    }

}

class DVRoutingTableEntry implements RoutingTableEntry {

    private int destid;
    private int intid;
    private int metric;
    private int time;

    public DVRoutingTableEntry(int d, int i, int m, int t) {
        destid = d;
        intid = i;
        metric = m;
        time = t;
    }

    public int getDestination() {
        return destid;
    }

    public void setDestination(int d) {
        destid = d;
    }

    public int getInterface() {
        return intid;
    }

    public void setInterface(int i) {
        intid = i;
    }

    public int getMetric() {
        return metric;
    }

    public void setMetric(int m) {
        metric = m;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int t) {
        time = t;
    }

    public String toString() {
        return "d " + destid + " i " + intid + " m " + metric;
    }
}
