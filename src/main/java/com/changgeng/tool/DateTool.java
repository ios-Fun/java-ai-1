package com.changgeng.tool;

import com.changgeng.pojo.DeviceRequest;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

@Slf4j
public class DateTool {
    public static Date[] getStartAndEndTime(DeviceRequest deviceRequest) {
        Date[] dates = new Date[2];

        Date endDate = new Date();
        if (deviceRequest.getStartTime() == null && deviceRequest.getUnit() == null) {

            // 往前一周
            Date startDate = minusDays(endDate, 7);
            dates[0] = startDate;
            dates[1] = endDate;
            // return dates;
        }else {
            String num = deviceRequest.getNum();
            String unit = deviceRequest.getUnit();
            Date startDate = null;
            if (num != null && unit != null) {
                if (unit.equals("week")) {
                    startDate = minusWeeks(endDate, Integer.parseInt(num));
                }else if (unit.equals("day")) {
                    startDate = minusDays(endDate, Integer.parseInt(num));
                }else if (unit.equals("month")) {
                    startDate = minusMonths(endDate, Integer.parseInt(num));
                }else if (unit.equals("year")) {
                    startDate = minusYears(endDate, Integer.parseInt(num));
                }else {
                    log.info("unsupported unit: {}", unit);
                    startDate = minusDays(endDate, 7);
                }
                dates[0] = startDate;
                dates[1] = endDate;
            }else {
                startDate = convertStringToDate(deviceRequest.getStartTime());
                endDate = convertStringToDate(deviceRequest.getEndTime());
                dates[0] = startDate;
                dates[1] = endDate;
            }
        }
        return dates;
    }

    public static Date convertStringToDate(String timeStr) {
        // 这种格式自带 +08:00 偏移，直接用 OffsetDateTime 解析
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(timeStr);
        // 转成 Date
        return Date.from(offsetDateTime.toInstant());
    }

    public static Date minusDays(Date date, int days) {
        if (date == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        // 往前推：add 负数
        calendar.add(Calendar.DATE, -days);
        return calendar.getTime();
    }

    // ======================== 2. 往前 N 周 ========================
    public static Date minusWeeks(Date date, int weeks) {
        if (date == null) return null;
        LocalDateTime ldt = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return Date.from(ldt.minusWeeks(weeks).atZone(ZoneId.systemDefault()).toInstant());
    }

    // ======================== 3. 往前 N 月 ========================
    public static Date minusMonths(Date date, int months) {
        if (date == null) return null;
        LocalDateTime ldt = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return Date.from(ldt.minusMonths(months).atZone(ZoneId.systemDefault()).toInstant());
    }

    // ======================== 4. 往前 N 年 ========================
    public static Date minusYears(Date date, int years) {
        if (date == null) return null;
        LocalDateTime ldt = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return Date.from(ldt.minusYears(years).atZone(ZoneId.systemDefault()).toInstant());
    }
}
