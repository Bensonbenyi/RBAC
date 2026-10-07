package cn.edu.upms.shared;

import java.util.List;

/** 有界分页结果，避免将一万名员工一次传给浏览器. */
public record PageResult<T>(List<T> items, long total, int page, int size) {}
