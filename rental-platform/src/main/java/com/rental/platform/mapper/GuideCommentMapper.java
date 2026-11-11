package com.rental.platform.mapper;

import com.rental.platform.entity.GuideComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GuideCommentMapper {

    /**
     * 查询攻略评论列表（含用户信息）
     */
    List<GuideComment> findByGuideId(@Param("guideId") Long guideId);

    /**
     * 发表评论
     */
    int insert(GuideComment comment);
}

