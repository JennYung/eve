/**
 * Singleton Jackson ObjectMapper
 */
package com.almende.eve.rpc.jsonrpc.jackson;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JOM {
	private static ObjectMapper m = null;
	
	protected JOM() {}
	
	public static ObjectMapper getInstance() {
		if (m != null) {
			return m;
		}
		
		m = createInstance();
		return m;
	}
	
	public static ObjectNode createObjectNode() {
		return getInstance().createObjectNode();
	}
	
	public static ArrayNode createArrayNode() {
		return getInstance().createArrayNode();
	}
	
	public static NullNode createNullNode() {
		return NullNode.getInstance();
	}
	
	private static synchronized ObjectMapper createInstance () {
		ObjectMapper mapper = new ObjectMapper();
		
		// set configuration
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL , false);
		
		return mapper;
	}	
}
