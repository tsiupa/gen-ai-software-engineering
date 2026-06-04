package com.support.api.service.classifier;

import com.support.api.dto.ClassificationResult;
import com.support.api.model.TicketCategory;
import com.support.api.model.TicketPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CategorizationTest {

    private TicketClassifier classifier;

    @BeforeEach
    void init() {
        classifier = new TicketClassifier();
    }

    @Test
    @DisplayName("account_access category: login + password keywords")
    void accountAccessFromLogin() {
        ClassificationResult r = classifier.classify("Cannot login",
                "I forgot my password and cant login. authentication keeps failing.");
        assertThat(r.getCategory()).isEqualTo(TicketCategory.ACCOUNT_ACCESS);
        assertThat(r.getKeywordsFound()).contains("login", "password");
    }

    @Test
    @DisplayName("technical_issue category: error / not working keywords")
    void technicalIssueFromError() {
        ClassificationResult r = classifier.classify("Save button broken",
                "When I click save the app shows an error and the page is not working.");
        assertThat(r.getCategory()).isEqualTo(TicketCategory.TECHNICAL_ISSUE);
        assertThat(r.getKeywordsFound()).contains("error", "not working");
    }

    @Test
    @DisplayName("billing_question category: invoice / refund keywords")
    void billingFromInvoice() {
        ClassificationResult r = classifier.classify("Wrong invoice",
                "Please process a refund for my invoice - I was charged twice.");
        assertThat(r.getCategory()).isEqualTo(TicketCategory.BILLING_QUESTION);
        assertThat(r.getKeywordsFound()).contains("invoice", "refund", "charge");
    }

    @Test
    @DisplayName("feature_request category: would love / feature keywords")
    void featureRequestFromWouldLove() {
        ClassificationResult r = classifier.classify("Idea",
                "Would love a feature that lets me export data. Just a suggestion.");
        assertThat(r.getCategory()).isEqualTo(TicketCategory.FEATURE_REQUEST);
        assertThat(r.getKeywordsFound()).contains("would love", "feature");
    }

    @Test
    @DisplayName("bug_report category: steps to reproduce / regression keywords")
    void bugReportFromSteps() {
        ClassificationResult r = classifier.classify("Defect",
                "Steps to reproduce: open settings, tap save. This is a regression from last release.");
        assertThat(r.getCategory()).isEqualTo(TicketCategory.BUG_REPORT);
        assertThat(r.getKeywordsFound()).contains("steps to reproduce", "regression");
    }

    @Test
    @DisplayName("URGENT priority on 'critical' or 'security' keyword")
    void urgentPriority() {
        ClassificationResult r = classifier.classify("issue", "this is a critical security incident.");
        assertThat(r.getPriority()).isEqualTo(TicketPriority.URGENT);
        assertThat(r.getKeywordsFound()).contains("critical", "security");
    }

    @Test
    @DisplayName("HIGH priority on 'important' / 'blocking' / 'asap'")
    void highPriority() {
        ClassificationResult r = classifier.classify("ASAP", "important issue that is blocking our team");
        assertThat(r.getPriority()).isEqualTo(TicketPriority.HIGH);
        assertThat(r.getKeywordsFound()).anyMatch(k -> k.equals("important") || k.equals("blocking") || k.equals("asap"));
    }

    @Test
    @DisplayName("LOW priority on 'minor' / 'cosmetic' / 'suggestion'")
    void lowPriority() {
        ClassificationResult r = classifier.classify("Idea", "minor cosmetic suggestion only - nothing urgent");
        assertThat(r.getPriority()).isEqualTo(TicketPriority.LOW);
        assertThat(r.getKeywordsFound()).contains("minor", "cosmetic", "suggestion");
    }

    @Test
    @DisplayName("MEDIUM priority is the default when no priority keywords match")
    void mediumDefault() {
        ClassificationResult r = classifier.classify("Question", "How do I export my data to a file?");
        assertThat(r.getPriority()).isEqualTo(TicketPriority.MEDIUM);
    }

    @Test
    @DisplayName("Result includes confidence (0,1], reasoning, and keyword list")
    void resultMetadata() {
        ClassificationResult r = classifier.classify("Cannot access",
                "I cant access my account, this is critical.");
        assertThat(r.getConfidence()).isBetween(0.0, 1.0);
        assertThat(r.getConfidence()).isGreaterThan(0.0);
        assertThat(r.getReasoning()).isNotBlank();
        assertThat(r.getKeywordsFound()).isNotEmpty();

        ClassificationResult unmatched = classifier.classify("hello", "hi there");
        assertThat(unmatched.getCategory()).isEqualTo(TicketCategory.OTHER);
        assertThat(unmatched.getPriority()).isEqualTo(TicketPriority.MEDIUM);
        assertThat(unmatched.getReasoning()).contains("no category keywords matched");
    }
}