package com.yunshu.zhixue.common.response;

import java.util.List;

public record PageResult<T>(List<T> list, long total, long page, long pageSize) {
}
