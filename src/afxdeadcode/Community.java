package afxdeadcode;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */


import afxdeadcode.bots.RichPoor;
import afxdeadcode.bots.HappySad;
import afxdeadcode.netention.Detail;
import afxdeadcode.netention.Session;
import afxdeadcode.netention.feed.TwitterChannel;



import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import redstone.xmlrpc.XmlRpcFault;


/**
 *
 * @author SeH
 */
public class Community  {

    Map<String, Agent> agents = new ConcurrentHashMap();
    public List<String> agentsToInvestigate = Collections.synchronizedList(new LinkedList());
            
    Map<String, Set<String>> queries = new ConcurrentHashMap<>();

    private final TwitterChannel tc;
    private final WordpressChannel blog;    
    

    public static long ms(int h, int m, int s, int ms) {
        return ms + s * 1000 + m * (1000*60) + h * (1000*60*60);
    }
    
    static long minTweetPeriod = 16 * 60; //in s, safe @ 6
    long analysisPeriod = ms(0,0,3,0);  //in ms
    long dontReinvestigate = 1 * 60 * 60; //in seconds
    long waitForNextQueries = 2 * 60; //in seconds
    int refreshAfterAnalysisCycles = 8 * 12000; //the great cycle... only makes sense relative to analysisPeriod... TODO find better way to specify this
    
    boolean sendToBlogger = false;  //limits to 50 emails before captcha required
    boolean sendToWordpress = true;
    boolean emitToTwitter = true;

    @Deprecated boolean includeReportURL = false;

        
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
    
    protected void emitReport(String Title, String Content) {
        //Blogger
        /*if (sendToBlogger) {
            try {
                email.sendMessage(new NMessage(Title, email.getFrom(), Session.get("blogger.postemail"), new Date(), Content));
            } catch (Exception ex) {
                Logger.getLogger(Community.class.getName()).log(Level.SEVERE, null, ex);
            }
        }*/

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
                            String a = d.getFrom(); //d.getValue(StringIs.class, NMessage.from).getValue();
                            //addMentions(d);
                            boolean existing = agents.containsKey(a);
                            getAgent(a).add(d);
                            al.add(a);
                            if (existing) {
                                if (getAgent(a).lastUpdated.getTime() - now.getTime() > dontReinvestigate * 1000)
                                    if (!agentsToInvestigate.contains(a))
                                        agentsToInvestigate.add(a);
                            }
                            else {
                                if (!agentsToInvestigate.contains(a))
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

    public static String getColor(float r, float g, float b) {
        r = Math.max(0, Math.min(r, 1.0f));
        g = Math.max(0, Math.min(g, 1.0f));
        b = Math.max(0, Math.min(b, 1.0f));
        
        Color c = new Color(r, g, b);
        String rgb = Integer.toHexString(c.getRGB());
        return "#" + rgb.substring(2, rgb.length());        
    }
    
    
    
    public Community() throws Exception {
        super();
        
        
        this.blog = new WordpressChannel();

        this.tc = new TwitterChannel();
        this.tc.setKey(Session.get("twitter.key"));
        
    }
    
    public void start() {

        
        runAnalyzeUsers();
        
        //new SwingWindow(new CommunityBrowser(this), 800, 600, true);
        
    }
    
    
    
    public static void main(String[] args) throws Exception {
        Session.init();
        
        Community c = new Community();
        new Thread(new HappySad(c, minTweetPeriod, minTweetPeriod * 3 /8)).start();
        new Thread(new RichPoor(c, minTweetPeriod, minTweetPeriod * 7 /8)).start();
        //new SmartStupid(2 * 60, 2 * 60);
        c.start();
        
    }
}
