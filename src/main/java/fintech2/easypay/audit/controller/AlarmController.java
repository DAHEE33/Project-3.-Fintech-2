package fintech2.easypay.audit.controller;

import fintech2.easypay.audit.service.AlarmService;
import fintech2.easypay.auth.dto.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getNotificationCount(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            String userId = userPrincipal != null ? userPrincipal.getId().toString() : null;
            int count = alarmService.getUnreadNotificationCount(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("count", count);
            response.put("success", true);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("count", 0);
            response.put("success", false);
            response.put("message", "알림 개수 조회에 실패했습니다");
            
            return ResponseEntity.ok(response);
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getNotificationList(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "all") String category) {
        try {
            String userId = userPrincipal != null ? userPrincipal.getId().toString() : null;
            List<Map<String, Object>> alarms = alarmService.getNotificationList(userId, category);
            
            Map<String, Object> response = new HashMap<>();
            response.put("alarms", alarms);
            response.put("success", true);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("alarms", List.of());
            response.put("success", false);
            response.put("message", "알림 목록 조회에 실패했습니다");
            
            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/mark-read")
    public ResponseEntity<Map<String, Object>> markNotificationsAsRead(
            @AuthenticationPrincipal UserPrincipal userPrincipal) {
        try {
            String userId = userPrincipal != null ? userPrincipal.getId().toString() : null;
            alarmService.markNotificationsAsRead(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "알림이 읽음 처리되었습니다");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "알림 읽음 처리에 실패했습니다");
            
            return ResponseEntity.ok(response);
        }
    }
} 