package com.rental.platform.controller;

import com.rental.platform.ai.AgentPresentation;
import com.rental.platform.ai.advisor.TokenAdvisor;
import com.rental.platform.ai.tool.RentalTools;
import com.rental.platform.config.SpringAiConfig;
import com.rental.platform.config.UserContextHolder;
import com.rental.platform.exception.BusinessException;
import com.rental.platform.service.PrivateMessageService;
import com.rental.platform.utils.Result;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
 private final com.rental.platform.service.HouseImageSearchService imageSearch;
 private final com.rental.platform.service.ContractReviewService contracts;
 private final com.rental.platform.client.PythonAiClient python;
 private final ChatModel model;private final RentalTools tools;private final ChatMemory memory;private final TokenAdvisor tokens;private final PrivateMessageService privateMessages;
 public AssistantController(ChatModel model,RentalTools tools,ChatMemory memory,TokenAdvisor tokens,PrivateMessageService privateMessages,com.rental.platform.service.HouseImageSearchService imageSearch,com.rental.platform.service.ContractReviewService contracts,com.rental.platform.client.PythonAiClient python){this.imageSearch=imageSearch;this.contracts=contracts;this.python=python;this.model=model;this.tools=tools;this.memory=memory;this.tokens=tokens;this.privateMessages=privateMessages;}
 public record Input(String message,String mode){}
 private String message(Input body){if(body.message()==null||body.message().isBlank())throw new BusinessException("请输入你的需求");if(body.message().length()>12000)throw new BusinessException("一次消息请控制在12000字以内");return body.message().trim();}
 private String system(String mode){return SpringAiConfig.AGENT_BEHAVIOR_RULES+"""
   你叫小易，是统一的租房助手。找房、防坑、费用评估、合同咨询都由你在同一对话处理，无需用户切换角色或理解技术。
   根据对话需要自行决定是否调用工具，用户不需要开关操作模式。
   租房政策、押金维修、法律权益类问题必须优先调用 searchRentalKnowledge，并依据返回资料回答，缺少资料时说明不足。合同文本用 reviewContract，费用用 calculateCost，房源用 searchHouses/getHouseDetail。
   房源已由界面渲染图片卡片，搜索或详情工具成功后不要再输出房源表格或逐套重复罗列，只简要解释筛选依据、数量和建议。
   文档由界面渲染文件卡片和PDF预览，生成成功后不要输出下载URL或伪造文件链接，只简要说明用途；全文复制和下载交由文件卡片。
   联系房东使用站内私信草稿，收件人和全文由卡片显示；只能用户点击确认发送，不得绕过。
   不要编造房源、文件或工具执行成功结果。数据标明演示时说明为仿真示例。
   """+"当前咨询主题："+Map.of("house_find","找房","contract","合同咨询","pitfall","防坑","cost","费用评估").getOrDefault(mode==null?"":mode,"租房咨询");}
 private ChatClient client(AgentPresentation presentation){ToolCallback[] wrapped=Arrays.stream(ToolCallbacks.from(tools)).map(presentation::wrap).toArray(ToolCallback[]::new);return ChatClient.builder(model).defaultToolCallbacks(wrapped).defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build(),tokens).build();}
 private Set<Object> pending(long uid){return privateMessages.pending(uid).stream().map(d->d.get("id")).collect(java.util.stream.Collectors.toSet());}
 private List<Map<String,Object>> newDrafts(long uid,Set<Object> old){return privateMessages.pending(uid).stream().filter(d->!old.contains(d.get("id"))).toList();}
 @DeleteMapping("/memory") public Result<?> clearMemory(){long uid=UserContextHolder.getCurrentUserId();memory.clear("agent-"+uid);return Result.success();}
 @PostMapping(value="/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
 public Result<?> upload(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,@RequestParam String purpose,@RequestParam(defaultValue="") String caption) throws Exception {
  long uid=UserContextHolder.getCurrentUserId();
  if(file.isEmpty()||file.getSize()>10*1024*1024)throw new BusinessException("请选择10MB以内的图片或合同文件");
  String filename=java.util.Optional.ofNullable(file.getOriginalFilename()).orElse("upload");String lower=filename.toLowerCase(java.util.Locale.ROOT);
  boolean image=lower.matches(".*\\.(jpg|jpeg|png|webp|bmp)$");
  if(caption.length()>2000)throw new BusinessException("补充说明请控制在2000字以内");
  Map<String,Object> action=new LinkedHashMap<>();String reply;
  if("house".equals(purpose)) {
   if(!image)throw new BusinessException("以图找房支持 JPG、PNG、WEBP 或 BMP 图片");
   var allMatches=imageSearch.searchByImage(filename,file.getBytes(),8);
   var matches=allMatches.stream().limit(12).toList();
   for(var item:matches) item.put("id",item.get("houseId"));
   action.put("type","search_houses");action.put("result",Map.of("list",matches,"total",allMatches.size()));
   reply=matches.isEmpty()?"暂时没有找到相似房源。可以换张图片，或告诉我你喜欢的风格、城市和预算。":"我找到了这些视觉相近的房源。相似度只反映图片风格，你也可以继续补充城市和预算。";
  } else if("contract".equals(purpose)) {
   String text;
   if(image)text=python.ocr(filename,file.getBytes());
   else if(lower.endsWith(".pdf")) {
    var reader=new com.lowagie.text.pdf.PdfReader(file.getBytes());var content=new StringBuilder();
    try {if(reader.getNumberOfPages()>50)throw new BusinessException("请上传50页以内的合同");var extractor=new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);for(int i=1;i<=reader.getNumberOfPages();i++)content.append(extractor.getTextFromPage(i)).append("\n");}finally{reader.close();}text=content.toString();
   } else if(lower.endsWith(".docx")) {try(var document=new org.apache.poi.xwpf.usermodel.XWPFDocument(file.getInputStream());var extractor=new org.apache.poi.xwpf.extractor.XWPFWordExtractor(document)){text=extractor.getText();}}
   else throw new BusinessException("合同支持图片、PDF 或 DOCX 文件");
   if(text==null||text.trim().length()<10)throw new BusinessException("没有识别到有效文字；扫描PDF请改传清晰合同图片");
   if(text.length()>60000)throw new BusinessException("合同文字较多，请分段提交");
   var result=contracts.reviewContract(text,uid);
   if(Boolean.FALSE.equals(result.get("success")))throw new BusinessException(String.valueOf(result.getOrDefault("message","合同审查暂不可用")));
   action.put("type","contract_review");action.put("result",result);reply="合同已审查，下面列出了需要留意的条款。你可以继续追问其中某一条，也可以让我整理成文档。";
  } else throw new BusinessException("请选择房源图片或合同文件");
  action.put("id",UUID.randomUUID().toString());action.put("status","completed");
  memory.add("agent-"+uid,List.of(new org.springframework.ai.chat.messages.UserMessage("用户上传"+("house".equals(purpose)?"房源参考图片":"合同")+"，补充说明："+caption),new org.springframework.ai.chat.messages.AssistantMessage(reply+"\n真实处理结果："+com.alibaba.fastjson.JSON.toJSONString(action))));
  return Result.success(Map.of("reply",reply,"actions",List.of(action),"privateDrafts",List.of()));
 }
 @PostMapping("/reply") public Result<?> reply(@RequestBody Input input){
  String text=message(input);long uid=UserContextHolder.getCurrentUserId();String role=UserContextHolder.getCurrentUserRole();var old=pending(uid);var presentation=new AgentPresentation(uid,role,null);
  String reply=client(presentation).prompt().system(system(input.mode())).user(text).advisors(a->{a.param(ChatMemory.CONVERSATION_ID,"agent-"+uid);a.param(TokenAdvisor.USER_ID_PARAM,String.valueOf(uid));}).call().content();
  return Result.success(Map.of("reply",reply==null?"":reply,"actions",presentation.actions(),"privateDrafts",newDrafts(uid,old)));
 }
 @PostMapping(value="/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
 public Flux<ServerSentEvent<Map<String,Object>>> stream(@RequestBody Input input){
  String text=message(input);long uid=UserContextHolder.getCurrentUserId();String role=UserContextHolder.getCurrentUserRole();var old=pending(uid);
  return Flux.<ServerSentEvent<Map<String,Object>>>create(sink->{
   var presentation=new AgentPresentation(uid,role,action->sink.next(ServerSentEvent.builder(Map.<String,Object>of("action",action)).event("action").build()));
   sink.next(ServerSentEvent.builder(Map.<String,Object>of("message","小易正在理解你的需求…")).event("status").build());
   var subscription=client(presentation).prompt().system(system(input.mode())).user(text).advisors(a->{a.param(ChatMemory.CONVERSATION_ID,"agent-"+uid);a.param(TokenAdvisor.USER_ID_PARAM,String.valueOf(uid));}).stream().content().timeout(Duration.ofSeconds(150))
    .subscribeOn(Schedulers.boundedElastic()).subscribe(chunk->{if(chunk!=null&&!chunk.isEmpty())sink.next(ServerSentEvent.builder(Map.<String,Object>of("text",chunk)).event("text").build());},error->{sink.next(ServerSentEvent.builder(Map.<String,Object>of("message","回复暂时中断，已生成的卡片仍可使用，请稍后重试。")).event("error").build());sink.complete();},()->{
     try{sink.next(ServerSentEvent.builder(Map.<String,Object>of("actions",presentation.actions(),"privateDrafts",newDrafts(uid,old))).event("done").build());sink.complete();}catch(Exception e){sink.error(e);}
    });
   sink.onCancel(subscription::dispose);
  });
 }
}
