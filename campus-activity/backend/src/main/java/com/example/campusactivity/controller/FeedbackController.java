package com.example.campusactivity.controller;

import com.example.campusactivity.dto.ApiResponse;
import com.example.campusactivity.entity.Feedback;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.FeedbackRepository;
import com.example.campusactivity.repository.UserRepository;
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
public class FeedbackController {
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    public FeedbackController(FeedbackRepository feedbackRepository, UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/feedbacks")
    public List<Feedback> list(@RequestParam(required = false) String activityId,
                               @RequestParam(required = false) String userId) {
        if (activityId != null) {
            return feedbackRepository.findByActivityId(activityId);
        }
        if (userId != null) {
            return feedbackRepository.findByUserId(userId);
        }
        return feedbackRepository.findAll();
    }

    @PostMapping("/feedbacks")
    public Feedback create(@RequestBody Feedback feedback) {
        return feedbackRepository.save(feedback);
    }

    @PostMapping("/activities/{activityId}/feedbacks")
    public ResponseEntity<ApiResponse<Feedback>> createForActivity(@PathVariable String activityId,
                                                                   @RequestParam String userId,
                                                                   @RequestBody Feedback feedback) {
        UserAccount user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail("用户不存在"));
        }
        feedback.setActivityId(activityId);
        feedback.setUserId(userId);
        feedback.setUserName(user.getName());
        return ResponseEntity.ok(ApiResponse.ok("评价提交成功", feedbackRepository.save(feedback)));
    }

    @PutMapping("/feedbacks/{id}")
    public ResponseEntity<Feedback> update(@PathVariable String id, @RequestBody Feedback feedback) {
        if (!feedbackRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        feedback.setId(id);
        return ResponseEntity.ok(feedbackRepository.save(feedback));
    }

    @DeleteMapping("/feedbacks/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (!feedbackRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        feedbackRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
