package com.rental.platform.controller;

import com.rental.platform.service.HousingTrustService;
import com.rental.platform.service.PlatformWorkflowService;
import com.rental.platform.utils.Result;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/trust")
public class HousingTrustController {
    private final HousingTrustService trust;
    private final PlatformWorkflowService flow;
    public HousingTrustController(HousingTrustService trust,PlatformWorkflowService flow){this.trust=trust;this.flow=flow;}
    @PostMapping("/upload") public Result<?> upload(@RequestParam MultipartFile file,@RequestParam String kind) throws Exception { return Result.success(trust.upload(file,kind)); }
    @GetMapping("/files/{id}") public ResponseEntity<byte[]> file(@PathVariable String id) throws Exception {return bytes(id,false);}
    @GetMapping("/photos/{id}") public ResponseEntity<byte[]> photo(@PathVariable String id) throws Exception {return bytes(id,true);}
    private ResponseEntity<byte[]> bytes(String id,boolean photo) throws Exception {
        var meta=trust.fileMetadata(id,photo);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(meta.get("mime").toString())).header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline").cacheControl(CacheControl.noStore()).body(trust.readFile(id));
    }
    @GetMapping("/mine") public Result<?> mine(){return Result.success(trust.mine());}
    @GetMapping("/application/{id}") public Result<?> application(@PathVariable long id){return Result.success(trust.application(id));}
    @PostMapping("/application/{id}") public Result<?> submit(@PathVariable long id,@RequestBody Map<String,Object> body){trust.submit(id,body);return Result.success("已提交审核",null);}
    @PostMapping("/withdraw/{id}") public Result<?> withdraw(@PathVariable long id){trust.withdraw(id);return Result.success("已下架",null);}
    @GetMapping("/public/{id}") public Result<?> publicInfo(@PathVariable long id){return Result.success(trust.publicInfo(id));}
    @GetMapping("/queue") public Result<?> queue(@RequestParam(defaultValue="PENDING") String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="") String keyword){return Result.success(trust.queue(status,page,keyword));}
    @PostMapping("/review/{id}") public Result<?> review(@PathVariable long id,@RequestBody Map<String,Object> body){trust.review(id,body);return Result.success("审核完成",null);}
    @PostMapping("/report/{id}") public Result<?> report(@PathVariable long id,@RequestBody Map<String,Object> body){trust.report(id,body);return Result.success("举报已提交",null);}
    @GetMapping("/reports") public Result<?> reports(@RequestParam(defaultValue="false") boolean all){return Result.success(trust.reports(all));}
    @PostMapping("/reports/{id}") public Result<?> handle(@PathVariable long id,@RequestBody Map<String,Object> body){trust.handleReport(id,body);return Result.success("处理完成",null);}
    @GetMapping("/notices") public Result<?> notices(){return Result.success(flow.notices());}
    @PostMapping("/notices/{id}/read") public Result<?> read(@PathVariable long id){flow.read(id);return Result.success("已读",null);}
    @GetMapping("/content/{kind}") public Result<?> content(@PathVariable String kind){return Result.success(flow.ownContent(kind));}
    @GetMapping("/content/{kind}/{id}") public Result<?> contentDetail(@PathVariable String kind,@PathVariable long id){return Result.success(flow.ownDetail(kind,id));}
    @PutMapping("/content/{kind}/{id}") public Result<?> resubmit(@PathVariable String kind,@PathVariable long id,@RequestBody Map<String,Object> body){flow.resubmit(kind,id,body);return Result.success("已重新提交审核",null);}
}
