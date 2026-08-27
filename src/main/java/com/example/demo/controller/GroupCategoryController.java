package com.example.demo.controller;

import com.example.demo.entity.GroupCategory;
import com.example.demo.notification.GroupCategoryChangeNotifier;
import com.example.demo.dto.Request.GroupCategoryRequest;
import com.example.demo.dto.Request.SearchRequest;
import com.example.demo.dto.Request.StatusListRequest;
import com.example.demo.service.GroupCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/group-category")
@CrossOrigin(origins = "http://localhost:4200/")
public class GroupCategoryController {

    private final GroupCategoryService groupCategoryService;
    private final GroupCategoryChangeNotifier notifier;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChanges() {
        return notifier.subscribe();
    }

    @GetMapping()
    public ResponseEntity<Page<GroupCategory>> fillAll(SearchRequest searchRequest){
        return ResponseEntity.ok(groupCategoryService.fillAll(searchRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getByID(@PathVariable Long id){
        GroupCategory deleteGroupCategory = groupCategoryService.delete(id);
        return ResponseEntity.ok(deleteGroupCategory);
    }

    @PostMapping()
    public ResponseEntity<?> add(@RequestBody GroupCategoryRequest groupCategoryRequest ){
        GroupCategory newGroupCategory = groupCategoryService.add(groupCategoryRequest);
        return ResponseEntity.ok(newGroupCategory);
    }

    @PutMapping()
    public ResponseEntity<?> update (@RequestBody GroupCategoryRequest groupCategoryRequest){
        GroupCategory newGroupCategory = groupCategoryService.update(groupCategoryRequest);
        return ResponseEntity.ok(newGroupCategory);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id){
        GroupCategory deleteGroupCategory = groupCategoryService.delete(id);
        return ResponseEntity.ok(deleteGroupCategory);
    }

    @PostMapping("/submit-approval")
    public ResponseEntity<?> submitApproval(@RequestBody StatusListRequest request){
        try{
            Map<String,Object> result = groupCategoryService.submitApproval(request.getIds(),request.getStatus());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(500).body("Lỗi: " + e);
        }

    }

    @PostMapping("/approve")
    public ResponseEntity<?> approve(@RequestBody StatusListRequest request){
        try{
            Map<String,Object> result = groupCategoryService.approve(request.getIds(),request.getStatus());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(500).body("Lỗi: " + e);
        }

    }

    @PostMapping("/cancel-approve")
    public ResponseEntity<?> cancelApprove(@RequestBody StatusListRequest request){
        try{
            Map<String,Object> result = groupCategoryService.cancelApprove(request.getIds(),request.getStatus());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(500).body("Lỗi: " + e);
        }

    }

    @PostMapping("/reject")
    public ResponseEntity<?> reject(@RequestBody StatusListRequest request){
        try{
            Map<String,Object> result = groupCategoryService.reject(request.getIds(),request.getStatus());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(500).body("Lỗi: " + e);
        }

    }

    @PostMapping("/search")
    public Page<GroupCategory> searchSpecification(@RequestBody SearchRequest searchRequest){
        return groupCategoryService.searchSpecification(searchRequest);
    }

    @PostMapping("/search-native-query")
    public Page<GroupCategory> searchNativeQuery(@RequestBody SearchRequest searchRequest){
        return groupCategoryService.searchNativeQuery(searchRequest);
    }

    @PostMapping("/search-procedure")
    public Page<GroupCategory> searchProcedure(@RequestBody SearchRequest searchRequest){
        return groupCategoryService.searchProcedure(searchRequest);
    }

    @PostMapping("/exportAll")
    public ResponseEntity<byte[]> exportAll(@RequestBody SearchRequest searchRequest){
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "GroupCategories_" + timestamp + ".xlsx";

            ByteArrayInputStream inputStream = groupCategoryService.exportAll(searchRequest);
            byte[] fileBytes = inputStream.readAllBytes();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", fileName);
            headers.setContentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

            // Cho phép FE đọc Content-Disposition
            headers.setAccessControlExposeHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(fileBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

}
