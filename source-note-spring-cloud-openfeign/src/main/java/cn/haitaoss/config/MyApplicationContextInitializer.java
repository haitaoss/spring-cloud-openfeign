package cn.haitaoss.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.converter.Converter;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-10 16:17
 *
 */
@Slf4j
public class MyApplicationContextInitializer implements ApplicationContextInitializer {
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();
        if (!beanFactory.hasEmbeddedValueResolver()) {
            beanFactory.addEmbeddedValueResolver(strVal -> applicationContext.getEnvironment()
                    .resolvePlaceholders(strVal));
        }

        applicationContext.getEnvironment()
                .getConversionService()
                .addConverter(new Converter<String, Class>() {
                    @Override
                    public Class convert(String source) {
                        try {
                            return Class.forName(source);
                        } catch (Exception e) {
                            log.warn("String converter to Class Fail, error msg is {}", e.getMessage());
                        }
                        return null;
                    }
                });
    }
}
