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

import feign.Feign;
import feign.Target;

import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.util.StringUtils;

@SuppressWarnings("unchecked")
class FeignCircuitBreakerTargeter implements Targeter {

	private final CircuitBreakerFactory circuitBreakerFactory;

	private final boolean circuitBreakerGroupEnabled;

	private final CircuitBreakerNameResolver circuitBreakerNameResolver;

	FeignCircuitBreakerTargeter(CircuitBreakerFactory circuitBreakerFactory, boolean circuitBreakerGroupEnabled,
			CircuitBreakerNameResolver circuitBreakerNameResolver) {
		this.circuitBreakerFactory = circuitBreakerFactory;
		this.circuitBreakerGroupEnabled = circuitBreakerGroupEnabled;
		this.circuitBreakerNameResolver = circuitBreakerNameResolver;
	}

	@Override
	public <T> T target(FeignClientFactoryBean factory, Feign.Builder feign, FeignContext context,
			Target.HardCodedTarget<T> target) {
		// 不是 FeignCircuitBreaker 类型的就不做处理
		if (!(feign instanceof FeignCircuitBreaker.Builder)) {
			return feign.target(target);
		}
		FeignCircuitBreaker.Builder builder = (FeignCircuitBreaker.Builder) feign;
		String name = !StringUtils.hasText(factory.getContextId()) ? factory.getName() : factory.getContextId();
		/**
		 * 前置知识：Feign 其实是通过JDK动态代理 为接口创建出代理对象，所以要想拦截方法的执行只需要配置 InvocationHandler 即可。
		 *
		 * 下面的几行代码的最终目都是设置 FeignCircuitBreakerInvocationHandler 作为代理对象的 InvocationHandler，
		 * 所以关键还得看 FeignCircuitBreakerInvocationHandler
		 *
		 * {@link FeignCircuitBreakerInvocationHandler#invoke(Object, Method, Object[])}
		 * 		大致流程是方法的执行交给 CircuitBreaker 执行 {@link CircuitBreaker#run(Supplier, Function)}
		 * 		CircuitBreaker 可以拿到 fallback 或者 fallbackFactory。可以决定什么时候回调 fallback 的逻辑
		 * */
		Class<?> fallback = factory.getFallback();
		if (fallback != void.class) {
			// 存在 fallback 的情况
			return targetWithFallback(name, context, target, builder, fallback);
		}
		Class<?> fallbackFactory = factory.getFallbackFactory();
		if (fallbackFactory != void.class) {
			// 存在 fallbackFactory 的情况
			return targetWithFallbackFactory(name, context, target, builder, fallbackFactory);
		}
		return builder(name, builder).target(target);
	}

	private <T> T targetWithFallbackFactory(String feignClientName, FeignContext context,
			Target.HardCodedTarget<T> target, FeignCircuitBreaker.Builder builder, Class<?> fallbackFactoryClass) {
		FallbackFactory<? extends T> fallbackFactory = (FallbackFactory<? extends T>) getFromContext("fallbackFactory",
				feignClientName, context, fallbackFactoryClass, FallbackFactory.class);
		return builder(feignClientName, builder).target(target, fallbackFactory);
	}

	private <T> T targetWithFallback(String feignClientName, FeignContext context, Target.HardCodedTarget<T> target,
			FeignCircuitBreaker.Builder builder, Class<?> fallback) {
		T fallbackInstance = getFromContext("fallback", feignClientName, context, fallback, target.type());
		return builder(feignClientName, builder).target(target, fallbackInstance);
	}

	private <T> T getFromContext(String fallbackMechanism, String feignClientName, FeignContext context,
			Class<?> beanType, Class<T> targetType) {
		Object fallbackInstance = context.getInstance(feignClientName, beanType);
		if (fallbackInstance == null) {
			throw new IllegalStateException(
					String.format("No " + fallbackMechanism + " instance of type %s found for feign client %s",
							beanType, feignClientName));
		}

		if (!targetType.isAssignableFrom(beanType)) {
			throw new IllegalStateException(String.format("Incompatible " + fallbackMechanism
					+ " instance. Fallback/fallbackFactory of type %s is not assignable to %s for feign client %s",
					beanType, targetType, feignClientName));
		}
		return (T) fallbackInstance;
	}

	private FeignCircuitBreaker.Builder builder(String feignClientName, FeignCircuitBreaker.Builder builder) {
		return builder.circuitBreakerFactory(circuitBreakerFactory).feignClientName(feignClientName)
				.circuitBreakerGroupEnabled(circuitBreakerGroupEnabled)
				.circuitBreakerNameResolver(circuitBreakerNameResolver);
	}

}
