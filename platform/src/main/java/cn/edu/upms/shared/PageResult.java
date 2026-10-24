package cn.edu.upms.shared;

import java.util.List;

/**
 * 分页查询的传输结果，避免一次返回全部员工或日志.
 *
 * @param <T> 当前页条目类型，使用公开事实或响应 DTO，不暴露持久化实体
 * @param items 当前页的条目集合
 * @param total 相同筛选和管理范围下的总条目数
 * @param page 请求的页号，从 0 开始
 * @param size 请求的页大小；实际数据库查询另在 Service 内限制最大值
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {}
