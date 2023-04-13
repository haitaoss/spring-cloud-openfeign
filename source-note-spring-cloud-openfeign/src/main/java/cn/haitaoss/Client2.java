package cn.haitaoss;

import cn.haitaoss.base.Base;
import cn.haitaoss.base.BaseApp;
import org.springframework.boot.SpringApplication;

/**
 * @author haitao.chen
 * email haitaoss@aliyun.com
 * date 2023-04-03 09:44
 *
 */
@Base
public class Client2 extends BaseApp {
    public static void main(String[] args) {
        System.setProperty("server.port", "8082");
        System.setProperty("spring.application.name", "Client2");
        SpringApplication.run(Client2.class, args);
    }
}