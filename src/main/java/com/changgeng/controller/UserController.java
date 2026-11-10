package com.changgeng.controller;

import com.alibaba.fastjson.JSON;
import com.changgeng.common.result.Result;
import com.changgeng.pojo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/ai/user")
@Slf4j
public class UserController {
    /**
     * 获取登录的用户信息
     * @return
     */
    @RequestMapping("/get")
    public Result getUser(){
        UserVO userVO = new UserVO("1","test",randomSelectRole());
        log.error(JSON.toJSONString(userVO));
        return Result.success(userVO);
    }

    private static final String[] ROLES = {
            "设备维护部_主任",
            "设备维护部_副主任",
            "设备维护部_其他",
            "主控室_主任",
            "主控室_副主任",
            "主控室_总值长",
            "主控室_值长",
            "主控室_班长",
            "主控室_其他"
    };

    public static String randomSelectRole() {
        int index = ThreadLocalRandom.current().nextInt(ROLES.length);
        return ROLES[index];
    }
}
