package com.changgeng.service;


import com.changgeng.client.DamExtClient;
import com.changgeng.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TagService {

    @Autowired
    DamExtClient damExtClient;

    public List<Map> getTagInfos(
            Integer tagId,
            String tagName,
            String srcTagName,
            String name
    ) {
        List<Map> list;
        // 模糊name查询
        if (name !=null && !name.isEmpty()){
            list = damExtClient.getTagInfosByName(name);
        }else {
            list = damExtClient.getTagInfosByTTS(tagId, tagName, srcTagName);
        }
        return list;
    }

}
