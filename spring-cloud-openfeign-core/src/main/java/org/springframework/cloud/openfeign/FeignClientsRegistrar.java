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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import feign.Request;

import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * @author Spencer Gibb
 * @author Jakub Narloch
 * @author Venil Noronha
 * @author Gang Li
 * @author Michal Domagala
 * @author Marcin Grzejszczak
 * @author Olga Maciaszek-Sharma
 * @author Jasbir Singh
 */
class FeignClientsRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {

	// patterned after Spring Integration IntegrationComponentScanRegistrar
	// and RibbonClientsConfigurationRegistgrar

	private ResourceLoader resourceLoader;

	private Environment environment;

	FeignClientsRegistrar() {
	}

	static void validateFallback(final Class clazz) {
		Assert.isTrue(!clazz.isInterface(), "Fallback class must implement the interface annotated by @FeignClient");
	}

	static void validateFallbackFactory(final Class clazz) {
		Assert.isTrue(!clazz.isInterface(), "Fallback factory must produce instances "
				+ "of fallback classes that implement the interface annotated by @FeignClient");
	}

	static String getName(String name) {
		if (!StringUtils.hasText(name)) {
			return "";
		}

		String host = null;
		try {
			String url;
            // 若没有设置协议名，那就使用 http://
			if (!name.startsWith("http://") && !name.startsWith("https://")) {
				url = "http://" + name;
			}
			else {
				url = name;
			}
			host = new URI(url).getHost();

		}
		catch (URISyntaxException e) {
		}
		Assert.state(host != null, "Service id not legal hostname (" + name + ")");
		return name;
	}

	static String getUrl(String url) {
		if (StringUtils.hasText(url) && !(url.startsWith("#{") && url.contains("}"))) {
			if (!url.contains("://")) {
				url = "http://" + url;
			}
			try {
				new URL(url);
			}
			catch (MalformedURLException e) {
				throw new IllegalArgumentException(url + " is malformed", e);
			}
		}
		return url;
	}

	static String getPath(String path) {
		if (StringUtils.hasText(path)) {
			path = path.trim();
			if (!path.startsWith("/")) {
				path = "/" + path;
			}
			if (path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
		}
		return path;
	}

	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	@Override
	public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        /**
         * 注册默认配置
         *
         * @EnableFeignClients(defaultConfiguration={A.class})
         * public class Config{}
         *
         * 获取 defaultConfiguration 注解值映射成 BeanDefinition 注册到 BeanFactory 中
         * 		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(FeignClientSpecification.class);
         * 		builder.addConstructorArgValue("default."+Config.class.getName());
         * 		builder.addConstructorArgValue(defaultConfiguration);
         * 		registry.registerBeanDefinition(name + "." + FeignClientSpecification.class.getSimpleName(),
         * 				builder.getBeanDefinition());
         *
         *		Tips: FeignContext 继承 NamedContextFactory, 会依赖 FeignClientSpecification 类型的bean 用来配置要生成的IOC容器。
         *			  FeignContext 会使用 beanName是 "default." 前缀的 FeignClientSpecification 作为默认项，用来配置要生成的IOC容器
         *
         * */
		registerDefaultConfiguration(metadata, registry);
        // 注册 FeignClient
		registerFeignClients(metadata, registry);
	}

	private void registerDefaultConfiguration(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
		Map<String, Object> defaultAttrs = metadata.getAnnotationAttributes(EnableFeignClients.class.getName(), true);

		if (defaultAttrs != null && defaultAttrs.containsKey("defaultConfiguration")) {
			String name;
			if (metadata.hasEnclosingClass()) {
				name = "default." + metadata.getEnclosingClassName();
			}
			else {
				name = "default." + metadata.getClassName();
			}
			registerClientConfiguration(registry, name, defaultAttrs.get("defaultConfiguration"));
		}
	}

	public void registerFeignClients(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {

		LinkedHashSet<BeanDefinition> candidateComponents = new LinkedHashSet<>();
		Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableFeignClients.class.getName());
		final Class<?>[] clients = attrs == null ? null : (Class<?>[]) attrs.get("clients");
        // @EnableFeignClients 没有设置 clients 值，那就扫描包下的类得到
		if (clients == null || clients.length == 0) {
			ClassPathScanningCandidateComponentProvider scanner = getScanner();
			scanner.setResourceLoader(this.resourceLoader);
            // 配置 include 规则，只会收集有 @FeignClient 的类
			scanner.addIncludeFilter(new AnnotationTypeFilter(FeignClient.class));
            /**
             * @EnableFeignClients(value、basePackages、basePackageClasses) 的值作为要扫描的包路径，
             * 若这三个注解值都没设置，那就是用标注了 @EnableFeignClients 注解所在的类的包作为要扫描的包
             * */
			Set<String> basePackages = getBasePackages(metadata);
			for (String basePackage : basePackages) {
                // 开始扫描，将扫描的结果存到 candidateComponents
				candidateComponents.addAll(scanner.findCandidateComponents(basePackage));
			}
        } else {
            // @EnableFeignClients 设置了 clients 值，那就只使用这些值
			for (Class<?> clazz : clients) {
				candidateComponents.add(new AnnotatedGenericBeanDefinition(clazz));
			}
		}

        // 遍历 candidateComponents 挨个映射成 BeanDefinition 注册到 BeanFactory 中
		for (BeanDefinition candidateComponent : candidateComponents) {
			if (candidateComponent instanceof AnnotatedBeanDefinition) {
				// verify annotated class is an interface
				AnnotatedBeanDefinition beanDefinition = (AnnotatedBeanDefinition) candidateComponent;
				AnnotationMetadata annotationMetadata = beanDefinition.getMetadata();
        		// @FeignClient 标注的类 不是接口就报错
				Assert.isTrue(annotationMetadata.isInterface(), "@FeignClient can only be specified on an interface");

        		// 拿到注解的属性值
				Map<String, Object> attributes = annotationMetadata
						.getAnnotationAttributes(FeignClient.class.getCanonicalName());

        		/**
        		 * 为空就依次获取属性 contextId -> value -> name  都没设置就报错。
        		 *
        		 * 注：这里感觉不合理，应该也要解析占位符的，因为下面的会解析这里不解析 会导致不一致的
        		 * */
				String name = getClientName(attributes);
        		/**
        		 * 设置了  @FeignClient(configuration={A.class}) 那就映射成 BeanDefinition 注册到 BeanFactory 中
        		 *
        		 *		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(FeignClientSpecification.class);
        		 * 		builder.addConstructorArgValue(name);
        		 * 		builder.addConstructorArgValue(configuration);
        		 * 		registry.registerBeanDefinition(name + "." + FeignClientSpecification.class.getSimpleName(),
        		 * 				builder.getBeanDefinition());
        		 * */
				registerClientConfiguration(registry, name, attributes.get("configuration"));

				// 注册 FeignClient
				registerFeignClient(registry, annotationMetadata, attributes);
			}
		}
	}

	private void registerFeignClient(BeanDefinitionRegistry registry, AnnotationMetadata annotationMetadata,
			Map<String, Object> attributes) {
        // 拿到标注了注解的类名
		String className = annotationMetadata.getClassName();
		Class clazz = ClassUtils.resolveClassName(className, null);
		ConfigurableBeanFactory beanFactory = registry instanceof ConfigurableBeanFactory
				? (ConfigurableBeanFactory) registry : null;
        /**
         * 获取属性值，值为空就依次获取: contextId -> name -> value
         * 最后使用 environment.resolvePlaceholders(value) 解析占位符，也就是说这些注解值是支持使用 ${}、#{} 的
         * 如果都没有设置，那么 contextId 就是空字符串
         * */
		String contextId = getContextId(beanFactory, attributes);
        /**
         * 同上，只不过获取的是 name -> value
         * */
		String name = getName(attributes);
        // new 一个 FeignClientFactoryBean
		FeignClientFactoryBean factoryBean = new FeignClientFactoryBean();
		factoryBean.setBeanFactory(beanFactory);
		factoryBean.setName(name);
		factoryBean.setContextId(contextId);
		factoryBean.setType(clazz);
		// 根据 属性 feign.client.refresh-enabled 设置
		factoryBean.setRefreshableClient(isClientRefreshEnabled());
		/**
		 * 设置 Supplier , 也就是实例化会回调 Supplier 得到实例
		 *
		 * Tips：因为每次实例化bean都会重新设置 url、path 的值且支持使用占位符，会根据属性文件配置的值进行替换，所以我们可以
		 * 		将 bean 设置成 refresh 作用域的，然后就能实现 url、path 的动态更新
		 *
		 * 		可以通过设置属性让bean变成refresh作用域的 spring.cloud.refresh.ExtraRefreshable=XxFeignClient
		 * */
		BeanDefinitionBuilder definition = BeanDefinitionBuilder.genericBeanDefinition(clazz, () -> {
        	// 根据 @FeignClient(url="")的值来设置，会解析占位符，还会补全http://
			factoryBean.setUrl(getUrl(beanFactory, attributes));
        	// 获取 @FeignClient(path="")的值来设置,会解析占位符, 会补上前缀/,移除后缀/
			factoryBean.setPath(getPath(beanFactory, attributes));
        	// 剩下的就是简单读取值然后设置给factoryBean
			factoryBean.setDecode404(Boolean.parseBoolean(String.valueOf(attributes.get("decode404"))));
			Object fallback = attributes.get("fallback");
			if (fallback != null) {
				factoryBean.setFallback(fallback instanceof Class ? (Class<?>) fallback
						: ClassUtils.resolveClassName(fallback.toString(), null));
			}
			Object fallbackFactory = attributes.get("fallbackFactory");
			if (fallbackFactory != null) {
				factoryBean.setFallbackFactory(fallbackFactory instanceof Class ? (Class<?>) fallbackFactory
						: ClassUtils.resolveClassName(fallbackFactory.toString(), null));
			}
			return factoryBean.getObject();
		});
		definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        definition.setLazyInit(true); // 懒加载的
        // 校验  fallback、fallbackFactory 的值不能是接口
		validate(attributes);

		AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
		beanDefinition.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, className);
		beanDefinition.setAttribute("feignClientsRegistrarFactoryBean", factoryBean);

		// has a default, won't be null
		boolean primary = (Boolean) attributes.get("primary");

		beanDefinition.setPrimary(primary);

		// 作为别名
		String[] qualifiers = getQualifiers(attributes);
		if (ObjectUtils.isEmpty(qualifiers)) {
			qualifiers = new String[] { contextId + "FeignClient" };
		}

		BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className, qualifiers);
		// 注册到 BeanFactory 中
		BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);

		/**
		 * 如果 feign.client.refresh-enabled 是true那就注册 OptionsFactoryBean 到容器中
		 * 而且是 refresh 作用域的
		 *
		 * 当 FeignClientFactoryBean.getObject() 时会拿到 OptionsFactoryBean 用来配置 Feign.Builder
		 * */
		registerOptionsBeanDefinition(registry, contextId);
	}

	private void validate(Map<String, Object> attributes) {
		AnnotationAttributes annotation = AnnotationAttributes.fromMap(attributes);
		// This blows up if an aliased property is overspecified
		// FIXME annotation.getAliasedString("name", FeignClient.class, null);
		validateFallback(annotation.getClass("fallback"));
		validateFallbackFactory(annotation.getClass("fallbackFactory"));
	}

	/* for testing */ String getName(Map<String, Object> attributes) {
		return getName(null, attributes);
	}

	String getName(ConfigurableBeanFactory beanFactory, Map<String, Object> attributes) {
		String name = (String) attributes.get("serviceId");
		if (!StringUtils.hasText(name)) {
			name = (String) attributes.get("name");
		}
		if (!StringUtils.hasText(name)) {
			name = (String) attributes.get("value");
		}
		name = resolve(beanFactory, name);
		return getName(name);
	}

	private String getContextId(ConfigurableBeanFactory beanFactory, Map<String, Object> attributes) {
		String contextId = (String) attributes.get("contextId");
		if (!StringUtils.hasText(contextId)) {
			return getName(attributes);
		}

		contextId = resolve(beanFactory, contextId);
		return getName(contextId);
	}

	private String resolve(ConfigurableBeanFactory beanFactory, String value) {
		if (StringUtils.hasText(value)) {
			if (beanFactory == null) {
				return this.environment.resolvePlaceholders(value);
			}
			BeanExpressionResolver resolver = beanFactory.getBeanExpressionResolver();
			String resolved = beanFactory.resolveEmbeddedValue(value);
			if (resolver == null) {
				return resolved;
			}
			Object evaluateValue = resolver.evaluate(resolved, new BeanExpressionContext(beanFactory, null));
			if (evaluateValue != null) {
				return String.valueOf(evaluateValue);
			}
			return null;
		}
		return value;
	}

	private String getUrl(ConfigurableBeanFactory beanFactory, Map<String, Object> attributes) {
		String url = resolve(beanFactory, (String) attributes.get("url"));
		return getUrl(url);
	}

	private String getPath(ConfigurableBeanFactory beanFactory, Map<String, Object> attributes) {
		String path = resolve(beanFactory, (String) attributes.get("path"));
		return getPath(path);
	}

	protected ClassPathScanningCandidateComponentProvider getScanner() {
		return new ClassPathScanningCandidateComponentProvider(false, this.environment) {
			@Override
			protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
				boolean isCandidate = false;
				// 是独立的(也就是说 不是内部类)
				if (beanDefinition.getMetadata().isIndependent()) {
        			// 不是注解
					if (!beanDefinition.getMetadata().isAnnotation()) {
						isCandidate = true;
					}
				}
				return isCandidate;
			}
		};
	}

	protected Set<String> getBasePackages(AnnotationMetadata importingClassMetadata) {
		Map<String, Object> attributes = importingClassMetadata
				.getAnnotationAttributes(EnableFeignClients.class.getCanonicalName());

		Set<String> basePackages = new HashSet<>();
		for (String pkg : (String[]) attributes.get("value")) {
			if (StringUtils.hasText(pkg)) {
				basePackages.add(pkg);
			}
		}
		for (String pkg : (String[]) attributes.get("basePackages")) {
			if (StringUtils.hasText(pkg)) {
				basePackages.add(pkg);
			}
		}
		for (Class<?> clazz : (Class[]) attributes.get("basePackageClasses")) {
			basePackages.add(ClassUtils.getPackageName(clazz));
		}

		if (basePackages.isEmpty()) {
			basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
		}
		return basePackages;
	}

	private String getQualifier(Map<String, Object> client) {
		if (client == null) {
			return null;
		}
		String qualifier = (String) client.get("qualifier");
		if (StringUtils.hasText(qualifier)) {
			return qualifier;
		}
		return null;
	}

	private String[] getQualifiers(Map<String, Object> client) {
		if (client == null) {
			return null;
		}
		List<String> qualifierList = new ArrayList<>(Arrays.asList((String[]) client.get("qualifiers")));
		qualifierList.removeIf(qualifier -> !StringUtils.hasText(qualifier));
		if (qualifierList.isEmpty() && getQualifier(client) != null) {
			qualifierList = Collections.singletonList(getQualifier(client));
		}
		return !qualifierList.isEmpty() ? qualifierList.toArray(new String[0]) : null;
	}

	private String getClientName(Map<String, Object> client) {
		if (client == null) {
			return null;
		}
		String value = (String) client.get("contextId");
		if (!StringUtils.hasText(value)) {
			value = (String) client.get("value");
		}
		if (!StringUtils.hasText(value)) {
			value = (String) client.get("name");
		}
		if (!StringUtils.hasText(value)) {
			value = (String) client.get("serviceId");
		}
		if (StringUtils.hasText(value)) {
			return value;
		}

		throw new IllegalStateException(
				"Either 'name' or 'value' must be provided in @" + FeignClient.class.getSimpleName());
	}

	private void registerClientConfiguration(BeanDefinitionRegistry registry, Object name, Object configuration) {
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(FeignClientSpecification.class);
		builder.addConstructorArgValue(name);
		builder.addConstructorArgValue(configuration);
		registry.registerBeanDefinition(name + "." + FeignClientSpecification.class.getSimpleName(),
				builder.getBeanDefinition());
	}

	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	/**
	 * This method is meant to create {@link Request.Options} beans definition with
	 * refreshScope.
	 * @param registry spring bean definition registry
	 * @param contextId name of feign client
	 */
	private void registerOptionsBeanDefinition(BeanDefinitionRegistry registry, String contextId) {
		if (isClientRefreshEnabled()) {
			String beanName = Request.Options.class.getCanonicalName() + "-" + contextId;
			BeanDefinitionBuilder definitionBuilder = BeanDefinitionBuilder
					.genericBeanDefinition(OptionsFactoryBean.class);
			definitionBuilder.setScope("refresh");
			definitionBuilder.addPropertyValue("contextId", contextId);
			BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(definitionBuilder.getBeanDefinition(),
					beanName);
			definitionHolder = ScopedProxyUtils.createScopedProxy(definitionHolder, registry, true);
			BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, registry);
		}
	}

	private boolean isClientRefreshEnabled() {
		return environment.getProperty("feign.client.refresh-enabled", Boolean.class, false);
	}

}
