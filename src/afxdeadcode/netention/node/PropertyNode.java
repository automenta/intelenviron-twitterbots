/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package afxdeadcode.netention.node;

import afxdeadcode.netention.Node;
import afxdeadcode.netention.PropertyValue;



/**
 * wraps a property value as a node
 */
public class PropertyNode extends Node {

    public PropertyNode(PropertyValue pv) {
        super(pv.getProperty());
    }


}
