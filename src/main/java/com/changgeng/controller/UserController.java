package com.changgeng.controller;

import com.changgeng.common.result.Result;
import com.changgeng.pojo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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
        UserVO userVO = new UserVO("1","test","主控室_其他");
        return Result.success(userVO);
    }
}
