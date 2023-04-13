package cn.haitaoss.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-09 20:58
 */
@FeignClient(name = "store",
        url = "${FeignClient.StoreClient.url:}",
        path = "${FeignClient.StoreClient.path:}",
        fallback = StoreClient.StoreClientFallBack.class,
        fallbackFactory = StoreClient.StoreClientFallBackFactory.class)
public interface StoreClient {

    @PostMapping("name")
    @Cacheable(cacheNames = "demo-cache",key = "#data.size()")
        // String name(Map<String, String> data); // 啥注解都没写，就会将内容写到请求体中
        //    String name(@RequestBody Map<String, String> data);
    String name(@MatrixVariable Map<String, String> data);


    @Slf4j
    class StoreClientFallBack implements StoreClient {
        @Override
        public String name(Map<String, String> data) {
            log.info("StoreClientFallBack...name");
            return "fallback";
        }
    }

    @Slf4j
    class StoreClientFallBackFactory implements FallbackFactory<StoreClient> {

        @Override
        public StoreClient create(Throwable cause) {
            log.info("异常信息是 {}", cause.getMessage());
            return new StoreClientFallBack();
        }
    }
}
