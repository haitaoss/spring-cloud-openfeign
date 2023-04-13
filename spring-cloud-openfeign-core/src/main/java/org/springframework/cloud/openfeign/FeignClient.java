/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.openfeign;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * Annotation for interfaces declaring that a REST client with that interface should be
 * created (e.g. for autowiring into another component). If SC LoadBalancer is available
 * it will be used to load balance the backend requests, and the load balancer can be
 * configured using the same name (i.e. value) as the feign client.
 *
 * @author Spencer Gibb
 * @author Venil Noronha
 * @author Olga Maciaszek-Sharma
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface FeignClient {

	/**
	 * 服务名。支持占位符解析
	 * The name of the service with optional protocol prefix. Synonym for {@link #name()
	 * name}. A name must be specified for all clients, whether or not a url is provided.
	 * Can be specified as property key, eg: ${propertyKey}.
	 * @return the name of the service with optional protocol prefix
	 */
	@AliasFor("name")
	String value() default "";

	/**
	 * FeignClient Context 的ID，没指定会使用 name 或者 value 的值。支持占位符解析
	 * This will be used as the bean name instead of name if present, but will not be used
	 * as a service id.
	 * @return bean name instead of name if present
	 */
	String contextId() default "";

	/**
	 * 服务名。支持占位符解析
	 * @return The service id with optional protocol prefix. Synonym for {@link #value()
	 * value}.
	 */
	@AliasFor("value")
	String name() default "";

	/**
	 * 别名
	 * @return the <code>@Qualifier</code> value for the feign client.
	 * @deprecated in favour of {@link #qualifiers()}.
	 *
	 * If both {@link #qualifier()} and {@link #qualifiers()} are present, we will use the
	 * latter, unless the array returned by {@link #qualifiers()} is empty or only
	 * contains <code>null</code> or whitespace values, in which case we'll fall back
	 * first to {@link #qualifier()} and, if that's also not present, to the default =
	 * <code>contextId + "FeignClient"</code>.
	 */
	@Deprecated
	String qualifier() default "";

	/**
	 * @return the <code>@Qualifiers</code> value for the feign client.
	 *
	 * If both {@link #qualifier()} and {@link #qualifiers()} are present, we will use the
	 * latter, unless the array returned by {@link #qualifiers()} is empty or only
	 * contains <code>null</code> or whitespace values, in which case we'll fall back
	 * first to {@link #qualifier()} and, if that's also not present, to the default =
	 * <code>contextId + "FeignClient"</code>.
	 */
	String[] qualifiers() default {};

	/**
	 * 若指定这个值，那就不会使用 name，也就不会变成负载均衡请求了。支持占位符解析
	 * @return an absolute URL or resolvable hostname (the protocol is optional).
	 */
	String url() default "";

	/**
	 * @return whether 404s should be decoded instead of throwing FeignExceptions
	 */
	boolean decode404() default false;

	/**
	 * 给 FeignClient Context 设置默认配置类
	 * A custom configuration class for the feign client. Can contain override
	 * <code>@Bean</code> definition for the pieces that make up the client, for instance
	 * {@link feign.codec.Decoder}, {@link feign.codec.Encoder}, {@link feign.Contract}.
	 *
	 * @see FeignClientsConfiguration for the defaults
	 * @return list of configurations for feign client
	 */
	Class<?>[] configuration() default {};

	/**
	 * 使用 {@link FeignCircuitBreaker.Builder} 构造的 FeignClient 才会用到这个属性，
	 * 是用来给 CircuitBreaker 使用的，用于在执行HTTP请求时出错后 的兜底策略。
	 *
	 * 需要注册到容器中才行
	 * Fallback class for the specified Feign client interface. The fallback class must
	 * implement the interface annotated by this annotation and be a valid spring bean.
	 * @return fallback class for the specified Feign client interface
	 */
	Class<?> fallback() default void.class;

	/**
	 * 和 {@link FeignClient#fallback()} 的用法类似，只不过这个是用来创建 fallback的。
	 * 如果指定了 fallback ，那么这个属性就没用了。
	 *
	 * 需要注册到容器中才行
	 * Define a fallback factory for the specified Feign client interface. The fallback
	 * factory must produce instances of fallback classes that implement the interface
	 * annotated by {@link FeignClient}. The fallback factory must be a valid spring bean.
	 *
	 * @see FallbackFactory for details.
	 * @return fallback factory for the specified Feign client interface
	 */
	Class<?> fallbackFactory() default void.class;

	/**
	 * 访问路径是 url + path 。支持占位符解析
	 * @return path prefix to be used by all method-level mappings.
	 */
	String path() default "";

	/**
	 * bean是否是 @Primary 的
	 * @return whether to mark the feign proxy as a primary bean. Defaults to true.
	 */
	boolean primary() default true;

}
