package com.rental.platform.ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rental.platform.config.UserContextHolder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;
import java.util.*;
import java.util.function.Consumer;

/** Request-local tool receipts; cards come from real tool results, never model-authored IDs. */
public class AgentPresentation {
    private final List<Map<String,Object>> actions = new ArrayList<>();
    private final Consumer<Map<String,Object>> listener;
    private final Long userId;
    private final String role;
    public AgentPresentation(Long userId,String role,Consumer<Map<String,Object>> listener){this.userId=userId;this.role=role;this.listener=listener;}
    public synchronized List<Map<String,Object>> actions(){return List.copyOf(actions);}
    public ToolCallback wrap(ToolCallback delegate) {
        return new ToolCallback() {
            public ToolDefinition getToolDefinition(){return delegate.getToolDefinition();}
            public ToolMetadata getToolMetadata(){return delegate.getToolMetadata();}
            public String call(String input){return call(input,null);}
            public String call(String input,ToolContext context){
                String output=UserContextHolder.withToolIdentity(userId,role,()->context==null?delegate.call(input):delegate.call(input,context));
                capture(delegate.getToolDefinition().name(),output);return output;
            }
        };
    }
    public synchronized void capture(String tool,String output){
        String type=switch(tool){case "searchRentalKnowledge"->"policy_qa";case "reviewContract"->"contract_review";case "calculateCost"->"calculate_cost";case "searchHouses"->"search_houses";case "getHouseDetail"->"get_house_detail";case "generatePdf"->"generate_pdf";case "generateDocx"->"generate_docx";default->null;};
        if(type==null)return;
        JSONObject result;
        try {result=JSON.parseObject(output);}catch(Exception ignored){return;}
        if(result==null||result.containsKey("error")||Boolean.FALSE.equals(result.get("success")))return;
        if(type.equals("search_houses"))result.put("list",result.get("houses"));
        if(type.equals("get_house_detail")){JSONObject card=new JSONObject();card.put("total",1);card.put("list",List.of(result));result=card;}
        Map<String,Object> action=new LinkedHashMap<>();action.put("id",UUID.randomUUID().toString());action.put("type",type);action.put("status","completed");action.put("result",result);
        actions.add(action);if(listener!=null)listener.accept(action);
    }
}
