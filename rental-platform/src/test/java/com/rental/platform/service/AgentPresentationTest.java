package com.rental.platform.service;

import com.rental.platform.ai.AgentPresentation;
import com.rental.platform.config.UserContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class AgentPresentationTest {
 @Test void realToolResultProducesCardAndUsesCapturedIdentity() throws Exception {
  var presentation=new AgentPresentation(123L,"TENANT",null);
  ToolCallback callback=new ToolCallback(){
   public ToolDefinition getToolDefinition(){return ToolDefinition.builder().name("searchHouses").description("test").inputSchema("{}").build();}
   public String call(String input){assertEquals(123L,UserContextHolder.getCurrentUserId());assertEquals("TENANT",UserContextHolder.getCurrentUserRole());return "{\"total\":1,\"houses\":[{\"id\":7,\"title\":\"demo\",\"coverImage\":\"/house-images/test.jpg\"}]}";}
  };
  var executor=Executors.newSingleThreadExecutor();
  try {executor.submit(()->presentation.wrap(callback).call("{}")).get();assertTrue(executor.submit(()->{try{UserContextHolder.getCurrentUserId();return false;}catch(Exception expected){return true;}}).get());}
  finally {executor.shutdownNow();}
  assertEquals(1,presentation.actions().size());assertEquals("search_houses",presentation.actions().get(0).get("type"));
 }
 @Test void requestReceiptsAreIsolatedAndErrorsDoNotCreateFakeCards(){
  var a=new AgentPresentation(1L,"TENANT",null);var b=new AgentPresentation(2L,"LANDLORD",null);
  a.capture("searchHouses","{\"error\":\"unavailable\"}");assertTrue(a.actions().isEmpty());
  a.capture("generatePdf","{\"success\":true,\"downloadUrl\":\"/api/file/download/pdf/test.pdf\",\"text\":\"actual content\"}");
  assertEquals(1,a.actions().size());assertTrue(b.actions().isEmpty());
 }
 @Test void identityIsRestoredAfterToolFailureAndNestedCalls(){
  assertThrows(RuntimeException.class,()->UserContextHolder.withToolIdentity(1L,"TENANT",()->{throw new RuntimeException("fail");}));
  assertThrows(Exception.class,UserContextHolder::getCurrentUserId);
  UserContextHolder.withToolIdentity(1L,"TENANT",()->{UserContextHolder.withToolIdentity(2L,"LANDLORD",()->{assertEquals(2L,UserContextHolder.getCurrentUserId());return null;});assertEquals(1L,UserContextHolder.getCurrentUserId());return null;});
  assertThrows(Exception.class,UserContextHolder::getCurrentUserId);
 }
}
