package com.example.demo.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ActivityQueryExpansionService {

    public String expand(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        List<String> terms = new ArrayList<>();
        terms.add(query);

        addIfMatches(normalized, terms, List.of("周末", "放松", "休闲", "娱乐", "好玩", "轻松", "解压"),
                "音乐 文艺 摄影 社团 羽毛球 体育 校园 志愿 社区");
        addIfMatches(normalized, terms, List.of("ai", "人工智能", "大模型", "模型", "技术", "前沿", "讲座", "学习"),
                "AI 人工智能 大模型 技术 前沿 讲座 学术 编程 软件");
        addIfMatches(normalized, terms, List.of("编程", "算法", "程序", "代码", "竞赛", "训练", "acm", "icpc"),
                "编程 算法 程序设计 竞赛 训练营 ACM ICPC 数据结构");
        addIfMatches(normalized, terms, List.of("运动", "体育", "锻炼", "羽毛球", "比赛", "健身"),
                "体育运动 羽毛球 友谊赛 比赛 锻炼 体育馆");
        addIfMatches(normalized, terms, List.of("公益", "志愿", "志愿者", "服务", "社区", "公益活动"),
                "志愿服务 志愿者 社区服务 公益 招募 服务时长");
        addIfMatches(normalized, terms, List.of("摄影", "拍照", "照片", "采风", "艺术", "户外"),
                "摄影 摄影社 户外 采风 艺术 构图 后期");
        addIfMatches(normalized, terms, List.of("音乐", "唱歌", "乐队", "演出", "晚会", "文艺"),
                "音乐 音乐节 夏日之声 演出 乐队 歌手 文艺");

        return String.join(" ", terms);
    }

    public boolean hasExpansion(String query) {
        return !expand(query).equals(query);
    }

    private void addIfMatches(String query, List<String> terms, List<String> triggers, String expansion) {
        for (String trigger : triggers) {
            if (query.contains(trigger.toLowerCase(Locale.ROOT))) {
                terms.add(expansion);
                return;
            }
        }
    }
}
