/*
 * Created on Oct 26, 2004
 */
package org.gridlab.gat.engine;

import org.gridlab.gat.monitoring.MetricDefinition;
import org.gridlab.gat.monitoring.MetricValue;

/**
 * @author rob
 */
class MetricNode {

	Object adaptor;

	String methodName;

	MetricDefinition definition;

	MetricValue lastValue;

	MetricNode(Object adaptor, String methodName, MetricDefinition definition) {
		this.adaptor = adaptor;
		this.methodName = methodName;
		this.definition = definition;
	}

	void setLastValue(MetricValue v) {
		lastValue = v;
	}
	/*
	 * public boolean equals(Object o) { if (!(o instanceof MetricNode)) return
	 * false;
	 * 
	 * MetricNode other = (MetricNode) o;
	 * 
	 * return other.adaptor == adaptor; }
	 */
}