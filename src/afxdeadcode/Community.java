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

    final long minTweetPeriod = 6 * 60; //in s, safe @ 6
    final long analysisPeriod = (long)(1.0 * 1000.0); //in ms
    int refreshAfterAnalysisCycles = 8 * 12000; //the great cycle... only makes sense relative to analysisPeriod... TODO find better way to specify this
    long dontReuseAgentUntil = 60 * 60 * 6; //in seconds
    long dontReinvestigate = 1 * 60 * 60; //in seconds
    long waitForNextQueries = 2 * 60; //in seconds
    
    Map<String, Set<String>> queries = new ConcurrentHashMap<>();

    boolean sendToBlogger = false;  //limits to 50 emails before captcha required
    boolean sendToWordpress = true;
    boolean emitToTwitter = true;

    boolean includeReportURL = false;
    
        
    @Deprecated public static int getKeywordCount(String haystack, String needle) {
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
    

    
    public static String oneOf(String... x) {
        int p = (int)Math.floor(Math.random() * x.length);
        return x[p];
    }
    
    
    
    protected void emitTweet(String message) {
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
    public abstract class TagMatcher implements Runnable {
        private final long phaseSeconds;
        private final long period;
        
        long focusMinutes = 3 * 60;

        NGramClassifier a, b;
        private final ConcurrentSkipListSet<String> localAgents;
        private final String catA;
        private final String catB;
        
        int numAAgents = 2;
        int numBAgents = 2;
        
        public TagMatcher(String catA, String catB, long period, long phaseSeconds) {
            this.period = period;
            this.phaseSeconds = phaseSeconds;
            
            this.catA = catA;
            this.catB = catB;
            
            a = NGramClassifier.load(catA);
            b = NGramClassifier.load(catB);

            localAgents = new ConcurrentSkipListSet<String>();
            
            for (String q : getSeedQueries()) 
                queries.put(q, localAgents);
        
        }
        
        abstract public String[] getSeedQueries();
    
        
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
        
        public String getTitle(Collection<String> sa, Collection<String> sb) {
            return catA + " (" + getUserString(sa) + ") vs. " + catB + " (" +getUserString(sb) + ")";
        }
        
        abstract public String getTweet(String sa, String sb);
        
        protected void operate() {
            
            Collection<String> sa = getMost(localAgents, numAAgents, dontReuseAgentUntil, a, b);
            Collection<String> sb = getMost(localAgents, numBAgents, dontReuseAgentUntil, b, a);
            
            if (!((sa.size() == 0) || (sb.size() == 0))) {  
                
                String happyAuthorsStr = getUserString(sa);
                String sadAuthorsStr = getUserString(sb);
                //emit(happyAuthors + " seem #happy. " + oneOf("So please help", "Will you help") +  " " + sadAuthors + " who seem #sad ? " + oneOf("#Kindness", "#Health", "#Wisdom", "#Happiness"));

                String richReport = getReport(sa, a);
                String poorReport = getReport(sb, b);
                
                final String Title = getTitle(sa, sb);
                final String Content = richReport + "<br/>" + poorReport;

                emitReport(Title, Content);                
                
                //TWEET
                emitTweet(getTweet(happyAuthorsStr, sadAuthorsStr));
                
            }
        }

        public String getReport(Collection<String> authors, NGramClassifier classifier) {
            StringBuilder s = new StringBuilder();

            final String key = classifier.getName();
            
            s.append("<center><h1>" + key + "</h1></center>");
            for (String a : authors) {
                s.append("<p><h1>" + a + " " + key + "?</h1></p>");
                Agent ax = getAgent("twitter.com/" + a.substring(1)); //TODO clumsy

                List<Detail> detailsByTime = ax.getDetailsByTime2();
                double minScore = -1;
                double maxScore = -1;


                for (Detail d : detailsByTime) {
                    float age = (float)ax.getAgeFactor(d, focusMinutes);
                    float score = (float)ax.getScore(classifier, ax.lastUpdated, d) * age;
                    if (minScore == -1) { minScore = score; maxScore = score; }
                    if (minScore > score) minScore = score;
                    if (maxScore < score) maxScore = score;
                }

                for (Detail d : detailsByTime) {
                    float score = 0.5f;
                    float age = (float)ax.getAgeFactor(d, focusMinutes);
                    if (minScore!=maxScore) {
                        score = (float)ax.getScore(classifier, ax.lastUpdated, d) * age; //TODO repeats with above, use function
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
    
        public Collection<String> getMost(Collection<String> agents, int num, long minRepeatAgentTime, final NGramClassifier classifierA, final NGramClassifier classifierB) {
            List<String> mostA = new ArrayList(agents);

            final Map<String, Double> scores = new HashMap();
            for (String s : agents) {
                Agent ss = getAgent(s);
                Date aW = ss.lastUpdated; 
                double ak = ss.getScore(classifierA, aW, focusMinutes);
                double bk = ss.getScore(classifierB, aW, focusMinutes);
                scores.put(s, ak / bk);
            }
            
            Collections.sort(mostA, new Comparator<String>() {
                @Override public int compare(String a, String b) {   
                    double A = scores.get(a);
                    double B = scores.get(b);
                    return Double.compare(A, B);
                }                
            });
            

            final Date now = new Date();
            List<String> p = new LinkedList();
            for (String x : mostA) {

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
                System.out.println(classifierA.getName() + " : SCORE=" + x + " " + scores.get(x));
                p.add("@" + x.split("/")[1]);

                num--;
                if (num == 0)
                    break;
            }

            return p;

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
    
    public class HappySad extends TagMatcher {

        public HappySad(long period, long phaseSeconds) {
            super("happy", "sad", period, phaseSeconds);            
            this.numAAgents = 3; //TODO this is a hack
        }

        @Override
        public String[] getSeedQueries() {
            return new String[] {
                "\"i am happy\"",
                ":)",
                "\"i am sad\"",
                "i am crying"
            };
        }

        @Override
        public String getTweet(String sa, String sb) {
                return sa + " seem #happy. " + oneOf("So please help", "Will you help") +  " " + sb + " who seem #sad ? " + 
                        oneOf("#Kindness", "#Health", "#Wisdom", "#Happiness") + " " +
                        oneOf("#SocialGood", "#Cause", "#Volunteer", "#4Change", "#GiveBack", "#DoGood") + " "
                        //(includeReportURL ? reportURL : "")
                        ;
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
    
    public class RichPoor extends TagMatcher {

        public RichPoor(long period, long phaseSeconds) {
            super("rich", "poor", period, phaseSeconds);

            
        }

        @Override
        public String getTweet(String sa, String sb) {
            return sa + " may have #wealth to share with " + sb + " ? " + 
                        oneOf("#Generosity", "#Charity", "#Kindness", "#Opportunity", "#NewEconomy", "#Poverty") + " " +
                        oneOf("#Fundraising", "#Philanthropy", "#SocialGood", "#Cause", "#GiveBack", "#HumanRights", "#DoGood") + " ";
                        //(includeReportURL ? reportURL : "")
        }

        @Override
        public String[] getSeedQueries() {
            return new String[] {
                "i splurged", 
                "i spent dollars",
                "\"i got paid\"", 
                "i am poor", 
                "i am broke",
                "i need money"
            };
        }
               
    }
    
    
    public Community() throws Exception {
        
        this.blog = new WordpressChannel();

        Self s = new MemorySelf();
        
        tc = new TwitterChannel();
        tc.setKey(Session.get("twitter.key"));

        s.queue(new HappySad(minTweetPeriod, minTweetPeriod * 3 /8));
        //s.queue(new SmartStupid(2 * 60, 2 * 60));
        s.queue(new RichPoor(minTweetPeriod, minTweetPeriod * 7 /8));
        s.queue(new Runnable() {
            @Override public void run() {
                runAnalyzeUsers();
            }            
        });
        
        //new SwingWindow(new CommunityBrowser(this), 800, 600, true);
        
    }
    
    
    
    public static void main(String[] args) throws Exception {
        Session.init();
        
        new Community();
    }
}
