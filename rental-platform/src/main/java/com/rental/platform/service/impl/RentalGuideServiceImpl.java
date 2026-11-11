package com.rental.platform.service.impl;

import com.rental.platform.entity.GuideComment;
import com.rental.platform.entity.RentalGuide;
import com.rental.platform.exception.BusinessException;
import com.rental.platform.mapper.GuideCommentMapper;
import com.rental.platform.mapper.RentalGuideMapper;
import com.rental.platform.service.RentalGuideService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 租房攻略服务实现 —— 构造器注入 + 事务管理
 */
@Service
public class RentalGuideServiceImpl implements RentalGuideService {

    private static final Logger log = LoggerFactory.getLogger(RentalGuideServiceImpl.class);

    private final RentalGuideMapper rentalGuideMapper;
    private final GuideCommentMapper guideCommentMapper;

    // ✅ 构造器注入
    public RentalGuideServiceImpl(RentalGuideMapper rentalGuideMapper,
                                   GuideCommentMapper guideCommentMapper) {
        this.rentalGuideMapper = rentalGuideMapper;
        this.guideCommentMapper = guideCommentMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RentalGuide> findAll(String category, int offset, int size) {
        return rentalGuideMapper.findAll(category, offset, size);
    }

    @Override
    @Transactional(readOnly = true)
    public int countAll(String category) {
        return rentalGuideMapper.countAll(category);
    }

    @Override
    @Transactional
    public RentalGuide getDetail(Long id) {

        RentalGuide guide = rentalGuideMapper.findById(id);
        if (guide == null) {
            throw BusinessException.notFound("攻略不存在");
        }
        if(!java.util.Set.of("published","APPROVED").contains(guide.getStatus())&&!com.rental.platform.config.UserContextHolder.hasRole("ADMIN")) {
            if(!guide.getAuthorId().equals(com.rental.platform.config.UserContextHolder.getCurrentUserId()))throw BusinessException.notFound("攻略尚未发布");
        }
        rentalGuideMapper.incrementViewCount(id);
        return guide;
    }

    @Override
    @Transactional
    public boolean publish(RentalGuide guide) {
        if (guide.getTitle() == null || guide.getTitle().isBlank()) {
            throw new BusinessException("标题不能为空");
        }
        if (guide.getAuthorId() == null) {
            throw new BusinessException("作者ID不能为空");
        }
        if(guide.getContent()==null||guide.getContent().isBlank())throw new BusinessException("请填写攻略正文");
        guide.setStatus("PENDING");guide.setAuthorId(com.rental.platform.config.UserContextHolder.getCurrentUserId());
        int rows = rentalGuideMapper.insert(guide);
        log.info("攻略发布成功, id={}, title={}", guide.getId(), guide.getTitle());
        return rows > 0;
    }

    @Override
    @Transactional
    public boolean like(Long guideId, Long userId) {
        RentalGuide guide = rentalGuideMapper.findById(guideId);
        if (guide == null) {
            throw BusinessException.notFound("攻略不存在");
        }
        rentalGuideMapper.incrementLikeCount(guideId);
        return true;
    }

    @Override
    @Transactional
    public boolean addComment(Long guideId, Long userId, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("评论内容不能为空");
        }

        GuideComment comment = new GuideComment();
        comment.setGuideId(guideId);
        comment.setUserId(userId);
        comment.setContent(content);

        guideCommentMapper.insert(comment);
        rentalGuideMapper.incrementCommentCount(guideId);
        return true;
    }
}
