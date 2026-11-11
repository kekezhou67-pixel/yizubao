package com.rental.platform.mcp;

import com.rental.platform.ai.RagService;
import com.rental.platform.ai.tool.RentalTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * MCP 对外工具集（AI 改造 · 阶段8，讲义03 §7 / 工程方案 §8.1）
 *
 * 通过 MCP 协议把平台 6 项能力开放给任意外部 AI 应用
 * （Claude Desktop / Cursor / 其他 Spring AI MCP Client）。
 *
 * 为什么单独建类而不直接注册 RentalTools：
 * MCP 只开放【只读】能力白名单（最小权限原则，讲义03 安全要求）——
 * 发布房源/发短信/收藏等写操作依赖登录用户上下文（JWT），
 * MCP 调用方没有用户态，暴露写操作既危险也无法审计。
 * 本类方法全部委托 RentalTools / RagService，逻辑零重复。
 */
@Component
public class McpTools {

    private final RentalTools rentalTools;
    private final RagService ragService;

    public McpTools(RentalTools rentalTools, RagService ragService) {
        this.rentalTools = rentalTools;
        this.ragService = ragService;
    }

    @Tool(description = "按条件搜索租房房源（城市/区域/价格区间/居室数），返回真实在租房源列表")
    public String searchHouses(
            @ToolParam(description = "城市名，如：杭州。可空", required = false) String city,
            @ToolParam(description = "区域名，如：西湖区。可空", required = false) String district,
            @ToolParam(description = "最低月租金（元）。可空", required = false) Integer minPrice,
            @ToolParam(description = "最高月租金（元）。可空", required = false) Integer maxPrice,
            @ToolParam(description = "卧室数。可空", required = false) Integer bedrooms,
            @ToolParam(description = "关键词。可空", required = false) String keyword) {
        return rentalTools.searchHouses(city, district, minPrice, maxPrice, bedrooms, keyword);
    }

    @Tool(description = "查询单个房源的完整详情（地址、户型、设施、地铁距离等）")
    public String getHouseDetail(
            @ToolParam(description = "房源ID") Long houseId) {
        return rentalTools.getHouseDetail(houseId);
    }

    @Tool(description = "查询地铁信息：不传城市返回城市列表；传城市返回线路；传城市+线路返回站点")
    public String searchSubway(
            @ToolParam(description = "城市名。可空", required = false) String city,
            @ToolParam(description = "线路名。可空", required = false) String line) {
        return rentalTools.searchSubway(city, line);
    }

    @Tool(description = "检索租房知识库（民法典租赁章节、租房法律常识），返回最相关的法条/知识片段及来源。回答租房法律问题前应先调用本工具。")
    public String queryKnowledge(
            @ToolParam(description = "查询问题或关键词，如：押金退还、维修责任") String query,
            @ToolParam(description = "返回条数，默认5", required = false) Integer topK) {
        int k = topK != null && topK > 0 ? Math.min(topK, 10) : 5;
        List<RagService.RetrievedChunk> chunks = ragService.retrieve(query, k);
        if (chunks.isEmpty()) {
            return "{\"count\":0,\"hint\":\"知识库中未检索到相关内容\"}";
        }
        StringBuilder sb = new StringBuilder("{\"count\":").append(chunks.size()).append(",\"results\":[");
        for (int i = 0; i < chunks.size(); i++) {
            RagService.RetrievedChunk c = chunks.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"text\":").append(jsonQuote(c.childText()))
              .append(",\"docTitle\":").append(jsonQuote(c.docTitle()))
              .append(",\"score\":").append(String.format("%.4f", c.score()))
              .append("}");
        }
        return sb.append("]}").toString();
    }

    @Tool(description = "审查租房合同文本，逐条分析风险并给出法条依据")
    public String reviewContract(
            @ToolParam(description = "合同完整文本") String text) {
        return rentalTools.reviewContract(text);
    }

    @Tool(description = "查询租房攻略（签约流程、防坑技巧、租房准备等经验文章）")
    public String getGuides(
            @ToolParam(description = "分类。可空", required = false) String category,
            @ToolParam(description = "关键词。可空", required = false) String keyword) {
        return rentalTools.getGuides(category, keyword);
    }

    // ==================== 内部 ====================

    private String jsonQuote(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "").replace("\t", "\\t") + "\"";
    }
}
