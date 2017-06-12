package com.saintgobain.sg4pTool.beans.properties;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Class to save properties differences
 * @author Xuhao
 *
 */
public class PropertiesDifferent {
	
	private String key;
	private String originalValue;
	private String differentValue;
	private String theKeyBeforeThis;
	
	public PropertiesDifferent() {
		this.key = "";
		this.originalValue = "";
		this.differentValue = "";
		this.theKeyBeforeThis = "";

	}
	
	public PropertiesDifferent(String key, String originalValue, String differentValue, String theKeyBeforeThis) {
		this.key = key;
		this.originalValue = originalValue;
		this.differentValue = differentValue;
		this.theKeyBeforeThis = theKeyBeforeThis;

	}
	public String getKey() {
		return key;
	}
	public void setKey(String key) {
		this.key = key;
	}
	public String getOriginalValue() {
		return originalValue;
	}
	public void setOriginalValue(String originalValue) {
		this.originalValue = originalValue;
	}
	public String getDifferentValue() {
		return differentValue;
	}
	public void setDifferentValue(String differentValue) {
		this.differentValue = differentValue;
	}

	public String getTheKeyBeforeThis() {
		return theKeyBeforeThis;
	}

	public void setTheKeyBeforeThis(String theKeyBeforeThis) {
		this.theKeyBeforeThis = theKeyBeforeThis;
	}
	
	@Override 
	public String toString(){
		return ToStringBuilder.reflectionToString(this,
                ToStringStyle.SHORT_PREFIX_STYLE);
	}


}
