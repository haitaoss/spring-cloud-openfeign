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

import java.util.ArrayList;
import java.util.List;

import feign.Contract;
import feign.Feign;
import feign.Logger;
import feign.QueryMapEncoder;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.form.MultipartFormContentProcessor;
import feign.form.spring.SpringFormEncoder;
import feign.micrometer.MicrometerCapability;
import feign.optionals.OptionalDecoder;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.openfeign.clientconfig.FeignClientConfigurer;
import org.springframework.cloud.openfeign.support.AbstractFormWriter;
import org.springframework.cloud.openfeign.support.FeignEncoderProperties;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.cloud.openfeign.support.PageableSpringEncoder;
import org.springframework.cloud.openfeign.support.PageableSpringQueryMapEncoder;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.format.support.FormattingConversionService;

import static feign.form.ContentType.MULTIPART;

/**
 * @author Dave Syer
 * @author Venil Noronha
 * @author Darren Foong
 * @author Jonatan Ivanov
 * @author Olga Maciaszek-Sharma
 * @author Hyeonmin Park
 * @author Yanming Zhou
 */
@Configuration(proxyBeanMethods = false)
public class FeignClientsConfiguration {

	@Autowired
	private ObjectFactory<HttpMessageConverters> messageConverters;

	@Autowired(required = false)
	private List<AnnotatedParameterProcessor> parameterProcessors = new ArrayList<>();

	@Autowired(required = false)
	private List<FeignFormatterRegistrar> feignFormatterRegistrars = new ArrayList<>();

	@Autowired(required = false)
	private Logger logger;

	@Autowired(required = false)
	private SpringDataWebProperties springDataWebProperties;

	@Autowired(required = false)
	private FeignClientProperties feignClientProperties;

	@Autowired(required = false)
	private FeignEncoderProperties encoderProperties;

	/**
	 * 依赖IOC容器配置的 List<HttpMessageConverter> ，其作用是将 执行 FeignClient 接口的响应体 转成 方法的参数类型
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean
	public Decoder feignDecoder(ObjectProvider<HttpMessageConverterCustomizer> customizers) {
		return new OptionalDecoder(new ResponseEntityDecoder(new SpringDecoder(messageConverters, customizers)));
	}

	/**
	 * 依赖IOC容器配置的 List<HttpMessageConverter> ,其作用是将 FeignClient 接口的参数 设置到请求体中
	 * @param formWriterProvider
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnMissingClass("org.springframework.data.domain.Pageable")
	public Encoder feignEncoder(ObjectProvider<AbstractFormWriter> formWriterProvider,
			ObjectProvider<HttpMessageConverterCustomizer> customizers) {
		return springEncoder(formWriterProvider, encoderProperties, customizers);
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.data.domain.Pageable")
	@ConditionalOnMissingBean
	public Encoder feignEncoderPageable(ObjectProvider<AbstractFormWriter> formWriterProvider,
			ObjectProvider<HttpMessageConverterCustomizer> customizers) {
		PageableSpringEncoder encoder = new PageableSpringEncoder(
				springEncoder(formWriterProvider, encoderProperties, customizers));

		if (springDataWebProperties != null) {
			encoder.setPageParameter(springDataWebProperties.getPageable().getPageParameter());
			encoder.setSizeParameter(springDataWebProperties.getPageable().getSizeParameter());
			encoder.setSortParameter(springDataWebProperties.getSort().getSortParameter());
		}
		return encoder;
	}

	@Bean
	@ConditionalOnClass(name = "org.springframework.data.domain.Pageable")
	@ConditionalOnMissingBean
	public QueryMapEncoder feignQueryMapEncoderPageable() {
		PageableSpringQueryMapEncoder queryMapEncoder = new PageableSpringQueryMapEncoder();
		if (springDataWebProperties != null) {
			queryMapEncoder.setPageParameter(springDataWebProperties.getPageable().getPageParameter());
			queryMapEncoder.setSizeParameter(springDataWebProperties.getPageable().getSizeParameter());
			queryMapEncoder.setSortParameter(springDataWebProperties.getSort().getSortParameter());
		}
		return queryMapEncoder;
	}

	/**
	 * 扩展 FeignClient 接口 支持的特殊注解，然后再执行接口方法时 将特殊注解标注的内容 映射到Request对象中，
	 * 比如设置 请求头、请求路径、查询参数、请求体 等等
	 * @param feignConversionService
	 * @return
	 */
	@Bean
	@ConditionalOnMissingBean
	public Contract feignContract(ConversionService feignConversionService) {
		boolean decodeSlash = feignClientProperties == null || feignClientProperties.isDecodeSlash();
		return new SpringMvcContract(parameterProcessors, feignConversionService, decodeSlash);
	}

	@Bean
	public FormattingConversionService feignConversionService() {
		FormattingConversionService conversionService = new DefaultFormattingConversionService();
		for (FeignFormatterRegistrar feignFormatterRegistrar : feignFormatterRegistrars) {
			feignFormatterRegistrar.registerFormatters(conversionService);
		}
		return conversionService;
	}

	@Bean
	@ConditionalOnMissingBean
	public Retryer feignRetryer() {
		return Retryer.NEVER_RETRY;
	}

	@Bean
	@ConditionalOnMissingBean(FeignLoggerFactory.class)
	public FeignLoggerFactory feignLoggerFactory() {
		return new DefaultFeignLoggerFactory(logger);
	}

	@Bean
	@ConditionalOnMissingBean(FeignClientConfigurer.class)
	public FeignClientConfigurer feignClientConfigurer() {
		return new FeignClientConfigurer() {
		};
	}

	private Encoder springEncoder(ObjectProvider<AbstractFormWriter> formWriterProvider,
			FeignEncoderProperties encoderProperties, ObjectProvider<HttpMessageConverterCustomizer> customizers) {
		AbstractFormWriter formWriter = formWriterProvider.getIfAvailable();

		if (formWriter != null) {
			return new SpringEncoder(new SpringPojoFormEncoder(formWriter), messageConverters, encoderProperties,
					customizers);
		}
		else {
			return new SpringEncoder(new SpringFormEncoder(), messageConverters, encoderProperties, customizers);
		}
	}

	private class SpringPojoFormEncoder extends SpringFormEncoder {

		SpringPojoFormEncoder(AbstractFormWriter formWriter) {
			super();

			MultipartFormContentProcessor processor = (MultipartFormContentProcessor) getContentProcessor(MULTIPART);
			processor.addFirstWriter(formWriter);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Conditional(FeignCircuitBreakerDisabledConditions.class)
	protected static class DefaultFeignBuilderConfiguration {

		/**
		 * 设置成 prototype 的，因为每一个 @FeignClient 再实例化时，都会获取到 Feign.Builder 进行配置
		 *
		 * @param retryer
		 * @return
		 */
		@Bean
		@Scope("prototype")
		@ConditionalOnMissingBean
		public Feign.Builder feignBuilder(Retryer retryer) {
			return Feign.builder().retryer(retryer);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass(CircuitBreaker.class)
	@ConditionalOnProperty("feign.circuitbreaker.enabled")
	protected static class CircuitBreakerPresentFeignBuilderConfiguration {

		@Bean
		@Scope("prototype")
		@ConditionalOnMissingBean({ Feign.Builder.class, CircuitBreakerFactory.class })
		public Feign.Builder defaultFeignBuilder(Retryer retryer) {
			return Feign.builder().retryer(retryer);
		}

		/**
		 * 生成 FeignClient 的 Builder 对象，FeignCircuitBreaker OpenFeign 定义的，用来使用 FeignCircuitBreaker 来执行 FeignClient 接口的方法
		 * @return
		 */
		@Bean
		@Scope("prototype")
		@ConditionalOnMissingBean
		@ConditionalOnBean(CircuitBreakerFactory.class)
		public Feign.Builder circuitBreakerFeignBuilder() {
			return FeignCircuitBreaker.builder();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
	@ConditionalOnClass(name = "feign.micrometer.MicrometerCapability")
	@ConditionalOnProperty(name = "feign.metrics.enabled", matchIfMissing = true)
	@Conditional(FeignClientMetricsEnabledCondition.class)
	protected static class MetricsConfiguration {

		@Bean
		@ConditionalOnMissingBean
		public MicrometerCapability micrometerCapability(MeterRegistry meterRegistry) {
			return new MicrometerCapability(meterRegistry);
		}

	}

}
