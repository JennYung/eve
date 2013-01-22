package com.almende.eve.rpc.jsonrpc;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.almende.eve.agent.annotation.AccessType;
import com.almende.eve.agent.annotation.Name;
import com.almende.eve.agent.annotation.Required;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.eve.agent.annotation.Access;
import com.almende.util.AnnotationUtil;
import com.almende.util.AnnotationUtil.AnnotatedClass;
import com.almende.util.AnnotationUtil.AnnotatedMethod;
import com.almende.util.AnnotationUtil.AnnotatedParam;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JSONRPC {
	//static private Logger logger = Logger.getLogger(JSONRPC.class.getName());

	// TODO: implement JSONRPC 2.0 Batch
	
	/**
	 * Invoke a method on an object
	 * @param obj     Request will be invoked on the given object
	 * @param request A request in JSON-RPC format
	 * @return
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonGenerationException 
	 */
	static public String invoke (Object object, String request) 
			throws JsonGenerationException, JsonMappingException, IOException {
		JSONRequest jsonRequest = null;
		JSONResponse jsonResponse = null;
		try {
			jsonRequest = new JSONRequest(request);
			jsonResponse = invoke(object, jsonRequest);
		}
		catch (JSONRPCException err) {
			jsonResponse = new JSONResponse(err);
		}
		
		return jsonResponse.toString();
	}

	/**
	 * Invoke a method on an object
	 * @param obj     Request will be invoked on the given object
	 * @param request A request in JSON-RPC format
	 * @return
	 */
	static public JSONResponse invoke (Object object, JSONRequest request) {
		JSONResponse resp = new JSONResponse(); 
		resp.setId(request.getId());

		try {
			AnnotatedMethod annotatedMethod = getMethod(object.getClass(), request.getMethod());
			if (annotatedMethod == null) {
				throw new JSONRPCException(
						JSONRPCException.CODE.METHOD_NOT_FOUND, 
						"Method '" + request.getMethod() + "' not found");
			}
			
			Method method = annotatedMethod.getActualMethod();
			
			Object[] params = castParams(request.getParams(), annotatedMethod.getParams());
			Object result = method.invoke(object, params);
			if (result == null) {
				result = JOM.createNullNode();
			}
			resp.setResult(result);
		}
		catch (Exception err) {
			if (err instanceof JSONRPCException) {
				resp.setError((JSONRPCException) err);
			}
			else if (err.getCause() != null && 
					err.getCause() instanceof JSONRPCException) {
				resp.setError((JSONRPCException) err.getCause());
			}
			else {
				JSONRPCException jsonError = new JSONRPCException(
						JSONRPCException.CODE.INTERNAL_ERROR, getMessage(err));
				resp.setError(jsonError);
			}
		}
		
		return resp;
	}
	
	/**
	 * Validate whether the given class contains valid JSON-RPC methods.
	 * A class if valid when:<br>
	 * - There are no public methods with equal names<br>
	 * - The parameters of all public methods have the @Name annotation<br>
	 * If the class is not valid, an Exception is thrown
	 * @param c         The class to be verified
	 * @return errors   A list with validation errors. When no problems are 
	 *                   found, an empty list is returned 
	 */
	static public List<String> validate (Class<?> c) {
		List<String> errors = new ArrayList<String>();
		Set<String> methodNames = new HashSet<String>();
		
		AnnotatedClass ac = AnnotationUtil.get(c);
		for (AnnotatedMethod method : ac.getMethods()) {
			boolean available = isAvailable(method);				
			if (available) {
				// The method name may only occur once
				String name = method.getName();
				if (methodNames.contains(name)) {
					errors.add("Public method '" + name + 
						"' is defined more than once, which is not" + 
						" allowed for JSON-RPC (Class " + c.getName() + ")");
				}
				methodNames.add(name);
				
				// each of the method parameters must have the @Name annotation
				List<AnnotatedParam> params = method.getParams();
				for(int i = 0; i < params.size(); i++){
					if (getName(params.get(i)) == null) {
						errors.add("Parameter " + i + " in public method '" + name + 
							"' is missing the @Name annotation, which is" + 
							" required for JSON-RPC (Class " + c.getName() + ")");
					}
				}
			}
		}
		
		return errors;
	}

	/**
	 * Describe all JSON-RPC methods of given class
	 * @param c      The class to be described
	 * @param asJSON If true, the described methods will be in an easy to parse
	 *                JSON structure. If false, the returned description will
	 *                be in human readable format.
	 * @return
	 */
	public static List<Object> describe(Class<?> c, Boolean asJSON) {
		try {
			Map<String, Object> methods = new TreeMap<String, Object>();
			if (asJSON == null) {
				asJSON = false;
			}

			AnnotatedClass annotatedClass = AnnotationUtil.get(c);
			for (AnnotatedMethod method : annotatedClass.getMethods()) {
				if (isAvailable(method)) {
					if (asJSON) {
						// format as JSON
						List<Object> descParams = new ArrayList<Object>();
						for(AnnotatedParam param : method.getParams()){
							
							Map<String, Object> paramData = new HashMap<String, Object>();
							paramData.put("name", getName(param));
							paramData.put("type", typeToString(param.getGenericType()));
							paramData.put("required", isRequired(param));
							descParams.add(paramData);
						}
						
						Map<String, Object> result = new HashMap<String, Object>(); 
						result.put("type", typeToString(method.getGenericReturnType()));
						
						Map<String, Object> desc = new HashMap<String, Object>();
						desc.put("method", method.getName());
						desc.put("params", descParams);
						desc.put("result", result);
						methods.put(method.getName(), desc);
					}
					else {
						// format as string
						String p = "";
						for(AnnotatedParam param : method.getParams()){
							if (!p.isEmpty()) {
								p += ", ";
							}
							String ps = typeToString(param.getGenericType()) + 
									" " + getName(param);
							p += isRequired(param) ? ps : ("[" + ps + "]");
						}
						String desc = typeToString(method.getGenericReturnType()) + 
								" " + method.getName() + "(" + p + ")";
						methods.put(method.getName(), desc);							
					}
				}				
			}
			
			// create a sorted array
			List<Object> sortedMethods = new ArrayList<Object>();
			TreeSet<String> methodNames = new TreeSet<String>(methods.keySet());
			for (String methodName : methodNames) { 
			   sortedMethods.add(methods.get(methodName));
			}
			return sortedMethods;
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Get type description from a class. Returns for example "String" or 
	 * "List<String>".
	 * @param c
	 * @return
	 */
	private static String typeToString(Type c) {
		String s = c.toString();
		
		// replace full namespaces to short names
		int point = s.lastIndexOf(".");
		while (point >= 0) {
			int angle = s.lastIndexOf("<", point);
			int space = s.lastIndexOf(" ", point);
			int start = Math.max(angle, space);
			s = s.substring(0, start + 1) + s.substring(point + 1);
			point = s.lastIndexOf(".");
		}
		
		// remove modifiers like "class blabla" or "interface blabla"
		int space = s.indexOf(" ");
		int angle = s.indexOf("<", point);
		if (space >= 0 && (angle < 0 || angle > space)) {
			s = s.substring(space + 1);
		}
		
		return s;
		
		/*
		// TODO: do some more professional reflection...
		String s = c.getSimpleName();	

		// the following seems not to work
		TypeVariable<?>[] types = c.getTypeParameters();
		if (types.length > 0) {
			s += "<";
			for (int j = 0; j < types.length; j++) {
				TypeVariable<?> jj = types[j];
				s += jj.getName();
				 ... not working
				//s += types[j].getClass().getSimpleName();
			}
			s += ">";
		}
		*/
	}
	
	/**
	 * Try to retrieve the message description of an error
	 * @param error
	 * @return message  String with the error description, or null if not found.
	 */
	static private String getMessage(Throwable error) {
		String message = error.getMessage();
		if (message == null && error.getCause() != null) {
			message = error.getCause().getMessage();
		}
		return message;
	}

	/**
	 * Find a method by name, 
	 * which is available for JSON-RPC, and has named parameters
	 * @param objectClass
	 * @param method
	 * @return methodType   meta information on the method, or null if not found
	 */
	static private AnnotatedMethod getMethod(Class<?> objectClass, String method) {
		AnnotatedClass annotatedClass = AnnotationUtil.get(objectClass);
		List<AnnotatedMethod> methods = annotatedClass.getMethods(method);
		for (AnnotatedMethod m : methods) {
			if (isAvailable(m)) {
				return m;
			}
		}
		return null;
	}

	/**
	 * Cast a JSONArray or JSONObject params to the desired paramTypes 
	 * @param params
	 * @param paramTypes
	 * @return 
	 * @throws Exception 
	 */
	static private Object[] castParams(Object params, List<AnnotatedParam> annotatedParams) 
			throws Exception {
		ObjectMapper mapper = JOM.getInstance();

		if (annotatedParams.size() == 0) {
			return new Object[0];
		}
		
		if (params instanceof ObjectNode) {
			// JSON-RPC 2.0 with named parameters in a JSONObject

			// check whether all method parameters are named
			boolean hasNamedParams = true;
			for (AnnotatedParam p : annotatedParams) {
				if (getName(p) == null) {
					hasNamedParams = false;
				}
			}

			if (hasNamedParams) {
				ObjectNode paramsObject = (ObjectNode)params;
				
				Object[] objects = new Object[annotatedParams.size()];
				for (int i = 0; i < annotatedParams.size(); i++) {
					AnnotatedParam p = annotatedParams.get(i);
					String name = getName(p);
					if (name == null) {
						throw new Exception("Name of parameter " + i + " not defined");
					}
					if (paramsObject.has(name)) {
						objects[i] = mapper.convertValue(paramsObject.get(name), p.getType());
					}
					else {
						if (isRequired(p)) {
							throw new Exception(
									"Required parameter '" + name + "' missing");
						}
						//else if (paramType.getSuperclass() == null) {
						else if (p.getType().isPrimitive()) {
							throw new Exception(
									"Parameter '" + name + "' cannot be both optional and " +
									"a primitive type (" + p.getType().getSimpleName() + ")");
						}
						else {
							objects[i] = null;
						}
					}
				}
				return objects;
			}
			else if (annotatedParams.size() == 1 && 
					annotatedParams.get(0).getType().equals(ObjectNode.class)) {
				// the method expects one parameter of type JSONObject
				// feed the params object itself to it.
				Object[] objects = new Object[1];
				objects[0] = params;
				return objects;
			}
			else {
				throw new Exception("Names of parameters are undefined");
			}
		}
		else {
			throw new Exception("params must be a JSONObject");
		}
	}

	/**
	 * Create a JSONRequest from a java method and arguments
	 * @param method
	 * @param args
	 * @return
	 */
	public static JSONRequest createRequest(Method method, Object[] args) {
		AnnotatedMethod annotatedMethod = new AnnotationUtil.AnnotatedMethod(method);
		List<AnnotatedParam> annotatedParams = annotatedMethod.getParams();
		
		ObjectNode params = JOM.createObjectNode();
		
		for(int i = 0; i < annotatedParams.size(); i++) {
			AnnotatedParam annotatedParam = annotatedParams.get(i);
			if (i < args.length && args[i] != null) {
				String name = getName(annotatedParam);
				if (name != null) {
					JsonNode paramValue = JOM.getInstance().convertValue(args[i], 
							JsonNode.class);
					params.put(name, paramValue);
				}
				else {
					throw new IllegalArgumentException(
							"Parameter " + i + " in method '" + method.getName() + 
							"' is missing the @Name annotation.");
				}
			}
			else if (isRequired(annotatedParam)) {
				throw new IllegalArgumentException(
						"Required parameter " + i + " in method '" + method.getName() + 
						"' is null.");
			}
		}
		
		return new JSONRequest(method.getName(), params);		
	}

	/**
	 * Check whether a method is available for JSON-RPC calls. This is the
	 * case when it is public, has named parameters, and has no 
	 * annotation @Access(UNAVAILABLE)
	 * @param annotatedMethod
	 * @return available
	 */
	private static boolean isAvailable(AnnotatedMethod method) {
		int mod = method.getActualMethod().getModifiers();
		Access access = method.getAnnotation(Access.class); 
		return Modifier.isPublic(mod) &&
				hasNamedParams(method) &&
				(access == null || 
				(access.value() != AccessType.UNAVAILABLE &&
				 access.visible()));
	}
	
	/**
	 * Test whether a method has named parameters
	 * @param annotatedMethod
	 * @return hasNamedParams
	 */
	private static boolean hasNamedParams(AnnotatedMethod method) {
		for (AnnotatedParam param : method.getParams()) {
			if (getName(param) == null) {
				return false;
			}
		}		
		return true;
	}
	
	/**
	 * Test if a parameter is required
	 * Reads the parameter annotation @Required. Returns True if the annotation
	 * is not provided.
	 * @param param
	 * @return required
	 */
	private static boolean isRequired(AnnotatedParam param) {
		boolean required = true;
		Required requiredAnnotation = param.getAnnotation(Required.class);
		if (requiredAnnotation != null) {
			required = requiredAnnotation.value();
		}
		return required;
	}

	/**
	 * Get the name of a parameter
	 * Reads the parameter annotation @Name. 
	 * Returns null if the annotation is not provided.
	 * @param param
	 * @return name
	 */
	private static String getName(AnnotatedParam param) {
		String name = null;
		Name nameAnnotation = param.getAnnotation(Name.class);
		if (nameAnnotation != null) {
			name = nameAnnotation.value();
		}
		return name;
	}
}