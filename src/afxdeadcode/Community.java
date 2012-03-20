package afxdeadcode;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import automenta.netention.Detail;
import automenta.netention.NMessage;
import automenta.netention.Self;
import automenta.netention.Session;
import automenta.netention.feed.TwitterChannel;
import automenta.netention.impl.MemorySelf;

import automenta.netention.value.string.StringIs;
import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import redstone.xmlrpc.XmlRpcFault;


/**
 *
 * @author SeH
 */
public class Community {

    Map<String, Agent> agents = new ConcurrentHashMap();
    public List<String> agentsToInvestigate = Collections.synchronizedList(new LinkedList());
            
    private final TwitterChannel tc;

    final long minTweetPeriod = 3 * 60; //in s
    final long analysisPeriod = (long)(0.3 * 1000.0); //in ms
    int refreshAfterAnalysisCycles = 12000; //the great cycle... only makes sense relative to analysisPeriod... TODO find better way to specify this
    long dontReuseAgentUntil = 60 * 60 * 6; //in seconds
    
    Map<String, Set<String>> queries = new ConcurrentHashMap<>();
    public final Classifier classifier;

    boolean emitToTwitter = true;
    boolean includeReportURL = false;
        
    public static int getKeywordCount(String haystack, String needle) {
        haystack = haystack.toLowerCase();
        needle = needle.toLowerCase();
        if (haystack.contains(needle)) {
            return haystack.split(needle).length;            
        }
        return 0;
    }
    
    public static int getWordCount(String p) {
        if (!p.contains(" "))
            return 0;
        return p.split(" ").length;
    }
    private final WordpressChannel blog;
    

    public Agent getAgent(String user) {
        
        if (agents.containsKey(user))
            return agents.get(user);
        
        Agent a = new Agent(user);
        agents.put(user, a);
        return a;
    }
    

    
    public void addMentions(Detail d) {
//        agentList.add(d.getCreator());
//        for (StringIs s : d.getValues(StringIs.class, NMessage.mentions)) {
//            agentList.add(s.getValue());
//        }
    }
    
    
//    public String getScore(int num, final Date when, long minRepeatAgentTime, final String keys) {
//        List<String> a = new ArrayList(agents.keySet());
//        Collections.sort(a, new Comparator<String>() {
//            @Override public int compare(String a, String b) {                                
//                return Double.compare(getAgent(b).getScore(classifier, when, keys), getAgent(a).getScore(when, keys));
//            }                
//        });
//
//        String p = "";
//        for (String x : a) {
//
//            Agent ag = getAgent(x);
//            Date lc = ag.lastContacted;
//            if (lc!=null) {
//                if (when.getTime() - lc.getTime() < minRepeatAgentTime * 1000) {
//                    continue;
//                }
//            }
//            
//            ag.lastContacted = when;
//
//            System.out.println(Arrays.asList(keys) + ": SCORE=" + x + " " + getAgent(x).getScore(when, keys) + " in  " + getAgent(x).details.size() + " messages\n  " + getAgent(x).details.toString());
//            p += "@" + x.split("/")[1] + " ";
//            
//            num--;
//            if (num == 0)
//                break;
//        }
//
//        return p.trim();
//        
//    }
    
    public Collection<String> getScoreRatio(Collection<String> agents, int num, long minRepeatAgentTime, final String key, final String keyOpposite) {
        List<String> a = new ArrayList(agents);
        
        Collections.sort(a, new Comparator<String>() {
            @Override public int compare(String a, String b) {   
                Date bW = getAgent(b).lastUpdated;
                Date aW = getAgent(a).lastUpdated; 
                double bk = getAgent(b).getScore(classifier, bW, key);
                double ak = getAgent(a).getScore(classifier, aW, key);
                final double bR = bk / getAgent(b).getScore(classifier, bW, keyOpposite);
                final double aR = ak / getAgent(a).getScore(classifier, aW, keyOpposite);
                return Double.compare(bR, aR);
            }                
        });

        final Date now = new Date();
        List<String> p = new LinkedList();
        for (String x : a) {

            Agent ag = getAgent(x);
            Date lc = ag.lastContacted;
            if (lc!=null) {
                if (now.getTime() - lc.getTime() < minRepeatAgentTime * 1000) {
                    continue;
                }
            }
            
            ag.lastContacted = now;

            Date when = ag.lastUpdated;
            System.out.println(key + " : SCORE=" + x + " " + 
                        ((getAgent(x).getScore(classifier, when, key)/getAgent(x).getScore(classifier, when, keyOpposite)) + " in  " + getAgent(x).details.size())
                        );
            p.add("@" + x.split("/")[1]);
            
            num--;
            if (num == 0)
                break;
        }

        return p;
        
    }

    
    public static String oneOf(String... x) {
        int p = (int)Math.floor(Math.random() * x.length);
        return x[p];
    }
    
    
    
    protected void emit(String message) {
        message = message.trim();
        
        System.out.println("TWEETING: " + message);

        if (emitToTwitter)
            tc.updateStatus(message);        
    }
    
    public void runAnalyzeUsers() {
        int k = 1;                     
        
        while (true) {

            if (agentsToInvestigate.size()  == 0) {
                for (String p : queries.keySet()) {
                    final Set<String> al = queries.get(p);
                    try {
                        System.out.println("Keyword search: " + p);
                        List<Detail> tw = TwitterChannel.getTweets(p);
                        for (Detail d : tw) {
                            String a = d.getValue(StringIs.class, NMessage.from).getValue();
                            //addMentions(d);
                            getAgent(a).add(d);
                            al.add(a);
                            agentsToInvestigate.add(a);
                        }


                    } catch (Exception ex) {
                        Logger.getLogger(Community.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }

            }

            if (agentsToInvestigate.size() > 0) {
                Collections.sort(agentsToInvestigate, new Comparator<String>() {
                    @Override public int compare(String o1, String o2) {
                        return Integer.compare(o1.hashCode(), o2.hashCode());
                    }                    
                }); 
                
                final String s = agentsToInvestigate.get(0);
                //System.out.println("Investigating: " + s);
                getAgent(s).update(tc);
                //getAgent(s).print(classifier);
                agentsToInvestigate.remove(s);
            }
            
            try {
                Thread.sleep(analysisPeriod);
            } catch (InterruptedException ex) {
                Logger.getLogger(Community.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            if ((k % refreshAfterAnalysisCycles) == 0) {
                System.out.println("RESTARTING with FRESH DATA");
                agents.clear();
                agentsToInvestigate.clear();                
            }
            k++;
        }
    }

    public static String getUserString(Collection<String> c) {
        String p = "";
        for (String s : c)
            p += s + " ";
        return p.trim();
    }
    public abstract class Matcher implements Runnable {
        private final long phaseSeconds;
        private final long period;

        public Matcher(long period, long phaseSeconds) {
            this.period = period;
            this.phaseSeconds = phaseSeconds;
        }
    
        
        public void run() {
            try {
                Thread.sleep(phaseSeconds * 1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(Community.class.getName()).log(Level.SEVERE, null, ex);
            }
         
            while (true) {
                operate();
                
                try {
                    Thread.sleep(period * 1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Community.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        
        abstract protected void operate();
    }

    public static String getColor(float r, float g, float b) {
        r = Math.max(0, Math.min(r, 1.0f));
        g = Math.max(0, Math.min(g, 1.0f));
        b = Math.max(0, Math.min(b, 1.0f));
        
        Color c = new Color(r, g, b);
        String rgb = Integer.toHexString(c.getRGB());
        return "#" + rgb.substring(2, rgb.length());        
    }
    
    public class HappySad extends Matcher {
        private final Set happySadAgents;

        public HappySad(long period, long phaseSeconds) {
            super(period, phaseSeconds);
            
            happySadAgents = new ConcurrentSkipListSet<String>();
            
            queries.put("i am happy", happySadAgents);
            queries.put("i feel great", happySadAgents);
            queries.put("i am sad", happySadAgents);
            queries.put("i am crying", happySadAgents);
        }

        @Override
        protected void operate() {
            
            Collection<String> happyAuthors = getScoreRatio(happySadAgents, 3, dontReuseAgentUntil, "happy", "sad");
            Collection<String> sadAuthors = getScoreRatio(happySadAgents, 2, dontReuseAgentUntil, "sad", "happy");
            
            if (!((happyAuthors.size() == 0) || (sadAuthors.size() == 0))) {  
                
                String happyAuthorsStr = getUserString(happyAuthors);
                String sadAuthorsStr = getUserString(sadAuthors);
                //emit(happyAuthors + " seem #happy. " + oneOf("So please help", "Will you help") +  " " + sadAuthors + " who seem #sad ? " + oneOf("#Kindness", "#Health", "#Wisdom", "#Happiness"));

                String richReport = emitReport("happy", happyAuthors);
                String poorReport = emitReport("sad", sadAuthors);
                String reportURL = "";
                try {
                    reportURL = blog.newPost("Happy " + getUserString(happyAuthors) + " vs. Sad " + getUserString(sadAuthors), richReport + "<br/>" + poorReport);
                } catch (XmlRpcFault ex) {
                    Logger.getLogger(Community.class.getName()).log(Level.SEVERE, null, ex);
                }
                
                emit(happyAuthorsStr + " seem #happy. " + oneOf("So please help", "Will you help") +  " " + sadAuthorsStr + " who seem #sad ? " + 
                        oneOf("#Kindness", "#Health", "#Wisdom", "#Happiness") + " " +
                        oneOf("#SocialGood", "#Cause", "#Volunteer", "#4Change", "#GiveBack", "#DoGood") + " " +
                        (includeReportURL ? reportURL : "")
                        );
                
            }
            
        }
               
    }
//    public class SmartStupid extends Matcher {
//
//        public SmartStupid(long period, long phaseSeconds) {
//            super(period, phaseSeconds);
//            
//            queries.add("i am smart");
//            queries.add("i am stupid");
//
//        }
//
//        @Override
//        protected void operate() {           
//            Date now = new Date();
//                        
//            String happyAuthors = getLeadingAuthors(3, now, dontReuseAgentUntil, 
//                    "smart", "brilliant", "genius", "helpful", "thankful", "grateful", "solved", "figured", "possible"  );
//            String sadAuthors = getLeadingAuthors(2, now, dontReuseAgentUntil, 
//                    "stupid", "frustrated", "give up", "problem", "confused", "scared", "hopeless", "impossible" );
//            
//            if (!((happyAuthors.length() == 0) || (sadAuthors.length() == 0))) {            
//                emit(happyAuthors + " seem #intelligent. " + oneOf("Can you assist", "Can you help") +  " " + sadAuthors + " with " + oneOf("#Genius", "#Help", "#Ideas", "#Creativity") + " ?");
//            }           
//            
//        }
//               
//    }
    
    public class RichPoor extends Matcher {
        private final Set richPoorAgents;

        public RichPoor(long period, long phaseSeconds) {
            super(period, phaseSeconds);

            richPoorAgents = new ConcurrentSkipListSet<String>();
            
            queries.put("i splurged", richPoorAgents);        
            queries.put("i am poor", richPoorAgents);

        }
        

        @Override
        protected void operate() { 
                        
            Collection<String> happyAuthors = getScoreRatio(richPoorAgents, 2, dontReuseAgentUntil, "rich", "poor");
            Collection<String> sadAuthors = getScoreRatio(richPoorAgents, 2, dontReuseAgentUntil, "poor", "rich");
            
            if (!((happyAuthors.size() == 0) || (sadAuthors.size() == 0))) {  
                
                String happyAuthorsStr = getUserString(happyAuthors);
                String sadAuthorsStr = getUserString(sadAuthors);
                //emit(happyAuthors + " seem #happy. " + oneOf("So please help", "Will you help") +  " " + sadAuthors + " who seem #sad ? " + oneOf("#Kindness", "#Health", "#Wisdom", "#Happiness"));

                String richReport = emitReport("rich", happyAuthors);
                String poorReport = emitReport("poor", sadAuthors);
                String reportURL = "";
                try {
                    reportURL = blog.newPost("Rich " + getUserString(happyAuthors) + " vs. Poor " + getUserString(sadAuthors), richReport + "<br/>" + poorReport);
                } catch (XmlRpcFault ex) {
                    Logger.getLogger(Community.class.getName()).log(Level.SEVERE, null, ex);
                }
                
                emit(happyAuthorsStr + " may have #wealth to share with " + sadAuthorsStr + " ? " + 
                        oneOf("#Generosity", "#Charity", "#Kindness", "#Opportunity", "#NewEconomy", "#Poverty") + " " +
                        oneOf("#Fundraising", "#Philanthropy", "#SocialGood", "#Cause", "#GiveBack", "#HumanRights", "#DoGood") + " " +
                        (includeReportURL ? reportURL : "")
                        ); //http://www.socialbrite.org/2010/09/08/40-hashtags-for-social-good/

                
            }

        }

               
    }
    
    public String emitReport(String key, Collection<String> authors) {
        StringBuilder s = new StringBuilder();
        
        s.append("<center><h1>" + key + "</h1></center>");
        for (String a : authors) {
            s.append("<p><h2>" + a + "</h2></p>");
            Agent ax = getAgent("twitter.com/" + a.substring(1)); //TODO clumsy
            
            List<Detail> detailsByTime = ax.getDetailsByTime2();
            double minScore = -1;
            double maxScore = -1;
            for (Detail d : detailsByTime) {
                float score = (float)ax.getScore(classifier, ax.lastUpdated, key, d);
                if (minScore == -1) { minScore = score; maxScore = score; }
                if (minScore > score) minScore = score;
                if (maxScore < score) maxScore = score;
            }
            
            for (Detail d : detailsByTime) {
                float score = 1.0f;
                if (minScore!=maxScore) {
                    score = (float)ax.getScore(classifier, ax.lastUpdated, key, d);
                    score = (float)((score - minScore) / (maxScore - minScore));
                }
                
                float age = (float)ax.getAgeFactor(d, ax.focusMinutes);
                
                score *= age;
                
                float tc = (float)Math.min((1.0f - age), 0.3f);
                String style = "color: " + getColor(tc, tc, tc) + 
                                "; background-color: " + getColor(1.0f, (1.0f - score)/2.0f + 0.5f, (1.0f - score)/2.0f + 0.5f) + ";";
                
                s.append("<p style='" + style + ";margin-bottom:0;' >" + d.getName() + " (@" + d.getWhen().toString() + ": " + key+ "=" + score + ")</p>");
            }
        }
        
        return s.toString();
    }
    
    public Community(Classifier c) throws Exception {
        
        this.classifier = c;
        this.blog = new WordpressChannel();

        Self s = new MemorySelf();
        
        tc = new TwitterChannel();
        tc.setKey(Session.get("twitter.key"));

        s.queue(new Runnable() {
            @Override public void run() {
                runAnalyzeUsers();
            }            
        });
        s.queue(new HappySad(minTweetPeriod, minTweetPeriod * 3 /8));
        //s.queue(new SmartStupid(2 * 60, 2 * 60));
        s.queue(new RichPoor(minTweetPeriod, minTweetPeriod * 7 /8));
        
        //new SwingWindow(new CommunityBrowser(this), 800, 600, true);
        
    }
    
    
    
    public static void main(String[] args) throws Exception {
        Session.init();
        
        String path = "data";
        
        Classifier cc = Classifier.load(path, false);        
        System.out.println(cc.corpii);
        
        cc.addCategory("happy");
        cc.addCategory("sad");
        cc.addCategory("rich");
        cc.addCategory("poor");
        
        new Community(cc);
    }
}
