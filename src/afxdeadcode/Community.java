package afxdeadcode;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import automenta.netention.Detail;
import automenta.netention.NMessage;
import automenta.netention.Self;
import automenta.netention.Session;
import automenta.netention.email.EMailChannel;
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

    final long minTweetPeriod = 6 * 60; //in s
    final long analysisPeriod = (long)(0.8 * 1000.0); //in ms
    int refreshAfterAnalysisCycles = 8 * 12000; //the great cycle... only makes sense relative to analysisPeriod... TODO find better way to specify this
    long dontReuseAgentUntil = 60 * 60 * 6; //in seconds
    long dontReinvestigate = 1 * 60 * 60; //in seconds
    long waitForNextQueries = 2 * 60; //in seconds
    
    Map<String, Set<String>> queries = new ConcurrentHashMap<>();
    public final Classifier classifier;

    boolean sendToWordpress = true;
    boolean sendToBlogger = false;
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
    
    public Collection<String> getMost(Collection<String> agents, int num, long minRepeatAgentTime, final String key, final String keyOpposite) {
        List<String> a = new ArrayList(agents);
        
        final Map<String, Double> scores = new HashMap();
        for (String s : a) {
            Agent ss = getAgent(s);
            Date aW = ss.lastUpdated; 
            double ak = ss.getScore(classifier, aW, key);
            if (ak!=0.0)
                scores.put(s, ak);
        }
        Collections.sort(a, new Comparator<String>() {
            @Override public int compare(String a, String b) {   
//                Date bW = getAgent(b).lastUpdated;
//                Date aW = getAgent(a).lastUpdated; 
//                double bk = getAgent(b).getScore(classifier, bW, key);
//                double ak = getAgent(a).getScore(classifier, aW, key);
//                final double bR = bk / getAgent(b).getScore(classifier, bW, keyOpposite);
//                final double aR = ak / getAgent(a).getScore(classifier, aW, keyOpposite);
                double A = scores.get(a);
                double B = scores.get(b);
                final double bR = B / A;
                final double aR = A / B;
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
            if (ag.details.size() == 0)
                continue;
            
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
        
        Date now = new Date();
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
                            boolean existing = agents.containsKey(a);
                            getAgent(a).add(d);
                            al.add(a);
                            if (existing) {
                                if (getAgent(a).lastUpdated.getTime() - now.getTime() > dontReinvestigate * 1000)
                                    agentsToInvestigate.add(a);
                            }
                            else {
                                agentsToInvestigate.add(a);                                
                            }
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
                System.out.println("Investigating: " + s);
                getAgent(s).update(tc);
                //getAgent(s).print(classifier);
                agentsToInvestigate.remove(s);
            }
            else {
                System.out.println("No more agents to investigate... pausing before querying again");
                try {
                    Thread.sleep(waitForNextQueries*1000);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Community.class.getName()).log(Level.SEVERE, null, ex);
                }
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
    
    public final EMailChannel email = new EMailChannel();
    
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
            
            Collection<String> happyAuthors = getMost(happySadAgents, 3, dontReuseAgentUntil, "happy", "sad");
            Collection<String> sadAuthors = getMost(happySadAgents, 2, dontReuseAgentUntil, "sad", "happy");
            
            if (!((happyAuthors.size() == 0) || (sadAuthors.size() == 0))) {  
                
                String happyAuthorsStr = getUserString(happyAuthors);
                String sadAuthorsStr = getUserString(sadAuthors);
                //emit(happyAuthors + " seem #happy. " + oneOf("So please help", "Will you help") +  " " + sadAuthors + " who seem #sad ? " + oneOf("#Kindness", "#Health", "#Wisdom", "#Happiness"));

                String richReport = getReport("happy", happyAuthors);
                String poorReport = getReport("sad", sadAuthors);
                
                final String Title = "Happy " + getUserString(happyAuthors) + " vs. Sad " + getUserString(sadAuthors);
                final String Content = richReport + "<br/>" + poorReport;

                emitReport(Title, Content);                
                
                //TWEET
                emit(happyAuthorsStr + " seem #happy. " + oneOf("So please help", "Will you help") +  " " + sadAuthorsStr + " who seem #sad ? " + 
                        oneOf("#Kindness", "#Health", "#Wisdom", "#Happiness") + " " +
                        oneOf("#SocialGood", "#Cause", "#Volunteer", "#4Change", "#GiveBack", "#DoGood") + " "
                        //(includeReportURL ? reportURL : "")
                        );
                
            }
            
        }
               
    }
    
    protected void emitReport(String Title, String Content) {
        //Blogger
        if (sendToBlogger) {
            try {
                email.sendMessage(new NMessage(Title, email.getFrom(), Session.get("blogger.postemail"), new Date(), Content));
            } catch (Exception ex) {
                Logger.getLogger(Community.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (sendToWordpress) {
            //Wordpress
            String reportURL = "";
            try {
                reportURL = blog.newPost(Title, Content);
            } catch (XmlRpcFault ex) {
                Logger.getLogger(Community.class.getName()).log(Level.SEVERE, null, ex);
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
            queries.put("i spent dollars", richPoorAgents);        
            queries.put("\"i got paid\"", richPoorAgents);        
            queries.put("i am poor", richPoorAgents);
            queries.put("i am broke", richPoorAgents);
            queries.put("i need money", richPoorAgents);

        }
        

        @Override
        protected void operate() { 
                        
            Collection<String> happyAuthors = getMost(richPoorAgents, 2, dontReuseAgentUntil, "rich", "poor");
            Collection<String> sadAuthors = getMost(richPoorAgents, 2, dontReuseAgentUntil, "poor", "rich");
            
            if (!((happyAuthors.size() == 0) || (sadAuthors.size() == 0))) {  
                
                String happyAuthorsStr = getUserString(happyAuthors);
                String sadAuthorsStr = getUserString(sadAuthors);
                //emit(happyAuthors + " seem #happy. " + oneOf("So please help", "Will you help") +  " " + sadAuthors + " who seem #sad ? " + oneOf("#Kindness", "#Health", "#Wisdom", "#Happiness"));

                String richReport = getReport("rich", happyAuthors);
                String poorReport = getReport("poor", sadAuthors);

                final String Title = "Rich " + getUserString(happyAuthors) + " vs. Poor " + getUserString(sadAuthors);
                final String Content = richReport + "<br/>" + poorReport;
                emitReport(Title, Content);                

                
                emit(happyAuthorsStr + " may have #wealth to share with " + sadAuthorsStr + " ? " + 
                        oneOf("#Generosity", "#Charity", "#Kindness", "#Opportunity", "#NewEconomy", "#Poverty") + " " +
                        oneOf("#Fundraising", "#Philanthropy", "#SocialGood", "#Cause", "#GiveBack", "#HumanRights", "#DoGood") + " "
                        //(includeReportURL ? reportURL : "")
                        ); //http://www.socialbrite.org/2010/09/08/40-hashtags-for-social-good/

                
            }

        }

               
    }
    
    public String getReport(String key, Collection<String> authors) {
        StringBuilder s = new StringBuilder();
        
        s.append("<center><h1>" + key + "</h1></center>");
        for (String a : authors) {
            s.append("<p><h1>" + a + " " + key + "?</h1></p>");
            Agent ax = getAgent("twitter.com/" + a.substring(1)); //TODO clumsy
            
            List<Detail> detailsByTime = ax.getDetailsByTime2();
            double minScore = -1;
            double maxScore = -1;
            

            for (Detail d : detailsByTime) {
                float age = (float)ax.getAgeFactor(d, ax.focusMinutes);
                float score = (float)ax.getScore(classifier, ax.lastUpdated, key, d) * age;
                if (minScore == -1) { minScore = score; maxScore = score; }
                if (minScore > score) minScore = score;
                if (maxScore < score) maxScore = score;
            }
            
            for (Detail d : detailsByTime) {
                float score = 0.5f;
                float age = (float)ax.getAgeFactor(d, ax.focusMinutes);
                if (minScore!=maxScore) {
                    score = (float)ax.getScore(classifier, ax.lastUpdated, key, d) * age; //TODO repeats with above, use function
                    score = (float)((score - minScore) / (maxScore - minScore));
                }
                
                                
                float tc = (float)Math.min((1.0f - age), 0.3f);
                String style = "color: " + getColor(tc, tc, tc) + 
                                ";background-color: " + getColor(1.0f, (1.0f - score)/2.0f + 0.5f, (1.0f - score)/2.0f + 0.5f) + 
                                ";font-size:"+ Math.max(100, (int)((1.0 + (score/2.0))*100.0)) +"%;";
                
                s.append("<div style='" + style + ";margin-bottom:0;' >" + d.getName() + " <i>(@" + d.getWhen().toString() + ")</i></div>");
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
