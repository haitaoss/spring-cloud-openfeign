package cn.haitaoss;

import cn.haitaoss.base.Base;
import cn.haitaoss.base.BaseApp;
import cn.haitaoss.config.FeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Import;

@Base
@Import(FeignConfig.class)
public class Main extends BaseApp {

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}