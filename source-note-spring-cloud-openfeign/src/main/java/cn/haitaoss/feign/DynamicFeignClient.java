package cn.haitaoss.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.FeignClientBuilder;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-12 10:04
 *
 */
@RequestMapping("${server.servlet.context-path:}/{path}")
public interface DynamicFeignClient {
    @PostMapping
    Object exePost(URI uri, @PathVariable("path") String path, Object param);

    @GetMapping
    Object exeGet(URI uri, @PathVariable("path") String path, @SpringQueryMap Object param);

    default Object exePost(String serverName, @PathVariable("path") String path, Object param) {
        return exePost(getUrl(serverName), path, param);
    }

    default Object exeGet(String serverName, @PathVariable("path") String path, Object param) {
        return exeGet(getUrl(serverName), path, param);
    }

    default URI getUrl(String url) {
        if (StringUtils.hasText(url)) {
            if (!url.contains("://")) {
                url = "http://" + url;
            }
        }
        return URI.create(url);
    }

    public static void main(String[] args) {
        /**
         * TODOHAITAO: 2023/4/12 项目依赖 spring-cloud-starter-openfeign、spring-cloud-starter-loadbalancer
         * */
        System.setProperty("spring.cloud.discovery.client.simple.instances.order[0].uri","http://localhost:8080");
        System.setProperty("spring.cloud.discovery.client.simple.instances.user[0].uri","http://localhost:8080");
        ConfigurableApplicationContext context = SpringApplication.run(Config.class, args);

        DynamicFeignClient dynamicFeignClient = new FeignClientBuilder(context)
                .forType(DynamicFeignClient.class, "undefined")
                .build();

        Object getResult = dynamicFeignClient.exeGet("order", "name", "");
        System.out.println("getResult = " + getResult);

        Object postResult = dynamicFeignClient.exePost("user", "name", new HashMap<>());
        System.out.println("postResult = " + postResult);
    }

    @SpringBootApplication
    @RestController
    @Slf4j
    static class Config {
        @RequestMapping("name")
        public Object name(@RequestBody(required = false) Object data) {
            log.info("请求参数是 {}", data);
            HashMap<Object, Object> ret = new HashMap<>();
            ret.put("name", "haitao");
            return ret;
        }
    }
}
