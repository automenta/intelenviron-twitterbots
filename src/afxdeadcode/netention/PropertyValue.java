/**
 * 
 */
package afxdeadcode.netention;



public class PropertyValue implements Value {
	
	private String property;

        public PropertyValue() {
            property = null;
        }

	/** property ID or URI */
	public String getProperty() {
		return property;
	}

	public void setProperty(String property) {
		this.property = property;
	}
 
}