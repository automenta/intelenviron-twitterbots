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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 *
 * @author SeH
 */
public class Community {

    Map<String, Agent> agents = new ConcurrentHashMap();
    public List<String> agentsToInvestigate = new LinkedList();

    private final TwitterChannel tc;

    int refreshAfterCycles = 2000; //the great cycle
    final long minTweetPeriod = 4 * 60; //in s
    final long analysisPeriod = (long)(0.35 * 1000.0); //in ms
    long dontReuseAgentUntil = 60 * 60 * 6; //in seconds
    
    Map<String, Set<String>> queries = new ConcurrentHashMap<>();
    public final Classifier classifier;

    
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
    
    public String getScoreRatio(Collection<String> agents, int num, long minRepeatAgentTime, final String key1, final String key2) {
        List<String> a = new ArrayList(agents);
        
        Collections.sort(a, new Comparator<String>() {
            @Override public int compare(String a, String b) {   
                Date bW = getAgent(b).lastUpdated;
                Date aW = getAgent(a).lastUpdated; 
                final double bR = getAgent(b).getScore(classifier, bW, key1) / getAgent(b).getScore(classifier, bW, key2);
                final double aR = getAgent(a).getScore(classifier, aW, key1) / getAgent(a).getScore(classifier, aW, key2);
                return Double.compare(bR, aR);
            }                
        });

        Date now = new Date();
        String p = "";
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
            System.out.println(key1 + "/" + key2 + " : SCORE=" + x + " " + 
                        (getAgent(x).getScore(classifier, when, key1) / getAgent(x).getScore(classifier, when, key2) + " in  " + getAgent(x).details.size())
                        );
            p += "@" + x.split("/")[1] + " ";
            
            num--;
            if (num == 0)
                break;
        }

        return p.trim();
        
    }

    
    public static String oneOf(String... x) {
        int p = (int)Math.floor(Math.random() * x.length);
        return x[p];
    }
    
    
    
    protected void emit(String message) {
        System.out.println("TWEETING: " + message);

        //tc.updateStatus(message);        
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
            
            if ((k % refreshAfterCycles) == 0) {
                System.out.println("RESTARTING with FRESH DATA");
                agents.clear();
                agentsToInvestigate.clear();                
            }
            k++;
        }
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

    public class HappySad extends Matcher {
        private final HashSet happySadAgents;

        public HappySad(long period, long phaseSeconds) {
            super(period, phaseSeconds);
            
            happySadAgents = new HashSet();
            
            queries.put("i am happy", happySadAgents);
            queries.put("i feel great", happySadAgents);
            queries.put("i am sad", happySadAgents);
            queries.put("i am crying", happySadAgents);
        }

        @Override
        protected void operate() {
            
                        
            String happyAuthors = getScoreRatio(happySadAgents, 3, dontReuseAgentUntil, "happy", "sad");
            String sadAuthors = getScoreRatio(happySadAgents, 2, dontReuseAgentUntil, "sad", "happy");
            
            if (!((happyAuthors.length() == 0) || (sadAuthors.length() == 0))) {            
                emit(happyAuthors + " seem #happy. " + oneOf("So please help", "Will you help") +  " " + sadAuthors + " who seem #sad ? " + 
                        oneOf("#Kindness", "#Health", "#Wisdom", "#Happiness") + " " +
                        oneOf("#SocialGood", "#Cause", "#Volunteer", "#4Change", "#GiveBack", "#DoGood", "#Crisiscommons"));
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
        private final HashSet richPoorAgents;

        public RichPoor(long period, long phaseSeconds) {
            super(period, phaseSeconds);

            richPoorAgents = new HashSet();
            
            queries.put("i bought", richPoorAgents);        
            queries.put("i splurged", richPoorAgents);        
            queries.put("i am poor", richPoorAgents);
            queries.put("i need money", richPoorAgents);

        }

        @Override
        protected void operate() { 
                        
            String happyAuthors = getScoreRatio(richPoorAgents, 2, dontReuseAgentUntil, "rich", "poor");
            String sadAuthors = getScoreRatio(richPoorAgents, 2, dontReuseAgentUntil, "poor", "rich");
            
            if (!((happyAuthors.length() == 0) || (sadAuthors.length() == 0))) {            
                //emit(happyAuthors + " seem #happy. " + oneOf("So please help", "Will you help") +  " " + sadAuthors + " who seem #sad ? " + oneOf("#Kindness", "#Health", "#Wisdom", "#Happiness"));
                emit(happyAuthors + " may have #wealth to share with " + sadAuthors + " ? " + 
                        oneOf("#Generosity", "#Charity", "#Kindness", "#Opportunity") + " " +
                        oneOf("#Fundraising", "#Philanthropy", "#SocialGood", "#Cause", "#GiveBack") + " " +
                        oneOf("#NewEconomy", "#Poverty", "#HumanRights", "#DoGood")
                        ); //http://www.socialbrite.org/2010/09/08/40-hashtags-for-social-good/
            }

        }
               
    }
    
    public Community(Classifier c) throws Exception {
        
        this.classifier = c;

        Self s = new MemorySelf();
        
        tc = new TwitterChannel();
        tc.setKey(Session.get("twitter.key"));

        s.queue(new Runnable() {
            @Override public void run() {
                runAnalyzeUsers();
            }            
        });
        s.queue(new HappySad(minTweetPeriod, minTweetPeriod * 4 /8));
        //s.queue(new SmartStupid(2 * 60, 2 * 60));
        s.queue(new RichPoor(minTweetPeriod, minTweetPeriod * 6 /8));
        
        //new SwingWindow(new CommunityBrowser(this), 800, 600, true);
        
    }
    
    
    
    public static void main(String[] args) throws Exception {
        Session.init();
        
        String path = "data";
        
        Classifier cc = Classifier.load(path, false);        
        
        cc.addCategory("happy");
        cc.addCategory("sad");
        cc.addCategory("rich");
        cc.addCategory("poor");
        
        new Community(cc);
    }
}
