package com.example.campusactivity.controller;

import com.example.campusactivity.dto.ApiResponse;
import com.example.campusactivity.entity.CheckInRecord;
import com.example.campusactivity.repository.CheckInRepository;
import com.example.campusactivity.repository.SignupRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CheckInController {
    private final CheckInRepository checkInRepository;
    private final SignupRepository signupRepository;

    public CheckInController(CheckInRepository checkInRepository, SignupRepository signupRepository) {
        this.checkInRepository = checkInRepository;
        this.signupRepository = signupRepository;
    }

    @GetMapping("/checkins")
    public List<CheckInRecord> list(@RequestParam(required = false) String activityId,
                                    @RequestParam(required = false) String userId) {
        if (activityId != null) {
            return checkInRepository.findByActivityId(activityId);
        }
        if (userId != null) {
            return checkInRepository.findByUserId(userId);
        }
        return checkInRepository.findAll();
    }

    @PostMapping("/checkins")
    public CheckInRecord create(@RequestBody CheckInRecord checkInRecord) {
        return checkInRepository.save(checkInRecord);
    }

    @PostMapping("/activities/{activityId}/checkins")
    public ResponseEntity<ApiResponse<CheckInRecord>> checkIn(@PathVariable String activityId,
                                                              @RequestParam String userId,
                                                              @RequestParam(defaultValue = "qrcode") String method) {
        if (checkInRepository.findByActivityIdAndUserId(activityId, userId).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("您已签到"));
        }
        var signup = signupRepository.findByActivityIdAndUserId(activityId, userId);
        if (signup.isEmpty() || !"approved".equals(signup.get().getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("您尚未通过该活动的报名审核"));
        }
        CheckInRecord record = new CheckInRecord();
        record.setActivityId(activityId);
        record.setUserId(userId);
        record.setMethod(method);
        return ResponseEntity.ok(ApiResponse.ok("签到成功", checkInRepository.save(record)));
    }

    @PutMapping("/checkins/{id}")
    public ResponseEntity<CheckInRecord> update(@PathVariable String id, @RequestBody CheckInRecord checkInRecord) {
        if (!checkInRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        checkInRecord.setId(id);
        return ResponseEntity.ok(checkInRepository.save(checkInRecord));
    }

    @DeleteMapping("/checkins/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!checkInRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        checkInRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
