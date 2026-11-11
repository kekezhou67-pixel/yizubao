package com.rental.platform.service.impl;

import com.rental.platform.dto.HouseConverter;
import com.rental.platform.dto.HouseVO;
import com.rental.platform.dto.PageResult;
import com.rental.platform.entity.House;
import com.rental.platform.exception.BusinessException;
import com.rental.platform.mapper.HouseMapper;
import com.rental.platform.service.HouseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 房源服务实现 —— 构造器注入 + 事务管理 + 业务校验
 */
@Service
public class HouseServiceImpl implements HouseService {

    private static final Logger log = LoggerFactory.getLogger(HouseServiceImpl.class);

    private final HouseMapper houseMapper;
    private final com.rental.platform.service.HousingTrustService trust;

    // ✅ 构造器注入（推荐方式，便于测试和明确依赖）
    public HouseServiceImpl(HouseMapper houseMapper, com.rental.platform.service.HousingTrustService trust) {
        this.houseMapper = houseMapper;
        this.trust=trust;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<HouseVO> findAll(Map<String, Object> params, int page, int size) {
        params=new java.util.HashMap<>(params);
        if(!com.rental.platform.config.UserContextHolder.hasRole("ADMIN")) {
            boolean own=false;
            try {own=Long.toString(com.rental.platform.config.UserContextHolder.getCurrentUserId()).equals(java.util.Objects.toString(params.get("landlordId"),""));}catch(BusinessException ignored){}
            if(!own)params.put("status","APPROVED");
        }
        // 参数防御
        if (page < 1) page = 1;
        if (size < 1 || size > 5000) size = 12;

        params.put("offset", (page - 1) * size);
        params.put("limit", size);

        List<House> houses = houseMapper.findAll(params);
        long total = houseMapper.count(params);

        List<HouseVO> voList = houses.stream()
                .map(HouseConverter::toVO)
                .collect(Collectors.toList());

        return new PageResult<>(voList, total, page, size);
    }

    @Override
    @Transactional
    public HouseVO getDetail(Long id) {
        House house = houseMapper.findById(id);
        if (house == null) {
            throw BusinessException.notFound("房源不存在");
        }
        if(!"APPROVED".equals(house.getStatus())&&!com.rental.platform.config.UserContextHolder.hasRole("ADMIN")) {
            Long uid=null;try{uid=com.rental.platform.config.UserContextHolder.getCurrentUserId();}catch(BusinessException ignored){}
            if(!java.util.Objects.equals(uid,house.getLandlordId()))throw BusinessException.notFound("房源已下架或尚未通过审核");
        }
        houseMapper.incrementViewCount(id);
        return HouseConverter.toVO(house);
    }

    @Override
    @Transactional(readOnly = true)
    public House findById(Long id) {
        return houseMapper.findById(id);
    }

    @Override
    @Transactional
    public House publish(House house) {
        Long uid=com.rental.platform.config.UserContextHolder.getCurrentUserId();
        if(!com.rental.platform.config.UserContextHolder.hasRole("LANDLORD")&&!com.rental.platform.config.UserContextHolder.hasRole("ADMIN"))throw BusinessException.forbidden("请使用房东账号发布房源");
        house.setLandlordId(uid);
        // 业务校验
        if (house.getLandlordId() == null) {
            throw new BusinessException("房东ID不能为空");
        }
        if (house.getTitle() == null || house.getTitle().isBlank()) {
            throw new BusinessException("标题不能为空");
        }
        if (house.getCity() == null || house.getCity().isBlank()) {
            throw new BusinessException("城市不能为空");
        }
        if (house.getPrice() == null || house.getPrice().signum() <= 0) {
            throw new BusinessException("价格必须大于0");
        }

        // 默认值
        house.setStatus("PENDING");
        house.setViewCount(0);

        int rows = houseMapper.insert(house);
        if (rows <= 0) {
            throw new BusinessException("发布失败，请稍后重试");
        }

        log.info("房源发布成功, id={}, landlordId={}", house.getId(), house.getLandlordId());
        return house;
    }

    @Override
    @Transactional
    public boolean update(House house) {
        if (house.getId() == null) {
            throw new BusinessException("房源ID不能为空");
        }

        House existing = houseMapper.findById(house.getId());
        if (existing == null) {
            throw BusinessException.notFound("房源不存在");
        }

        if(!existing.getLandlordId().equals(com.rental.platform.config.UserContextHolder.getCurrentUserId()))throw BusinessException.forbidden("只能编辑本人房源");
        if(house.getTitle()==null||house.getTitle().isBlank()||house.getCity()==null||house.getCity().isBlank()||house.getPrice()==null||house.getPrice().signum()<=0||house.getArea()==null||house.getArea().signum()<=0)throw new BusinessException("请完整填写房源信息，租金和面积必须大于0");
        house.setLandlordId(existing.getLandlordId());house.setStatus("PENDING");
        int rows = houseMapper.update(house);
        trust.invalidate(house.getId());
        log.info("房源更新成功, id={}", house.getId());
        return rows > 0;
    }

    @Override
    @Transactional
    public boolean delete(Long id, Long operatorId, String operatorRole) {
        House existing = houseMapper.findById(id);
        if (existing == null) {
            throw BusinessException.notFound("房源不存在");
        }

        // 权限检查：只有房东本人或管理员可以删除
        boolean isOwner = existing.getLandlordId().equals(operatorId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(operatorRole);
        if (!isOwner && !isAdmin) {
            throw BusinessException.forbidden("无权删除该房源");
        }

        int rows = houseMapper.updateStatus(id,"OFFLINE");
        log.info("房源删除成功, id={}, operatorId={}", id, operatorId);
        return rows > 0;
    }

    @Override
    @Transactional
    public boolean changeStatus(Long id, String status, String operatorRole) {
        if (!"ADMIN".equalsIgnoreCase(operatorRole)) {
            throw BusinessException.forbidden("仅管理员可修改房源状态");
        }
        if (status == null || status.isBlank()) {
            throw new BusinessException("状态不能为空");
        }

        House existing = houseMapper.findById(id);
        if (existing == null) {
            throw BusinessException.notFound("房源不存在");
        }

        throw new BusinessException("请从审核工作台核对材料并填写处理原因");
    }

    @Override
    @Transactional(readOnly = true)
    public List<HouseVO> findBySubwayStation(Long stationId, Integer maxDistance) {
        List<House> houses = houseMapper.findBySubwayStation(stationId, maxDistance);
        return houses.stream()
                .filter(h -> "APPROVED".equals(h.getStatus()))
                .map(HouseConverter::toVO)
                .collect(Collectors.toList());
    }
}
