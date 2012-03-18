/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package afxdeadcode;

import automenta.netention.Detail;
import java.awt.BorderLayout;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

/**
 *
 * @author SeH
 */
public class CommunityBrowser extends JPanel implements ListSelectionListener {
    private final JSplitPane split;
    private final JTable table;
    private final JPanel userView;
    private final DefaultTableModel tableModel;
    private final Community community;
    private boolean updating;

    public CommunityBrowser(Community c) {
        super(new BorderLayout());
        
        this.community = c;
        
        tableModel = new DefaultTableModel();
        tableModel.addColumn("ID");
        tableModel.addColumn("Last Updated");
        tableModel.addColumn("Tweets Read");
        tableModel.addColumn("Happy|Sad");
        tableModel.addColumn("Rich|Poor");

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        
        
        split.setLeftComponent(new JScrollPane(table = new JTable(tableModel)));
        split.setRightComponent(new JScrollPane(userView = new JPanel()));
        
        table.getSelectionModel().addListSelectionListener(this);
        table.setAutoCreateRowSorter(true);
        table.setCellEditor(null);
        
        add(split, BorderLayout.CENTER);

        new Thread(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    update();
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(CommunityBrowser.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            
        }).start();
    }

    public int getRow(String username) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getValueAt(i, 0).toString().equals(username))
                return i;
        }
        return -1;
    }
    
    protected void update() {
        int selected = table.getSelectedRow();
        updating = true;
        final Date d = new Date();
        for (Agent a : community.agents.values()) {
            int existingRow = getRow(a.name);
            if (existingRow!=-1)
                tableModel.removeRow(existingRow);
            
            double happy = a.getScore(community.classifier, d, "happy");
            double sad = a.getScore(community.classifier, d, "sad");
            double rich = a.getScore(community.classifier, d, "rich");
            double poor = a.getScore(community.classifier, d, "poor");
            
            tableModel.addRow(new Object[] { a.name, a.lastContacted, a.details.size(), (happy/sad), (rich/poor) } );
        }
        updating = false;
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                updateUI();
            }            
        });
        
        
    }

    @Override
    public synchronized void valueChanged(ListSelectionEvent e) {
        if (updating)
            return;
        
        int selected = table.getSelectedRow();
        
        if (selected!=-1) {
            userView.removeAll();
            
            String user = table.getValueAt(selected, 0).toString();
            
            List<Detail> l = new LinkedList(community.getAgent(user).details);
            Collections.sort(l, new Comparator<Detail>() {
                @Override public int compare(Detail o1, Detail o2) {
                    return o2.getWhen().compareTo(o1.getWhen());
                }                
            });
            
            String p = "";
            for (Detail d : l) {
                p += d.getName() + " (" + d.getWhen() + ")" + "\n\n";
            }

            JTextArea t = new JTextArea(p, 80, 50);
            t.setEditable(false);
            t.setLineWrap(true);
            t.setWrapStyleWord(true);
            
            
            userView.add(t, BorderLayout.CENTER);
            
            updateUI();
        }
    
    }

    
    
}
