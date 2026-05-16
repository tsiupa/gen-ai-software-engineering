package com.support.api.service.classifier;

import com.support.api.dto.ClassificationResult;
import com.support.api.model.TicketCategory;
import com.support.api.model.TicketPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TicketClassifier {

    private static final Logger log = LoggerFactory.getLogger(TicketClassifier.class);

    private static final Map<TicketCategory, List<String>> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    private static final Map<TicketPriority, List<String>> PRIORITY_KEYWORDS = new LinkedHashMap<>();

    static {
        CATEGORY_KEYWORDS.put(TicketCategory.ACCOUNT_ACCESS, List.of(
                "login", "log in", "log-in", "sign in", "sign-in", "signin",
                "password", "2fa", "two-factor", "mfa", "locked out", "lockout",
                "can't access", "cant access", "cannot access", "authentication"));
        CATEGORY_KEYWORDS.put(TicketCategory.BILLING_QUESTION, List.of(
                "payment", "invoice", "refund", "charge", "billing", "billed",
                "subscription", "plan", "credit card", "receipt"));
        CATEGORY_KEYWORDS.put(TicketCategory.BUG_REPORT, List.of(
                "steps to reproduce", "reproduce", "reproduction", "regression",
                "defect", "stack trace", "stacktrace"));
        CATEGORY_KEYWORDS.put(TicketCategory.TECHNICAL_ISSUE, List.of(
                "bug", "error", "crash", "crashes", "crashing", "exception",
                "not working", "broken", "fails", "failed", "freezes", "hangs"));
        CATEGORY_KEYWORDS.put(TicketCategory.FEATURE_REQUEST, List.of(
                "feature", "feature request", "enhancement", "suggestion",
                "would love", "please add", "improvement", "wish", "could you add"));

        PRIORITY_KEYWORDS.put(TicketPriority.URGENT, List.of(
                "can't access", "cant access", "cannot access",
                "critical", "production down", "security"));
        PRIORITY_KEYWORDS.put(TicketPriority.HIGH, List.of(
                "important", "blocking", "asap"));
        PRIORITY_KEYWORDS.put(TicketPriority.LOW, List.of(
                "minor", "cosmetic", "suggestion"));
    }

    public ClassificationResult classify(String subject, String description) {
        String text = ((subject == null ? "" : subject) + " " + (description == null ? "" : description))
                .toLowerCase();

        CategoryMatch categoryMatch = findCategory(text);
        PriorityMatch priorityMatch = findPriority(text);

        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(categoryMatch.keywords);
        for (String kw : priorityMatch.keywords) {
            if (!allKeywords.contains(kw)) {
                allKeywords.add(kw);
            }
        }

        double confidence = computeConfidence(categoryMatch, priorityMatch);
        String reasoning = buildReasoning(categoryMatch, priorityMatch);

        ClassificationResult result = ClassificationResult.builder()
                .category(categoryMatch.category)
                .priority(priorityMatch.priority)
                .confidence(round(confidence))
                .reasoning(reasoning)
                .keywordsFound(allKeywords)
                .build();

        log.info("Classification decision: category={} priority={} confidence={} keywords={} reasoning='{}'",
                result.getCategory(), result.getPriority(), result.getConfidence(),
                result.getKeywordsFound(), result.getReasoning());

        return result;
    }

    private CategoryMatch findCategory(String text) {
        TicketCategory best = TicketCategory.OTHER;
        int bestCount = 0;
        List<String> bestKeywords = new ArrayList<>();
        for (Map.Entry<TicketCategory, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            List<String> hits = matches(text, entry.getValue());
            if (hits.size() > bestCount) {
                best = entry.getKey();
                bestCount = hits.size();
                bestKeywords = hits;
            }
        }
        return new CategoryMatch(best, bestKeywords);
    }

    private PriorityMatch findPriority(String text) {
        for (Map.Entry<TicketPriority, List<String>> entry : PRIORITY_KEYWORDS.entrySet()) {
            List<String> hits = matches(text, entry.getValue());
            if (!hits.isEmpty()) {
                return new PriorityMatch(entry.getKey(), hits);
            }
        }
        return new PriorityMatch(TicketPriority.MEDIUM, List.of());
    }

    private List<String> matches(String text, List<String> keywords) {
        List<String> hits = new ArrayList<>();
        for (String kw : keywords) {
            if (text.contains(kw)) {
                hits.add(kw);
            }
        }
        return hits;
    }

    private double computeConfidence(CategoryMatch cat, PriorityMatch pri) {
        if (cat.category == TicketCategory.OTHER && pri.keywords.isEmpty()) {
            return 0.2;
        }
        int signals = cat.keywords.size() + pri.keywords.size();
        double score = 0.3 + 0.2 * signals;
        return Math.min(1.0, score);
    }

    private String buildReasoning(CategoryMatch cat, PriorityMatch pri) {
        String catPart = cat.keywords.isEmpty()
                ? "no category keywords matched; defaulted to other"
                : "category " + cat.category.toJson() + " matched on " + cat.keywords;
        String priPart = pri.keywords.isEmpty()
                ? "no priority keywords matched; defaulted to medium"
                : "priority " + pri.priority.toJson() + " matched on " + pri.keywords;
        return catPart + "; " + priPart;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record CategoryMatch(TicketCategory category, List<String> keywords) {}
    private record PriorityMatch(TicketPriority priority, List<String> keywords) {}
}