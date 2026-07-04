package com.changgeng.common.result;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private Boolean success;
    private String message;
    private T data;

    // 成功时的快捷返回（带数据）
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setSuccess(true);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    // 成功时的快捷返回（不带数据，比如删除、修改成功）
    public static <T> Result<T> success() {
        return success(null);
    }

    // 失败时的快捷返回
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }
}