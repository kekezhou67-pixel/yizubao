package com.rental.platform.controller;

import com.rental.platform.config.UserContextHolder;
import com.rental.platform.dto.HouseConverter;
import com.rental.platform.dto.HousePublishDTO;
import com.rental.platform.dto.HouseVO;
import com.rental.platform.dto.PageResult;
import com.rental.platform.entity.House;
import com.rental.platform.exception.BusinessException;
import com.rental.platform.service.HouseService;
import com.rental.platform.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * 房源 Controller —— 构造器注入 + 参数校验 + VO 返回 + 分页
 */
@RestController
@RequestMapping("/api/house")
public class HouseController {

    private static final Logger log = LoggerFactory.getLogger(HouseController.class);

    private final HouseService houseService;

    // ✅ 构造器注入
    public HouseController(HouseService houseService) {
        this.houseService = houseService;
    }

    /**
     * 房源列表（分页 + 筛选 + 排序）
     */
    @GetMapping("/list")
    public Result<PageResult<HouseVO>> list(
            @RequestParam Map<String, Object> params,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        PageResult<HouseVO> result = houseService.findAll(params, page, size);
        return Result.success(result);
    }

    /**
     * 房源详情（含浏览数+1）
     */
    @GetMapping("/detail/{id}")
    public Result<HouseVO> detail(@PathVariable Long id) {
        HouseVO vo = houseService.getDetail(id);
        return Result.success(vo);
    }

    /**
     * 发布房源（DTO 校验 + 自动绑定当前用户）
     */
    @PostMapping("/publish")
    public Result<HouseVO> publish(@Valid @RequestBody HousePublishDTO dto) {
        Long userId = UserContextHolder.getCurrentUserId();

        House house = HouseConverter.toEntity(dto, userId);
        House saved = houseService.publish(house);

        return Result.success("发布成功", HouseConverter.toVO(saved));
    }

    /**
     * 更新房源
     */
    @PutMapping("/update")
    public Result<String> update(@RequestBody House house) {
        if (house.getId() == null) {
            return Result.error("房源ID不能为空");
        }
        boolean success = houseService.update(house);
        return success ? Result.success("更新成功", null) : Result.error("更新失败");
    }

    /**
     * 删除房源（仅房东本人或管理员）
     */
    @DeleteMapping("/delete/{id}")
    public Result<String> delete(@PathVariable Long id) {
        Long userId = UserContextHolder.getCurrentUserId();
        String role = UserContextHolder.getCurrentUserRole();

        boolean success = houseService.delete(id, userId, role);
        return success ? Result.success("删除成功", null) : Result.error("删除失败");
    }

    /**
     * 修改房源状态（仅管理员）
     */
    @PutMapping("/status")
    public Result<String> changeStatus(@RequestBody Map<String, Object> params) {
        Object idObj = params.get("id");
        Object statusObj = params.get("status");
        if (idObj == null || statusObj == null) {
            return Result.error("id 和 status 不能为空");
        }
        Long id = Long.valueOf(idObj.toString());
        String status = (String) statusObj;
        String role = UserContextHolder.getCurrentUserRole();

        boolean success = houseService.changeStatus(id, status, role);
        return success ? Result.success("状态更新成功", null) : Result.error("状态更新失败");
    }
}
