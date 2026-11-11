package com.rental.platform.service;

import com.rental.platform.entity.RentalGuide;
import java.util.List;

/**
 * 租房攻略服务接口
 */
public interface RentalGuideService {

    /** 分页列表 + 总数 */
    List<RentalGuide> findAll(String category, int offset, int size);

    /** 按分类统计总数 */
    int countAll(String category);

    /** 攻略详情（含浏览量+1） */
    RentalGuide getDetail(Long id);

    /** 发布攻略 */
    boolean publish(RentalGuide guide);

    /** 点赞（含去重） */
    boolean like(Long guideId, Long userId);

    /** 发表评论（含评论数+1） */
    boolean addComment(Long guideId, Long userId, String content);
}
