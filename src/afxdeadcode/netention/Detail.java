package afxdeadcode.netention;



import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;


public class Detail extends Node implements Comparable<Detail> {
    
    private Mode mode;
    private List<String> patterns = new LinkedList();
    private List<PropertyValue> values = new LinkedList();
    private String creator;
    private Date whenModified;
    private String iconURL = null;

    public Detail() {
        this("");
    }

    public Detail(String name) {
        this(name, Mode.Unknown);
    }

    @Override
    public int compareTo(Detail d) {
        return getID().compareTo(d.getID());
    }


    public Detail(String id, String name, Mode mode, String... initialPatterns) {
        super(id);
        setName(name);
        this.mode = mode;
        this.creator = "Me";
        setWhen(new Date());
        this.whenModified = getWhen();

        for (String p : initialPatterns) {
            addPattern(p);
        }        
        
    }

    /**
     * 
     * @param name
     * @param mode
     * @param initialPatterns  the order of these should indicate the most important patterns first.  the primary icon used is the icon of the first pattern
     */
    public Detail(String name, Mode mode, String... initialPatterns) {
        this(UUID.randomUUID().toString(), name, mode, initialPatterns);
    }


    public void setMode(Mode newMode) {
        this.mode = newMode;
    }
    
    public Mode getMode() {
        return mode;
    }

    public List<String> getPatterns() {
        return patterns;
    }
    

    public List<PropertyValue> getValues() {
        return values;
    }

    public <X extends PropertyValue> X getValue(Class<? extends X> c, String propID) {
        for (PropertyValue pv : getValues())
            if (pv.getProperty().equals(propID))
                if (c.isAssignableFrom(pv.getClass()))
                    return (X)pv;
        return null;
    }
    public <X extends PropertyValue> List<X> getValues(Class<? extends X> c, String propID) {
        LinkedList<X>  ll = new LinkedList();
        for (PropertyValue pv : getValues())
            if (pv.getProperty().equals(propID))
                if (c.isAssignableFrom(pv.getClass()))
                    ll.add((X)pv);
        return ll;
    }
    
    public boolean add(String propID, PropertyValue p) {
        p.setProperty(propID);
        return getValues().add(p);
    }
    
    //TODO impl
//    public boolean removeProperty(String propID) {
//        return false;
//    }
    
    public boolean addPattern(String patternID) {
        if (!getPatterns().contains(patternID))
            return getPatterns().add(patternID);
        return false;
    }
    public boolean removePattern(String patternID) {
        return getPatterns().remove(patternID);
    }

    public String toString() {
        return getName();
    }
    
    public String toString(int nameLength) {
        String s = toString();
        if (s.length() > nameLength) {
            s = s.substring(0, nameLength-1) + "...";
        }
        return s;
    }

    public String getCreator() {
        return creator;
    }

    public String getIconURL() {
        return iconURL;
    }

    public void setIconURL(String u) {
        this.iconURL = u;
    }


    public Date getWhenModified() {
        return whenModified;
    }

    public void setWhenModified(Date whenModified) {
        this.whenModified = whenModified;
    }


    public void removeAllValues(String propID) {
        List<PropertyValue> toRemove = new LinkedList();
        
        for (PropertyValue v : values) {
            if (v.getProperty().equals(propID))
                toRemove.add(v);
        }
        
        values.removeAll(toRemove);
    }

    public void mergeFrom(Detail d) {
        for (String p : d.getPatterns())
            addPattern(p);
        
        for (PropertyValue v : d.getValues()) {
            add(v.getProperty(), v);
        }
    }

    public boolean hasPatternOr(final String... a) {
        for (final String p : getPatterns()) {
            for (final String x : a) {
                if (x.equals(p))
                    return true;
            }
        }
        return false;
    }

    public Detail withID(String id) {
        setID(id);
        return this;
    }

    @Override
    public int hashCode() {
        return getID().hashCode();                
    }

    private String from;
    
    public String getFrom() {
        return from;
    }
    
    public void setFrom(String s) { this.from = s; }



    

}
