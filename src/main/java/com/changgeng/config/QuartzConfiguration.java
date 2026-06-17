package com.changgeng.config;

import com.changgeng.job.SmsTask;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.MethodInvokingJobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

//@Configuration
//public class QuartzConfiguration {
//    //定时任务1
//    @Bean(name = "firstJob")
//    public MethodInvokingJobDetailFactoryBean firstJobDetail(SmsTask smsTask){
//        MethodInvokingJobDetailFactoryBean jobDetail = new MethodInvokingJobDetailFactoryBean();
//        // 是否并发执行
//        jobDetail.setConcurrent(false);
//        // 需要执行的对象
//        jobDetail.setTargetObject(smsTask);
//        //需要执行的方法
//        jobDetail.setTargetMethod("sms");
//        return jobDetail;
//    }
//
//    // 触发器1
//    @Bean(name = "firstTrigger")
//    public CronTriggerFactoryBean firstTrigger(JobDetail firstJob){
//        CronTriggerFactoryBean trigger = new CronTriggerFactoryBean();
//        trigger.setJobDetail(firstJob);
//        trigger.setCronExpression("0 0/1 * * * ? *");
//        return trigger;
//    }
//
//    // 调度工厂
//    @Bean(name = "scheduler")
//    public SchedulerFactoryBean schedulerFactory(Trigger firstTrigger) {
//
//        SchedulerFactoryBean bean = new SchedulerFactoryBean();
//        // 延时启动，应用启动1秒后
//        bean.setStartupDelay(1);
//        //注册触发器
//        bean.setTriggers(firstTrigger);
//
//        return bean;
//    }
//}