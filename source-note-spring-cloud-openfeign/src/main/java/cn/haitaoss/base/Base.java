package cn.haitaoss.base;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@EnableFeignClients(basePackages = "cn.haitaoss.feign")
@EnableAutoConfiguration
@RestController
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Base {}