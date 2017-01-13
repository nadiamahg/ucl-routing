import java.lang.Math;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.Iterator;

public class DV implements RoutingAlgorithm {

    static int LOCAL = -1;
    static int UNKNOWN = -2;
    static int INFINITY = 60;

    static int EXPIRE_AFTER = 6; // number of update intervals for the expiration timer
    static int REMOVE_AFTER = 4; // number of update intervals for the garbage-collection timer

    private Router router;
    private int update_interval;
    private boolean poison_reverse;
    private boolean allow_expire;

    private Map<Integer, DVRoutingTableEntry> entries; // the routing table

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
            return entry.getInterface();
        } else {
            return UNKNOWN;
        }
    }

    public void tidyTable() {
        updateDownLinks();
        if (allow_expire) {
            expireOldEntries();
        }
    }

    private void updateDownLinks() {
        for (DVRoutingTableEntry entry : entries.values()) {
            int iface = entry.getInterface();
            if (!router.getInterfaceState(iface) && entry.getMetric() != INFINITY) {
                entry.setMetric(INFINITY);
                entry.setTime(router.getCurrentTime());
            }
        }
    }

    private void expireOldEntries() {
        int now = router.getCurrentTime();
        Iterator<DVRoutingTableEntry> it = entries.values().iterator();
        while (it.hasNext()) {
            DVRoutingTableEntry entry = it.next();

            // we don't want to expire/delete the local route
            if (entry.getInterface() == LOCAL) {
                continue;
            }

            // infinite routes have the garbage collection timer which gets them deleted
            if (entry.getMetric() == INFINITY) {
                if (entry.getTime() + REMOVE_AFTER * update_interval <= now) {
                    // System.out.println("REMOVING " + entry.toString());
                    it.remove();
                }

            // all other routes have the expiration timer
            } else {
                if (entry.getTime() + EXPIRE_AFTER * update_interval <= now) {
                    // System.out.println("EXPIRING " + entry.toString());
                    entry.setMetric(INFINITY);
                    entry.setTime(now);
                }
            }
        }
    }

    public Packet generateRoutingPacket(int iface) {

        // no packet if the link on the interface is down
        if (!router.getInterfaceState(iface)) {
            return null;
        }

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
            int metric = payloadEntry.getMetric();

            if (metric != INFINITY) {
                metric += weight;
            }

            // in case metric < infinity, but metric + weight > infinity
            // set it to infinity
            if (metric > INFINITY) {
                metric = INFINITY;
            }

            processRoutingEntry(destination, iface, metric);
        }

    }

    // process a single entry from the routing packet
    private void processRoutingEntry(int dest, int iface, int metric) {
        DVRoutingTableEntry entry = entries.get(dest);
        int now = router.getCurrentTime();
        if (entry != null) {
            if (entry.getInterface() == iface || entry.getMetric() > metric) {
                entry.setInterface(iface);
                // do not update the timer if both the old and new metric are infinity
                if (!(metric == INFINITY && entry.getMetric() == INFINITY)) {
                    entry.setTime(now);
                    entry.setMetric(metric);
                }
            }
        } else {
            // ignore received entry if its metric is infinity
            if (metric != INFINITY) {
                DVRoutingTableEntry routingEntry = new DVRoutingTableEntry(dest, iface, metric, now);
                entries.put(dest, routingEntry);
            }
        }
    }

    public void showRoutes() {
        System.out.println("Router " + router.getId());
        for (DVRoutingTableEntry routingEntry : entries.values()) {
            System.out.println(routingEntry.toString());
        }
    }
}

// A special (immutable) class for encoding entries into Payload.
// (to avoid the worries of mutability when passing entries around)
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
