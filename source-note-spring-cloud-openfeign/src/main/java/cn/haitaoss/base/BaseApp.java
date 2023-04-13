package cn.haitaoss.base;

import cn.haitaoss.feign.StoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;

@Slf4j
public class BaseApp implements InitializingBean {
    @Autowired
    private StoreClient storeClient;

    @Autowired
    @Lazy
    private HttpServletRequest request;

    @Value("${spring.application.name}")
    private String name;

    private HashMap<String, String> data = new HashMap<>();

    @RequestMapping("/call")
    public Object call() {
        print_header_info();
        return storeClient.name(data);
    }


    @RequestMapping("name")
    public Object name(@RequestBody(required = false) Object data) {
        log.info("data is {}", data);
        print_header_info();
        return this.data;
    }

    private void print_header_info() {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            log.info("请求头信息, name is {}, value is {}", name, request.getHeader(name));
        }
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        data.put("name", name);
        data.put("key", "value");
    }
}
