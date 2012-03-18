/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package afxdeadcode;

import automenta.netention.Detail;
import automenta.netention.feed.TwitterChannel;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author SeH
 */
//    public static double getKeywordDensity(String haystack, String needle) {
public class Agent {
    long focusMinutes = 60 * 1;
    
    public final Set<Detail> details = new HashSet();
    final transient Map<String, Double> scores = new HashMap();
    public final String name;
    public Date lastContacted, lastUpdated = new Date();

    public Agent(String name) {
        this.name = name;
        lastContacted = null;
    }

    public void add(Detail d) {
        details.add(d);
    }

    public static double getAgeFactor(Date detailDate, Date now, double minutesFalloff) {
        double distMinutes = now.getTime() - detailDate.getTime();
        distMinutes /= 60.0 * 1000.0;
        return Math.exp(-(distMinutes / minutesFalloff));
    }

    public void print(Classifier classifier) {
        System.out.println(name);
        System.out.println("  Happy=" + getScore(classifier, new Date(), "happy"));
        System.out.println("  Sad=" + getScore(classifier, new Date(), "sad"));
        System.out.println("  Rich=" + getScore(classifier, new Date(), "rich"));
        System.out.println("  Poor=" + getScore(classifier, new Date(), "poor"));
    }

    public double getScore(Classifier classifier, Date when, String k) {
        if ((scores.containsKey(k) && when.equals(lastUpdated))) {
            return scores.get(k);
        }
        double c = 0;
        double n = 0;
        if (details.size() == 0) {
            return 0;
        }
        
        final double bg = classifier.getAverageBackgroundDistance(k);
        
        for (Detail d : details) {
            String p = d.getName();
            
            double distance = classifier.getDistance(p, k);
            
            //METHOD 1: ratio
            //double num = bg / distance;
            
            //METHOD 2: difference
            double num = (bg - distance)/bg;
            if (num < 0)
                num = 0;
            
            double ageFactor;
            if (when!=null)
                 ageFactor = getAgeFactor(d.getWhen(), when, focusMinutes);
            else
                 ageFactor = 1.0;
            
            c += num * ageFactor;
        }
        if (when.equals(lastUpdated)) {
            scores.put(k, c);
        }
        return c;
    }

    //        public double getScore(Date when, String... k) {
    //            double c = 0, n = 0;
    //            if (details.size() ==0)
    //                return 0;
    //            for (Detail d : details) {
    //                String p = d.getName();
    //                double num = 0;
    //                for (String r : k) {
    //                    num += classifier.analyzeC(p, r);
    //                }
    //                double den = 1;
    //                if (den!=0) {
    //                    double ageFactor = getAgeFactor(d.getWhen(), when, focusMinutes);
    //                    c += (num/den) * ageFactor;
    //                }
    //            }
    //            return c;
    //        }
    //        public double getMeanKeywordDensity(Date when, String... k) {
    //            double c = 0, n = 0;
    //            if (details.size() ==0)
    //                return 0;
    //            for (Detail d : details) {
    //                String p = d.getName();
    //                double num = 0;
    //                for (String r : k)
    //                    num += getKeywordCount(p, r);
    //                //double den = getWordCount(p);
    //                double den = 1;
    //                if (den!=0) {
    //                    double ageFactor = getAgeFactor(d.getWhen(), when, focusMinutes);
    //                    c += (num/den) * ageFactor;
    //                }
    //                n++;
    //            }
    //            return c / n;
    //        }
    public void update(TwitterChannel t) {
        try {
            List<Detail> tw = TwitterChannel.getTweets("@" + name.split("/")[1]);
            details.addAll(tw);
            lastUpdated = new Date();
            //                for (Detail d : tw)
            //                    addMentions(d);
        } catch (Exception ex) {
            Logger.getLogger(Community.class.getName()).log(Level.SEVERE, null, ex);
        }
        scores.clear();
    }
    
}
