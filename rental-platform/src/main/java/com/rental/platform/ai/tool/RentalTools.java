package com.rental.platform.ai.tool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.rental.platform.config.UserContextHolder;
import com.rental.platform.dto.GeneratedFileInfo;
import com.rental.platform.dto.HouseVO;
import com.rental.platform.dto.PageResult;
import com.rental.platform.entity.House;
import com.rental.platform.entity.RentalGuide;
import com.rental.platform.entity.SubwayStation;
import com.rental.platform.mapper.SubwayStationMapper;
import com.rental.platform.service.AliSmsService;
import com.rental.platform.service.ContractReviewService;
import com.rental.platform.service.DocumentGenerationService;
import com.rental.platform.service.HouseFavoriteService;
import com.rental.platform.service.HouseService;
import com.rental.platform.service.RentalGuideService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租房业务工具集（AI 改造 · 阶段3）
 * 依据：讲义03 第1-2章 Function Call/@Tool + 工程方案步骤3
 *
 * 迁移自 AIAgentServiceImpl 的 13 个手写 switch 操作 ——
 * 意图识别不再靠"手写 JSON 截取 + switch 分发"，而是交给 Spring AI Agent Loop：
 * 模型根据每个 @Tool 的 description 自主决定调哪个工具、调几次、按什么顺序调。
 *
 * 讲义03 强调的两个关键点：
 * 1. description 写清"什么时候用 + 依赖顺序"（如：收藏前必须先查详情确认房源存在）
 * 2. 返回值信息完整（"房源1001：已收藏"而不是"OK"）—— 返回值是模型的下一步上下文
 *
 * 写操作安全（工程方案决策5）：
 * publishHouse / sendSms 均为两步式 —— 先返回预览让用户确认，
 * 用户明确同意后模型才调 confirmPublishHouse / confirmSendSms 真正执行。
 */
@Component
public class RentalTools {

    private static final Logger log = LoggerFactory.getLogger(RentalTools.class);

    private final com.rental.platform.ai.RagService knowledge;
    private final HouseService houseService;
    private final com.rental.platform.service.PrivateMessageService privateMessages;
    private final HouseFavoriteService houseFavoriteService;
    private final SubwayStationMapper subwayStationMapper;
    private final RentalGuideService rentalGuideService;
    private final AliSmsService aliSmsService;
    private final ContractReviewService contractReviewService;
    private final DocumentGenerationService documentGenerationService;
    private final ChatClient chatClient;   // 基础 ChatClient（无工具），用于文档内容生成，避免递归调工具

    public RentalTools(HouseService houseService,
                       com.rental.platform.ai.RagService knowledge,
                       com.rental.platform.service.PrivateMessageService privateMessages,
                       HouseFavoriteService houseFavoriteService,
                       SubwayStationMapper subwayStationMapper,
                       RentalGuideService rentalGuideService,
                       AliSmsService aliSmsService,
                       ContractReviewService contractReviewService,
                       DocumentGenerationService documentGenerationService,
                       @Lazy ChatClient chatClient) {   // @Lazy 打破循环：chatClient→toolResolver→mcpTools→rentalTools→chatClient
        this.houseService = houseService;
        this.knowledge = knowledge;
        this.privateMessages = privateMessages;
        this.houseFavoriteService = houseFavoriteService;
        this.subwayStationMapper = subwayStationMapper;
        this.rentalGuideService = rentalGuideService;
        this.aliSmsService = aliSmsService;
        this.contractReviewService = contractReviewService;
        this.documentGenerationService = documentGenerationService;
        this.chatClient = chatClient;
    }

    @Tool(description = "联系房东、私信、约看房时优先使用。根据房源ID给其发布者准备站内私人消息草稿，不发送。必须明确具体房源和用户要说的内容；不添加未授权的联系方式或承诺。返回收件人、房源和全文，页面将显示确认发送按钮。用户必须亲自点击按钮发送，你不能声称已经发送，不可改用短信绕过确认。")
    public String preparePrivateMessage(
        @ToolParam(description = "确定的房源ID，不是搜索序号") Long houseId,
        @ToolParam(description = "按用户要求代拟的完整私信正文，最多2000字") String content) {
        try {return toJson(privateMessages.prepare(houseId,UserContextHolder.getCurrentUserId(),content));}
        catch(Exception e){return toJson(Map.of("error",e.getMessage()));}
    }

    @Tool(description = "查询租房知识库并返回带引用来源的答案。用户问押金、维修、法律政策、防坑、租赁权益或需要依据时必须优先使用，不要仅凭常识编造法条。资料不足应明确说明。")
    public String searchRentalKnowledge(@ToolParam(description="用户完整的租房政策或权益问题") String question) {
        try {
            var result=knowledge.answer(question);
            return toJson(Map.of("answer",result.answer(),"sources",result.sources().stream().map(source->Map.of("text",source.text(),"score",source.score(),"docTitle",source.docTitle(),"docId",source.docId()==null?0L:source.docId())).toList()));
        } catch(Exception e){return toJson(Map.of("error","资料检索暂不可用，请稍后重试"));}
    }

    // ==================== 查询类工具（READ） ====================

    @Tool(description = "按条件搜索租房房源。用户想找房、搜房源、看某个城市/区域/价位的房子时调用。返回房源ID、标题、价格、户型、位置等信息。")
    public String searchHouses(
            @ToolParam(description = "城市名，如：杭州。用户没提就传 null", required = false) String city,
            @ToolParam(description = "区域名，如：西湖区。用户没提就传 null", required = false) String district,
            @ToolParam(description = "最低月租金（元）。用户没提就传 null", required = false) Integer minPrice,
            @ToolParam(description = "最高月租金（元）。'3000以下'就是3000。用户没提就传 null", required = false) Integer maxPrice,
            @ToolParam(description = "卧室数，如 2 表示两室。用户没提就传 null", required = false) Integer bedrooms,
            @ToolParam(description = "关键词（小区名/地铁线/特色），用户没提就传 null", required = false) String keyword) {

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("status", "APPROVED");
        if (city != null && !city.isBlank()) queryParams.put("city", city.trim());
        if (district != null && !district.isBlank()) queryParams.put("district", district.trim());
        if (minPrice != null) queryParams.put("minPrice", BigDecimal.valueOf(minPrice));
        if (maxPrice != null) queryParams.put("maxPrice", BigDecimal.valueOf(maxPrice));
        if (bedrooms != null) queryParams.put("bedrooms", bedrooms);
        if (keyword != null && !keyword.isBlank()) queryParams.put("keyword", keyword.trim());

        PageResult<HouseVO> page = houseService.findAll(queryParams, 1, 8);

        // 瘦身：只给模型关键字段，控制 Token 消耗
        // ★ rank 序号字段：用户说"第N个"时，模型据此映射到正确的房源 id（rank≠id！）
        List<Map<String, Object>> slim = new ArrayList<>();
        int rank = 1;
        for (HouseVO h : page.getList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", rank++);
            m.put("id", h.getId());
            m.put("title", h.getTitle());
            m.put("coverImage", h.getCoverImage());
            m.put("livingRooms", h.getLivingRooms());
            m.put("orientation", h.getOrientation());
            m.put("city", h.getCity());
            m.put("district", h.getDistrict());
            m.put("price", h.getPrice());
            m.put("area", h.getArea());
            m.put("bedrooms", h.getBedrooms());
            m.put("decoration", h.getDecoration());
            m.put("subway", h.getSubwayLineName() != null ? h.getSubwayLineName() + " " + h.getSubwayStationName() : null);
            slim.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotal());
        result.put("returned", slim.size());
        result.put("houses", slim);
        if (page.getTotal() == 0) {
            result.put("hint", "没有符合条件的房源，建议放宽价格或区域条件");
        }
        return toJson(result);
    }

    @Tool(description = "查询单个房源的完整详情（含地址、户型、设施、房东、联系方式）。收藏房源前必须先调用本工具确认房源存在。用户问'第N个房子'时，用之前搜索结果里的 id 调用。")
    public String getHouseDetail(
            @ToolParam(description = "房源ID（来自 searchHouses 结果里的 id 字段）") Long houseId) {
        try {
            HouseVO h = houseService.getDetail(houseId);
            if (h == null) {
                return toJson(Map.of("error", "房源 " + houseId + " 不存在"));
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.getId());
            m.put("title", h.getTitle());
            m.put("description", h.getDescription());
            m.put("coverImage", h.getCoverImage());
            m.put("city", h.getCity());
            m.put("district", h.getDistrict());
            m.put("address", h.getAddress());
            m.put("price", h.getPrice());
            m.put("area", h.getArea());
            m.put("bedrooms", h.getBedrooms());
            m.put("livingRooms", h.getLivingRooms());
            m.put("bathrooms", h.getBathrooms());
            m.put("floorInfo", h.getFloorInfo());
            m.put("orientation", h.getOrientation());
            m.put("decoration", h.getDecoration());
            m.put("facilities", h.getFacilities());
            m.put("subway", h.getSubwayLineName() + " " + h.getSubwayStationName() + " 步行" + h.getDistanceToSubway() + "米");
            m.put("landlord", h.getLandlordName());
            return toJson(m);
        } catch (Exception e) {
            log.error("查询房源详情失败 houseId={}: {}", houseId, e.getMessage());
            return toJson(Map.of("error", "查询失败: " + e.getMessage()));
        }
    }

    @Tool(description = "收藏房源到当前登录用户的收藏夹。必须先调用 getHouseDetail 确认房源存在，再调用本工具。")
    public String favoriteHouse(
            @ToolParam(description = "房源ID") Long houseId) {
        try {
            Long userId = UserContextHolder.getCurrentUserId();
            boolean ok = houseFavoriteService.add(userId, houseId);
            HouseVO h = houseService.getDetail(houseId);
            String title = h != null ? h.getTitle() : String.valueOf(houseId);
            return ok
                    ? toJson(Map.of("success", true, "message", "房源「" + title + "」(ID:" + houseId + ") 已收藏"))
                    : toJson(Map.of("success", false, "message", "房源「" + title + "」之前已经收藏过了"));
        } catch (Exception e) {
            return toJson(Map.of("error", "收藏失败: " + e.getMessage()));
        }
    }

    @Tool(description = "查询地铁信息。不传城市→返回所有有地铁数据的城市；传城市→返回该城市所有线路；传城市+线路→返回该线路所有站点。用户问'某地离哪个地铁站近'、'某城市地铁沿线'时调用。")
    public String searchSubway(
            @ToolParam(description = "城市名，如：杭州。查城市列表时传 null", required = false) String city,
            @ToolParam(description = "线路名，如：1号线。查城市全部线路时传 null", required = false) String line) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (city == null || city.isBlank()) {
            result.put("cities", subwayStationMapper.findCities());
            return toJson(result);
        }
        if (line == null || line.isBlank()) {
            result.put("city", city);
            result.put("lines", subwayStationMapper.findLinesByCity(city.trim()));
            return toJson(result);
        }
        List<SubwayStation> stations = subwayStationMapper.findByCityAndLine(city.trim(), line.trim());
        result.put("city", city);
        result.put("line", line);
        result.put("stationCount", stations.size());
        result.put("stations", stations.stream().map(SubwayStation::getName).toList());
        return toJson(result);
    }

    @Tool(description = "查询租房攻略文章（签约流程、合同知识、防坑技巧、租房准备等）。用户想要攻略、经验、指南类内容时调用。")
    public String getGuides(
            @ToolParam(description = "分类过滤，可选。传 null 查全部", required = false) String category,
            @ToolParam(description = "内容关键词，如：签约、押金、合同。传 null 不过滤", required = false) String keyword) {
        List<RentalGuide> guides = rentalGuideService.findAll(
                category != null && !category.isBlank() ? category.trim() : "", 0, 20);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.toLowerCase();
            guides = guides.stream()
                    .filter(g -> (g.getTitle() != null && g.getTitle().toLowerCase().contains(kw))
                            || (g.getContent() != null && g.getContent().toLowerCase().contains(kw)))
                    .toList();
        }
        List<Map<String, Object>> slim = new ArrayList<>();
        for (RentalGuide g : guides.subList(0, Math.min(5, guides.size()))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("title", g.getTitle());
            m.put("category", g.getCategory());
            // 正文截取前200字给模型，避免Token爆炸
            String content = g.getContent() != null ? g.getContent() : "";
            m.put("summary", content.length() > 200 ? content.substring(0, 200) + "..." : content);
            slim.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", guides.size());
        result.put("guides", slim);
        return toJson(result);
    }

    @Tool(description = "计算租房费用明细：初始费用（押金+首月租金+中介费）和每月固定支出（租金+水电+网费+物业费）。用户问'要准备多少钱'、'算算费用'、'月供多少'时调用。")
    public String calculateCost(
            @ToolParam(description = "城市名，如：杭州") String city,
            @ToolParam(description = "月租金（元）") Double monthlyRent,
            @ToolParam(description = "押金月数，常见为1（押一付三里的押一）或2。不确定就传1", required = false) Integer depositMonths) {
        int depMonths = depositMonths != null && depositMonths > 0 ? depositMonths : 1;
        double rent = monthlyRent != null ? monthlyRent : 3000;

        double agentFeeLow = rent * 0.5;
        double agentFeeHigh = rent;
        double deposit = rent * depMonths;
        double minInitial = rent + deposit + agentFeeLow;
        double maxInitial = rent + deposit + agentFeeHigh;
        double utility = rent * 0.08;
        double internet = 100;
        double propertyFee = rent * 0.03;
        double monthlyTotal = rent + utility + internet + propertyFee;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("monthlyRent", rent);
        result.put("depositMonths", depMonths);
        result.put("initialCost", Map.of(
                "firstMonthRent", Math.round(rent),
                "deposit", Math.round(deposit),
                "agentFeeRange", Math.round(agentFeeLow) + "~" + Math.round(agentFeeHigh),
                "minTotal", Math.round(minInitial),
                "maxTotal", Math.round(maxInitial)));
        result.put("monthlyCost", Map.of(
                "rent", Math.round(rent),
                "utilityEstimate", Math.round(utility),
                "internetEstimate", Math.round(internet),
                "propertyFee", Math.round(propertyFee),
                "total", Math.round(monthlyTotal)));
        return toJson(result);
    }

    @Tool(description = "对比同一城市两个区域的租房市场（房源数量、价格水平、样例房源）。用户问'A区和B区哪个好'、'对比一下两个区域'时调用。")
    public String compareDistricts(
            @ToolParam(description = "城市名") String city,
            @ToolParam(description = "第一个区域名") String district1,
            @ToolParam(description = "第二个区域名") String district2) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", city);
        result.put("district1", districtStats(city, district1));
        result.put("district2", districtStats(city, district2));
        return toJson(result);
    }

    private Map<String, Object> districtStats(String city, String district) {
        Map<String, Object> query = new HashMap<>();
        query.put("city", city);
        query.put("district", district);
        PageResult<HouseVO> r = houseService.findAll(query, 1, 5);
        double avgPrice = r.getList().stream()
                .mapToDouble(h -> h.getPrice() != null ? h.getPrice().doubleValue() : 0)
                .average().orElse(0);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("name", district);
        stats.put("houseCount", r.getTotal());
        stats.put("avgPrice", Math.round(avgPrice));
        stats.put("samples", r.getList().stream().map(h -> Map.of(
                "id", h.getId(), "title", h.getTitle(), "price", String.valueOf(h.getPrice()))).toList());
        return stats;
    }

    // ==================== 写操作工具（两步确认，工程方案决策5） ====================

    @Tool(description = "发布房源【第一步：预览，不写库】。房东用户说'帮我发布房源'、'我要出租'时调用。本工具只生成发布预览，返回后你必须把房源信息完整复述给用户，询问'确认发布吗？'。用户明确同意后才能调用 confirmPublishHouse。")
    public String publishHouse(
            @ToolParam(description = "房源标题，如：西湖区精装两居室出租") String title,
            @ToolParam(description = "城市") String city,
            @ToolParam(description = "区域") String district,
            @ToolParam(description = "详细地址") String address,
            @ToolParam(description = "月租金（元）") Double price,
            @ToolParam(description = "面积（平米）") Double area,
            @ToolParam(description = "卧室数") Integer bedrooms,
            @ToolParam(description = "客厅数", required = false) Integer livingRooms,
            @ToolParam(description = "卫生间数", required = false) Integer bathrooms,
            @ToolParam(description = "楼层信息，如：中层/共18层", required = false) String floorInfo,
            @ToolParam(description = "朝向，如：南", required = false) String orientation,
            @ToolParam(description = "装修情况：精装/简装/毛坯", required = false) String decoration,
            @ToolParam(description = "配套设施，逗号分隔，如：床,衣柜,空调,洗衣机", required = false) String facilities,
            @ToolParam(description = "房源描述", required = false) String description,
            @ToolParam(description = "联系电话", required = false) String contactPhone) {

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("pendingAction", "confirmPublishHouse");
        preview.put("title", title);
        preview.put("city", city);
        preview.put("district", district);
        preview.put("address", address);
        preview.put("price", price);
        preview.put("area", area);
        preview.put("bedrooms", bedrooms);
        preview.put("livingRooms", livingRooms);
        preview.put("bathrooms", bathrooms);
        preview.put("floorInfo", floorInfo);
        preview.put("orientation", orientation);
        preview.put("decoration", decoration);
        preview.put("facilities", facilities);
        preview.put("description", description);
        preview.put("contactPhone", contactPhone);
        preview.put("notice", "这是发布预览，尚未写库。请向用户复述以上全部信息并询问是否确认发布。用户同意后，用完全相同的参数调用 confirmPublishHouse。");
        return toJson(preview);
    }

    @Tool(description = "发布房源【第二步：真正写库】。仅当用户看过 publishHouse 的预览并明确回复'确认'、'发布吧'、'好的'之后才能调用。参数与 publishHouse 完全一致。")
    public String confirmPublishHouse(
            @ToolParam(description = "房源标题") String title,
            @ToolParam(description = "城市") String city,
            @ToolParam(description = "区域") String district,
            @ToolParam(description = "详细地址") String address,
            @ToolParam(description = "月租金（元）") Double price,
            @ToolParam(description = "面积（平米）") Double area,
            @ToolParam(description = "卧室数") Integer bedrooms,
            @ToolParam(description = "客厅数", required = false) Integer livingRooms,
            @ToolParam(description = "卫生间数", required = false) Integer bathrooms,
            @ToolParam(description = "楼层信息", required = false) String floorInfo,
            @ToolParam(description = "朝向", required = false) String orientation,
            @ToolParam(description = "装修情况", required = false) String decoration,
            @ToolParam(description = "配套设施", required = false) String facilities,
            @ToolParam(description = "房源描述", required = false) String description,
            @ToolParam(description = "联系电话", required = false) String contactPhone) {
        try {
            Long userId = UserContextHolder.getCurrentUserId();
            House house = new House();
            house.setLandlordId(userId);
            house.setTitle(title);
            house.setCity(city);
            house.setDistrict(district);
            house.setAddress(address);
            house.setPrice(price != null ? BigDecimal.valueOf(price) : BigDecimal.ZERO);
            house.setArea(area != null ? BigDecimal.valueOf(area) : BigDecimal.ZERO);
            house.setBedrooms(bedrooms != null ? bedrooms : 1);
            house.setLivingRooms(livingRooms != null ? livingRooms : 1);
            house.setBathrooms(bathrooms != null ? bathrooms : 1);
            house.setFloorInfo(floorInfo != null ? floorInfo : "");
            house.setOrientation(orientation != null ? orientation : "");
            house.setDecoration(decoration != null ? decoration : "");
            house.setFacilities(facilities != null ? facilities : "");
            house.setDescription(description != null ? description : "");
            house.setContactPhone(contactPhone != null ? contactPhone : "");

            House saved = houseService.publish(house);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("houseId", saved.getId());
            result.put("title", saved.getTitle());
            result.put("status", saved.getStatus());
            result.put("message", "房源「" + saved.getTitle() + "」发布成功（ID:" + saved.getId() + "），等待平台审核");
            return toJson(result);
        } catch (Exception e) {
            log.error("发布房源失败: {}", e.getMessage());
            return toJson(Map.of("error", "发布失败: " + e.getMessage()));
        }
    }

    @Tool(description = "给房东发短信【第一步：预览，不真正发送】。用户想联系房东、约看房时调用。返回预览后必须向用户复述手机号和内容并询问确认，用户同意后调用 confirmSendSms。")
    public String sendSms(
            @ToolParam(description = "接收方手机号") String phone,
            @ToolParam(description = "短信内容") String message) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("pendingAction", "confirmSendSms");
        preview.put("phone", phone);
        preview.put("message", message);
        preview.put("notice", "这是短信预览，尚未发送。请向用户复述手机号和内容并询问是否确认发送。用户同意后调用 confirmSendSms。");
        return toJson(preview);
    }

    @Tool(description = "给房东发短信【第二步：真正发送】。仅当用户确认过 sendSms 的预览后才能调用。")
    public String confirmSendSms(
            @ToolParam(description = "接收方手机号") String phone,
            @ToolParam(description = "短信内容") String message) {
        if (phone == null || phone.isBlank()) {
            return toJson(Map.of("error", "请提供手机号"));
        }
        boolean sent = aliSmsService.sendNotification(phone, message);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phone", phone);
        result.put("sent", sent);
        result.put("message", sent ? "短信已发送给 " + phone : "短信发送失败（短信通道未配置或不可用），建议直接电话联系房东");
        return toJson(result);
    }

    // ==================== 合同与文档工具 ====================

    @Tool(description = "审查租房合同文本，找出风险条款。用户贴出合同条款、问'这个合同有没有问题'时调用。返回风险点列表和总体风险等级。")
    public String reviewContract(
            @ToolParam(description = "合同完整文本内容") String text) {
        if (text == null || text.isBlank()) {
            return toJson(Map.of("error", "请提供合同文本内容"));
        }
        try {
            Long userId;
            try {
                userId = UserContextHolder.getCurrentUserId();
            } catch (Exception e) {
                userId = 0L;   // 未登录也允许审查（与旧实现一致）
            }
            Map<String, Object> reviewResult = contractReviewService.reviewContract(text, userId);
            return toJson(reviewResult);
        } catch (Exception e) {
            log.error("合同审查失败: {}", e.getMessage());
            return toJson(Map.of("error", "审查失败: " + e.getMessage()));
        }
    }

    @Tool(description = "生成 Word 文档（.docx）：租房合同、看房清单、对比报告等。用户明确要'生成Word'、'下载文档'时调用。返回下载链接。")
    public String generateDocx(
            @ToolParam(description = "文档标题") String title,
            @ToolParam(description = "文档类型：contract合同/checklist清单/comparison对比/report报告") String type,
            @ToolParam(description = "内容需求描述，越具体越好") String content) {
        return generateDocument(title, type, content, true);
    }

    @Tool(description = "生成 PDF 文档：看房清单、费用报告、租房计划等。用户明确要'生成PDF'时调用。返回下载链接。")
    public String generatePdf(
            @ToolParam(description = "文档标题") String title,
            @ToolParam(description = "文档类型：checklist清单/cost_report费用报告/plan计划/comparison对比") String type,
            @ToolParam(description = "内容需求描述，越具体越好") String content) {
        return generateDocument(title, type, content, false);
    }

    /**
     * 文档生成公共逻辑：
     * 旧实现用 aiService.chatForJson（LangChain4j 手写 JSON 解析），
     * 新实现用 Spring AI 结构化输出 .entity()（讲义02 第8章）——
     * Prompt 约束输出 JSON + Jackson 自动反序列化，不再手动 extractJson。
     */
    private String generateDocument(String title, String type, String contentDesc, boolean isDocx) {
        try {
            Long userId;
            try {
                userId = UserContextHolder.getCurrentUserId();
            } catch (Exception e) {
                userId = 0L;
            }

            String docPrompt = String.format("""
                    请生成一份「%s」的文档内容，用于生成%s。
                    类型：%s
                    用户需求：%s
                    要求4-8个段落，每个段落的 body 要详细、具体、有用。\
                    如果是费用报告要有数字，如果是合同要有条款，如果是清单要有逐项内容。
                    """, title, isDocx ? "Word文档" : "PDF", type, contentDesc);

            // ★ Spring AI 结构化输出：自动约束 JSON + 反序列化为 record
            DocSections docSections = chatClient.prompt()
                    .system("你是一个专业的文档撰写助手。")
                    .user(docPrompt)
                    .call()
                    .entity(DocSections.class);

            List<Map<String, Object>> sections = new ArrayList<>();
            if (docSections != null && docSections.sections() != null) {
                for (DocSections.Section s : docSections.sections()) {
                    sections.add(Map.of("heading", s.heading(), "body", s.body()));
                }
            }
            if (sections.isEmpty()) {
                sections.add(Map.of("heading", title, "body", contentDesc));
            }

            GeneratedFileInfo fileInfo = isDocx
                    ? documentGenerationService.generateDocx(title, sections, userId)
                    : documentGenerationService.generatePdf(title, sections, userId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("title", title);
            result.put("fileType", isDocx ? "docx" : "pdf");
            result.put("filename", fileInfo.getFilename());
            String url=fileInfo.getDownloadUrl();
            int marker=url.indexOf("/api/file/download/");
            result.put("downloadUrl",marker>=0?url.substring(marker):url);
            result.put("text",title+"\n\n"+sections.stream().map(section->section.get("heading")+"\n"+section.get("body")).collect(java.util.stream.Collectors.joining("\n\n")));
            result.put("fileSize", fileInfo.getFileSize());
            result.put("message", "文档已生成，用户可通过 downloadUrl 下载");
            return toJson(result);
        } catch (Exception e) {
            log.error("生成文档失败: {}", e.getMessage());
            return toJson(Map.of("error", "生成失败: " + e.getMessage()));
        }
    }

    @Tool(description = "规划文档大纲（不生成文件）。用户想先看看文档结构、再决定生成什么时调用。返回章节大纲。")
    public String planDocument(
            @ToolParam(description = "文档类型，如：report/contract/checklist") String type,
            @ToolParam(description = "文档主题") String topic,
            @ToolParam(description = "具体要求") String requirements) {
        try {
            String planPrompt = String.format("""
                    请为以下文档需求规划一个详细大纲：
                    文档类型：%s
                    主题：%s
                    要求：%s
                    规划5-8个章节，每个章节的描述要具体，并给出2-4个要点。
                    """, type, topic, requirements);

            DocPlan plan = chatClient.prompt()
                    .system("你是专业的文档策划师。")
                    .user(planPrompt)
                    .call()
                    .entity(DocPlan.class);

            return plan != null ? toJson(plan) : toJson(Map.of("error", "规划失败，请重试"));
        } catch (Exception e) {
            log.error("规划文档失败: {}", e.getMessage());
            return toJson(Map.of("error", "规划失败: " + e.getMessage()));
        }
    }

    // ==================== 结构化输出 DTO（讲义02：字段名与模型输出 JSON 一致） ====================

    /** 文档段落集合（generateDocx/generatePdf 用） */
    public record DocSections(List<Section> sections) {
        public record Section(String heading, String body) {}
    }

    /** 文档大纲（planDocument 用） */
    public record DocPlan(String title, List<PlanSection> sections) {
        public record PlanSection(String heading, String description, List<String> keyPoints) {}
    }

    // ==================== 工具方法 ====================

    private String toJson(Object obj) {
        return JSON.toJSONString(obj, SerializerFeature.WriteMapNullValue);
    }
}
