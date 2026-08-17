package com.uniqa.crmpoc.config;

import com.uniqa.crmpoc.domain.Category;
import com.uniqa.crmpoc.domain.Rule;
import com.uniqa.crmpoc.domain.RuleCondition;
import com.uniqa.crmpoc.domain.RuleField;
import com.uniqa.crmpoc.domain.RuleOperator;
import com.uniqa.crmpoc.repository.CategoryRepository;
import com.uniqa.crmpoc.repository.RuleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds default categories and a couple of starter rules on first boot, so
 * the rule-builder UI and inbox view aren't empty for the demo. Safe to run
 * repeatedly - checks existence before inserting.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final RuleRepository ruleRepository;

    public DataSeeder(CategoryRepository categoryRepository, RuleRepository ruleRepository) {
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
    }

    @Override
    public void run(String... args) {
        seedCategory("Complaints", "Unhappy customers, service issues, escalations");
        seedCategory("Claims", "New claim submissions and claim status inquiries");
        seedCategory("PolicyChanges", "Address changes, coverage changes, renewals");
        seedCategory("GeneralInquiry", "Pre-sales and general questions");

        if (ruleRepository.count() == 0) {
            Rule r1 = new Rule();
            r1.setName("Claim complaint keyword");
            r1.setDescription("Body mentions a claim and the customer sounds negative -> Complaints");
            r1.replaceConditions(List.of(condition(RuleField.BODY, RuleOperator.CONTAINS, "claim")));
            r1.setRequireNegativeSentiment(true);
            r1.setTargetCategoryName("Complaints");
            r1.setPriority(100);
            ruleRepository.save(r1);

            Rule r2 = new Rule();
            r2.setName("New claim submission");
            r2.setDescription("Body mentions submitting/filing a claim -> Claims");
            r2.replaceConditions(List.of(condition(RuleField.BODY, RuleOperator.CONTAINS, "submit a claim")));
            r2.setTargetCategoryName("Claims");
            r2.setPriority(50);
            ruleRepository.save(r2);
        }
    }

    private RuleCondition condition(RuleField field, RuleOperator operator, String value) {
        RuleCondition condition = new RuleCondition();
        condition.setField(field);
        condition.setOperator(operator);
        condition.setValues(new java.util.ArrayList<>(List.of(value)));
        return condition;
    }

    private void seedCategory(String name, String description) {
        if (!categoryRepository.existsByName(name)) {
            categoryRepository.save(new Category(null, name, description));
        }
    }
}
