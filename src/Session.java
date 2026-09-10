public class Session {

    public final long uid;
    public final String name;
    public final String group;
    public final long since;

    public Session(long uid, String name, String group) {
        this.uid = uid;
        this.name = name;
        this.group = group;
        this.since = System.currentTimeMillis();
    }

    public long seconds() {
        return (System.currentTimeMillis() - since) / 1000;
    }

    @Override
    public String toString() {
        return "#" + uid + " " + name + " (" + group + ")";
    }
}
