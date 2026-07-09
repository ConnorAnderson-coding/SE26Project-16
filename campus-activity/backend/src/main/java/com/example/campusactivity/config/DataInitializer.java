package com.example.campusactivity.config;

import com.example.campusactivity.entity.Activity;
import com.example.campusactivity.entity.CheckInRecord;
import com.example.campusactivity.entity.Favorite;
import com.example.campusactivity.entity.Feedback;
import com.example.campusactivity.entity.Signup;
import com.example.campusactivity.entity.UserAccount;
import com.example.campusactivity.repository.ActivityRepository;
import com.example.campusactivity.repository.CheckInRepository;
import com.example.campusactivity.repository.FavoriteRepository;
import com.example.campusactivity.repository.FeedbackRepository;
import com.example.campusactivity.repository.SignupRepository;
import com.example.campusactivity.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner initData(UserRepository users,
                               ActivityRepository activities,
                               SignupRepository signups,
                               FavoriteRepository favorites,
                               FeedbackRepository feedbacks,
                               CheckInRepository checkIns) {
        return args -> {
            if (users.count() > 0) {
                return;
            }

            users.save(user("524030910001", "123456", "张三", "student", "软件学院", "2024级",
                    List.of("AI", "摄影", "羽毛球"), List.of("weekday_evening", "weekend"),
                    List.of("524030910002", "524030910003")));
            users.save(user("524030910002", "123456", "李四", "student", "计算机学院", "2023级",
                    List.of("编程", "电竞", "篮球"), List.of("weekend"), List.of("524030910001")));
            users.save(user("T001", "123456", "王老师", "teacher", "软件学院", "教师",
                    List.of("AI", "创业"), List.of("weekday_morning", "weekday_afternoon"), List.of()));

            activities.save(activity("1", "AI 与大模型技术前沿讲座", "academic",
                    "本次讲座邀请业界专家，介绍大模型在软件工程、教育等领域的最新应用与发展趋势。",
                    "2026-07-15T14:00:00", "2026-07-15T16:00:00", "软件大楼 A101",
                    "T001", "王老师", "软件学院", "https://picsum.photos/seed/ai-lecture/800/400",
                    120, 85, 42, "published", List.of("AI", "编程"), "AI2026"));
            activities.save(activity("2", "校园羽毛球友谊赛", "sports",
                    "面向全校师生的羽毛球双打友谊赛，按学院分组，优胜队伍将获得精美奖品。",
                    "2026-07-20T09:00:00", "2026-07-20T12:00:00", "体育馆羽毛球场",
                    "524030910002", "李四", "计算机学院", "https://picsum.photos/seed/badminton/800/400",
                    64, 43, 28, "published", List.of("羽毛球", "体育运动"), "BD2026"));
            activities.save(activity("3", "摄影社户外采风活动", "club",
                    "摄影社组织校园及周边人文采风，专业学长带队讲解构图与后期技巧。",
                    "2026-07-18T08:00:00", "2026-07-18T17:00:00", "图书馆前广场集合",
                    "524030910001", "张三", "软件学院", "https://picsum.photos/seed/photo-club/800/400",
                    30, 22, 35, "published", List.of("摄影", "艺术"), "PH2026"));
            activities.save(activity("4", "程序设计竞赛训练营", "innovation",
                    "为期一周的算法与数据结构强化训练，涵盖动态规划、图论等高频考点。",
                    "2026-07-22T19:00:00", "2026-07-29T21:00:00", "计算机楼 302 实验室",
                    "T001", "王老师", "计算机学院", "https://picsum.photos/seed/coding/800/400",
                    50, 38, 56, "published", List.of("编程", "AI"), "CP2026"));

            Activity volunteer = activity("5", "校园志愿者招募 - 社区服务日", "volunteer",
                    "组织同学前往周边社区开展助老、环境清洁等志愿服务，可计入志愿服务时长。",
                    "2026-07-25T08:30:00", "2026-07-25T16:00:00", "校门口集合",
                    "524030910001", "张三", "软件学院", "https://picsum.photos/seed/volunteer/800/400",
                    40, 31, 19, "ended", List.of("志愿服务"), "VL2026");
            volunteer.setRecordSummary("本次活动共有 31 名志愿者参与，服务时长累计 248 小时，获得社区居民一致好评。");
            volunteer.setRecordPhotos(List.of("https://picsum.photos/seed/vol1/400/300", "https://picsum.photos/seed/vol2/400/300"));
            volunteer.setRecordPublishedAt(LocalDateTime.parse("2026-07-26T10:00:00"));
            activities.save(volunteer);

            Activity music = activity("6", "校园音乐节 - 夏日之声", "arts",
                    "各社团及个人歌手同台演出，涵盖流行、摇滚、民谣等多种风格。",
                    "2026-06-28T18:30:00", "2026-06-28T21:30:00", "中心广场",
                    "524030910002", "李四", "信息学院", "https://picsum.photos/seed/music/800/400",
                    500, 412, 198, "ended", List.of("音乐", "文艺"), "MU2026");
            music.setRecordSummary("音乐节圆满落幕，12 支乐队和 8 位独立歌手带来精彩演出。");
            music.setRecordPhotos(List.of("https://picsum.photos/seed/music1/400/300", "https://picsum.photos/seed/music2/400/300"));
            music.setRecordPublishedAt(LocalDateTime.parse("2026-06-29T09:00:00"));
            activities.save(music);

            signups.save(signup("s1", "1", "524030910001", "approved", "2026-07-01T10:00:00"));
            signups.save(signup("s2", "2", "524030910001", "pending", "2026-07-02T14:00:00"));
            signups.save(signup("s3", "3", "524030910002", "approved", "2026-07-03T09:00:00"));
            signups.save(signup("s4", "1", "524030910002", "pending", "2026-07-04T11:00:00"));

            favorites.save(favorite("fav1", "524030910001", "1"));
            favorites.save(favorite("fav2", "524030910001", "4"));
            favorites.save(favorite("fav3", "524030910002", "2"));

            feedbacks.save(feedback("f1", "6", "524030910001", "张三", 5,
                    "演出非常精彩，氛围很好，希望每年都能举办！", "2026-06-29T12:00:00"));
            checkIns.save(checkIn("c1", "6", "524030910001", "qrcode", "2026-06-28T18:45:00"));
        };
    }

    private static UserAccount user(String id, String password, String name, String role, String college,
                                    String grade, List<String> interests, List<String> availableTime,
                                    List<String> friends) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setPassword(password);
        user.setName(name);
        user.setRole(role);
        user.setCollege(college);
        user.setGrade(grade);
        user.setInterests(interests);
        user.setAvailableTime(availableTime);
        user.setFriends(friends);
        return user;
    }

    private static Activity activity(String id, String title, String category, String description,
                                     String startTime, String endTime, String location, String organizerId,
                                     String organizerName, String college, String poster, Integer maxParticipants,
                                     Integer signupCount, Integer favoriteCount, String status, List<String> tags,
                                     String checkInCode) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setTitle(title);
        activity.setCategory(category);
        activity.setDescription(description);
        activity.setStartTime(LocalDateTime.parse(startTime));
        activity.setEndTime(LocalDateTime.parse(endTime));
        activity.setLocation(location);
        activity.setOrganizerId(organizerId);
        activity.setOrganizerName(organizerName);
        activity.setCollege(college);
        activity.setPoster(poster);
        activity.setMaxParticipants(maxParticipants);
        activity.setSignupCount(signupCount);
        activity.setFavoriteCount(favoriteCount);
        activity.setStatus(status);
        activity.setTags(tags);
        activity.setCheckInCode(checkInCode);
        return activity;
    }

    private static Signup signup(String id, String activityId, String userId, String status, String createdAt) {
        Signup signup = new Signup();
        signup.setId(id);
        signup.setActivityId(activityId);
        signup.setUserId(userId);
        signup.setStatus(status);
        signup.setCreatedAt(LocalDateTime.parse(createdAt));
        return signup;
    }

    private static Favorite favorite(String id, String userId, String activityId) {
        Favorite favorite = new Favorite();
        favorite.setId(id);
        favorite.setUserId(userId);
        favorite.setActivityId(activityId);
        return favorite;
    }

    private static Feedback feedback(String id, String activityId, String userId, String userName,
                                     Integer rating, String content, String createdAt) {
        Feedback feedback = new Feedback();
        feedback.setId(id);
        feedback.setActivityId(activityId);
        feedback.setUserId(userId);
        feedback.setUserName(userName);
        feedback.setRating(rating);
        feedback.setContent(content);
        feedback.setCreatedAt(LocalDateTime.parse(createdAt));
        return feedback;
    }

    private static CheckInRecord checkIn(String id, String activityId, String userId, String method, String time) {
        CheckInRecord record = new CheckInRecord();
        record.setId(id);
        record.setActivityId(activityId);
        record.setUserId(userId);
        record.setMethod(method);
        record.setTime(LocalDateTime.parse(time));
        return record;
    }
}
