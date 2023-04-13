package cn.haitaoss.config;

import cn.haitaoss.feign.StoreClient;
import feign.*;
import feign.auth.BasicAuthRequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ConfigBuilder;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.cloud.openfeign.FeignClientFactoryBean;
import org.springframework.cloud.openfeign.FeignContext;
import org.springframework.cloud.openfeign.clientconfig.FeignClientConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-10 09:43
 *
 */
@Component
@Import({StoreClient.StoreClientFallBack.class, StoreClient.StoreClientFallBackFactory.class})
@Slf4j
public class FeignConfig {

    /**
     * 可以对 Feign.Builder 的所有属性进行增强。看 Feign 源码就知道了
     *  {@link Feign.Builder#build()}
     * @return
     */
    @Bean
    public Capability capability() {
        return new Capability() {
            @Override
            public Client enrich(Client client) {
                return new Client() {
                    @Override
                    public Response execute(Request request, Request.Options options) throws IOException {
                        log.info("增强Client.execute...");
                        return client.execute(request, options);
                    }
                };
            }
        };
    }


    /**
     * 断路器工厂
     * @param environment
     * @return
     */
    @Bean
    public CircuitBreakerFactory circuitBreakerFactory(Environment environment) {
        return new CircuitBreakerFactory() {
            @Override
            protected ConfigBuilder configBuilder(String id) {
                return null;
            }

            @Override
            public void configureDefault(Function defaultConfiguration) {

            }

            @Override
            public CircuitBreaker create(String id) {
                return new CircuitBreaker() {
                    @Override
                    public <T> T run(Supplier<T> toRun, Function<Throwable, T> fallback) {
                        log.info("CircuitBreaker执行FeignClient的接口方法");
                        try {
                            if (environment.containsProperty("feign.circuitbreaker.fast-fail")) {
                                if (environment.getProperty("feign.circuitbreaker.fast-fail")
                                        .equals("true")) {
                                    return fallback.apply(new RuntimeException("fast-fail"));
                                }
                            }
                            return toRun.get();
                        } catch (Exception e) {
                            // 出错就使用 fallback 做补偿措施
                            return fallback.apply(e);
                        } finally {
                        }
                    }
                };
            }
        };
    }

    /**
     * 会设置给 Feign.Builder
     * @return
     */
    @Bean
    public RequestInterceptor basicAuthRequestInterceptor() {
        return new BasicAuthRequestInterceptor("user", "password");
    }

    /**
     * 配置 Feign.Builder
     * @return
     */
    @Bean
    public FeignBuilderCustomizer feignBuilderCustomizer() {
        return new FeignBuilderCustomizer() {
            @Override
            public void customize(Feign.Builder builder) {
                log.info("FeignBuilderCustomizer... 可用来对 Feign.Builder 进行最后的配置");
            }
        };
    }

    @Bean
    public FeignClientConfigurer feignClientConfigurer() {
        return new FeignClientConfigurer() {

            @Override
            public boolean inheritParentConfiguration() {
                /**
                 * 为 false 会导致：
                 *      1. 不读取 feign.client.config.contextID.xx 属性来配置 Feign.Builder
                 *      2. 只从 contextId 的容器, 不从父容器中获取 组件 来配置 Feign.Builder
                 *
                 * 源码看这里 {@link FeignClientFactoryBean#configureFeign(FeignContext, Feign.Builder)}
                 * */
                return false;
            }
        };

    }

    /**
     * 不注册到IOC容器也行，可以通过参数配置 feign.client.config.feignClientName.requestInterceptors
     */
    @Slf4j
    public static class MyRequestInterceptor implements RequestInterceptor {
        @Override
        public void apply(RequestTemplate template) {
            log.info("MyRequestInterceptor.apply...可以用来对 RequestTemplate 进行修改");
        }
    }
}
