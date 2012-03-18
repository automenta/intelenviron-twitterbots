/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package afxdeadcode;

import automenta.netention.feed.TwitterChannel;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.knallgrau.utils.textcat.FingerPrint;

/**
 *
 * @author SeH
 */
public class Classifier implements Serializable {
    public Map<String, String> corpii = new HashMap();
    transient public Map<String, Category> cats = new HashMap();
    
    transient public Map<String, Double> avgBackground = new HashMap();
    transient public Map<String, List<Integer>> avgBackgroundSamples = new HashMap();
    
    public static Classifier load(String path, boolean saveOnExit) {
        Classifier cc;
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(path));
            cc = (Classifier) ois.readObject();
            //System.out.println("loaded:\n" + cc.corpii.toString());
            ois.close();
        } catch (Exception e) {
            cc = new Classifier();
        }
        
        try {
            cc.calibrateNormal();
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        
        if (saveOnExit)
           cc.saveOnExit(path);
        
        return cc;

    }

    public Classifier() {
    }

    public void update() {
        cats.clear();
    }
    
    public void calibrateNormal() throws Exception {
        System.out.println("Calibrating normal levels");
        
        final int cycles = 4;
        
        for (String s : TwitterChannel.getPublicTweetStrings(cycles)) {
            addBackground(s);
        }
        
        System.out.println("avg background distances: " + avgBackground);
    }
    
    public void addBackground(String p) {
        
        if (avgBackgroundSamples == null) {
            avgBackground = new HashMap();
            avgBackgroundSamples = new HashMap();
        }
        
        for (String c : corpii.keySet()) {
            int dist = getDistance(p, c);
            if (avgBackgroundSamples.get(c) == null)
                avgBackgroundSamples.put(c, new LinkedList());
            avgBackgroundSamples.get(c).add(dist);            
        }
        
        //recompute avgBackground
        for (String c : corpii.keySet()) {
            double total = 0;
            for (Integer i : avgBackgroundSamples.get(c)) {
                total += i;
            }
            double n = avgBackgroundSamples.size();
            avgBackground.put(c, total / n);
        }
    }
    
    public void addCategory(String x) {
        if (!corpii.containsKey(x))
            corpii.put(x, "");
    }
    
    public Category getCategory(String p) {
        if (cats == null)
            cats = new HashMap();
        
        if (cats.containsKey(p)) {
            return cats.get(p);
        }
        Category c = new Category(p);
        c.create(corpii.get(p));
        cats.put(p, c);
        return c;
    }
    
    public void save(String path) throws Exception {
        //System.out.println("saving:\n" + corpii.toString());
        ObjectOutputStream ois = new ObjectOutputStream(new FileOutputStream(path));
        ois.writeObject(this);
        ois.close();
    }

    public void saveOnExit(final String path) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override public void run() {    
                try {
                    save(path);
                } catch (Exception ex) {
                    Logger.getLogger(ClassifierTool.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }));
    }

    public Collection<String> categories() {
        return corpii.keySet();
    }

    public void set(String p, String text) {
        corpii.put(p, text);
        if (cats!=null)
            cats.remove(p); //invalidate it
    }

    public String getCorpus(String p) {
        return corpii.get(p);
    }
    
//    public Map<String, Double> analyzeC(String t, List<String> catCompared) {
//        Map<String, Integer> result = analyze(t, catCompared);
//        
//        Map<String, Double> d = new HashMap();
//        for (String x : result.keySet()) {
//            double nv = result.get(x) == 0 ? 1.0 : ((double)corpii.get(x).length()) / ((double)result.get(x));
//            d.put(x, nv);
//        }
//        
//        return d;
//        
//    }
    
    public int getDistance(String t, String c) {
        FingerPrint fp = new FingerPrint();
        fp.create(t);

        return fp.categorize(Arrays.asList(new FingerPrint[] { getCategory(c) }) ).get(c);
    }

//    public double analyzeC(String t, String c) {
//        FingerPrint fp = new FingerPrint();
//        fp.create(t);
//
//        int d = fp.categorize(Arrays.asList(new FingerPrint[] { getCategory(c) }) ).get(c);
//        
//        return d == 0 ? 1.0 : ((double)corpii.get(c).length()) / ((double)d); // * ((double)t.length());
//    }
//
//    @Deprecated public Map<String, Double> analyzeNormalized(String t, List<String> catCompared) {
//        Map<String, Double> result = analyzeC(t, catCompared);
//        double maxDist = 0, minDist = -1;
//        for (Double ii : result.values()) {
//            if (maxDist < ii) maxDist = ii;
//            if (minDist == -1) minDist = ii;
//            else if (minDist > ii) minDist = ii;
//        }
//        
//        Map<String, Double> d = new HashMap();
//        if ((maxDist!=0) && (maxDist!=minDist)) {
//            for (String x : result.keySet()) {
//                double nv = 1.0 - ((double)(result.get(x) - minDist)) / ((double)maxDist - minDist);
//                d.put(x, nv);
//            }
//        }
//        
//        return d;
//        
//    }
    
    public Map<String, Integer> analyze(String t, List<String> catCompared) {
        FingerPrint fp = new FingerPrint();
        fp.create(t);
        
        List<FingerPrint> ffp = new LinkedList();
        for (Category cat : cats.values())
            if (catCompared.contains(cat.getCategory()))
                ffp.add(cat);
        
        return fp.categorize(ffp);
    }

    public double getAverageBackgroundDistance(String k) {
        return avgBackground.get(k);
    }

    
}
