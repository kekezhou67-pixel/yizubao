package com.rental.platform.agent;

import java.util.List;
import java.util.Map;

/**
 * Agent 响应 —— AI 回复 + 执行的动作
 */
public class AgentResponse {

    /** AI 的文本回复 */
    private String reply;

    /** 当前模式 */
    private String mode;

    /** 执行的动作列表（每个动作包含类型、参数、结果） */
    private List<AgentAction> actions;

    public AgentResponse() {}

    public AgentResponse(String reply, String mode, List<AgentAction> actions) {
        this.reply = reply;
        this.mode = mode;
        this.actions = actions;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public List<AgentAction> getActions() { return actions; }
    public void setActions(List<AgentAction> actions) { this.actions = actions; }

    /**
     * 单个动作
     */
    public static class AgentAction {
        /** 动作类型 */
        private String type;
        /** 动作状态：pending/executing/completed/failed */
        private String status;
        /** 动作参数 */
        private Map<String, Object> params;
        /** 动作执行结果数据 */
        private Object result;
        /** 动作描述 */
        private String description;

        public AgentAction() {}

        public AgentAction(String type, String status, Map<String, Object> params, Object result, String description) {
            this.type = type;
            this.status = status;
            this.params = params;
            this.result = result;
            this.description = description;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}
