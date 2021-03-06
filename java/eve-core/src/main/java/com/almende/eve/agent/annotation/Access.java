package com.almende.eve.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// TODO: replace Access annotation by something more simple (see Jersey JSON annotation)
@Retention(RetentionPolicy.RUNTIME)
@Target(value=ElementType.METHOD)
public @interface Access {
	AccessType value();
	String[] roles() default {};
	boolean visible() default true;  // visible in getMethods()
}
