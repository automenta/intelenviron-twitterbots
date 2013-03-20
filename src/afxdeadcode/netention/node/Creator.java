/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package afxdeadcode.netention.node;

import afxdeadcode.netention.Detail;
import afxdeadcode.netention.Node;


/**
 *
 * @author seh
 */
public class Creator extends Node {

    public Creator(Detail d) {
        this(d.getCreator());
    }
    
    public Creator(String creatorURI) {
        super(creatorURI);
    }


}
