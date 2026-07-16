package com.example.campusactivity.controller;

import com.example.campusactivity.dto.ApiResponse;
import com.example.campusactivity.dto.ReviewRequest;
import com.example.campusactivity.entity.Activity;
import com.example.campusactivity.entity.Signup;
import com.example.campusactivity.repository.ActivityRepository;
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
public class SignupController {
    private final SignupRepository signupRepository;
    private final ActivityRepository activityRepository;

    public SignupController(SignupRepository signupRepository, ActivityRepository activityRepository) {
        this.signupRepository = signupRepository;
        this.activityRepository = activityRepository;
    }

    @GetMapping("/signups")
    public List<Signup> list(@RequestParam(required = false) String activityId,
                             @RequestParam(required = false) String userId) {
        if (activityId != null) {
            return signupRepository.findByActivityId(activityId);
        }
        if (userId != null) {
            return signupRepository.findByUserId(userId);
        }
        return signupRepository.findAll();
    }

    @GetMapping("/signups/{id}")
    public ResponseEntity<Signup> get(@PathVariable String id) {
        return signupRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/signups")
    public Signup create(@RequestBody Signup signup) {
        return signupRepository.save(signup);
    }

    @PostMapping("/activities/{activityId}/signup")
    public ResponseEntity<ApiResponse<Signup>> signup(@PathVariable String activityId, @RequestParam String userId) {
        if (signupRepository.findByActivityIdAndUserId(activityId, userId).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("您已报名该活动"));
        }
        Activity activity = activityRepository.findById(activityId).orElse(null);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }
        if (activity.getSignupCount() != null && activity.getMaxParticipants() != null
                && activity.getSignupCount() >= activity.getMaxParticipants()) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("报名人数已满"));
        }

        Signup signup = new Signup();
        signup.setActivityId(activityId);
        signup.setUserId(userId);
        signup.setStatus("pending");
        Signup saved = signupRepository.save(signup);

        activity.setSignupCount((activity.getSignupCount() == null ? 0 : activity.getSignupCount()) + 1);
        activityRepository.save(activity);
        return ResponseEntity.ok(ApiResponse.ok("报名成功，等待组织者审核", saved));
    }

    @PutMapping("/signups/{id}")
    public ResponseEntity<Signup> update(@PathVariable String id, @RequestBody Signup signup) {
        if (!signupRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        signup.setId(id);
        return ResponseEntity.ok(signupRepository.save(signup));
    }

    @PutMapping("/signups/{id}/review")
    public ResponseEntity<Signup> review(@PathVariable String id, @RequestBody ReviewRequest request) {
        return signupRepository.findById(id)
                .map(signup -> {
                    signup.setStatus(request.approved() ? "approved" : "rejected");
                    return ResponseEntity.ok(signupRepository.save(signup));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/signups/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!signupRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        signupRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
