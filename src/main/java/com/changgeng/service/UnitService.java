package com.changgeng.service;

import com.changgeng.client.DamExtClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class UnitService {

    @Autowired
    DamExtClient damExtClient;


    public List<Map> getItems(Integer unitId, String type) {
        List<Map> list = damExtClient.getItems(unitId, type);
        return list;
    }

}
