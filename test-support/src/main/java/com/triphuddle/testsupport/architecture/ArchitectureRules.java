package com.triphuddle.testsupport.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.triphuddle.common.entity.BaseJpaEntity;
import com.triphuddle.common.security.PublicEndpoint;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Rules every service follows. A service runs them all with
 * {@code @ArchTest static final ArchTests rules = ArchTests.in(ArchitectureRules.class);}.
 */
public final class ArchitectureRules {

    private static final List<Class<? extends Annotation>> MAPPINGS = List.of(GetMapping.class, PostMapping.class,
            PutMapping.class, PatchMapping.class, DeleteMapping.class, RequestMapping.class);

    private static final DescribedPredicate<JavaMethod> ENDPOINTS = DescribedPredicate.describe(
            "public request-mapped methods of @RestController classes",
            method -> method.getOwner().isAnnotatedWith(RestController.class)
                    && method.getModifiers().contains(JavaModifier.PUBLIC)
                    && MAPPINGS.stream().anyMatch(method::isAnnotatedWith));

    @ArchTest
    public static final ArchRule ENDPOINTS_ARE_AUTHORIZED = methods()
            .that(ENDPOINTS)
            .should().beAnnotatedWith(PreAuthorize.class)
            .orShould().beAnnotatedWith(PublicEndpoint.class)
            .because("an endpoint without @PreAuthorize is open to anyone the filter chain lets through, so being "
                    + "open must be said with @PublicEndpoint")
            // Workers such as notification and openai have no endpoints at all.
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule CONTROLLERS_DO_NOT_TOUCH_REPOSITORIES = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAssignableTo(Repository.class)
            .because("controllers go through services, where the rules and transactions are")
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule SERVICES_DO_NOT_DEPEND_ON_CONTROLLERS = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().areAnnotatedWith(RestController.class)
            .allowEmptyShould(true);

    @ArchTest
    public static final ArchRule ENTITIES_EXTEND_THE_BASE_ENTITY = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().beAssignableTo(BaseJpaEntity.class)
            .because("every record has an ID, audit timestamps and soft disable and delete")
            .allowEmptyShould(true);

    // Beans come through constructors; configuration values in @Value fields are fine.
    @ArchTest
    public static final ArchRule NO_FIELD_INJECTION = noFields()
            .should().beAnnotatedWith(Autowired.class)
            .orShould().beAnnotatedWith("jakarta.inject.Inject")
            .because("a bean injected into a field hides a dependency the constructor should declare")
            .allowEmptyShould(true);

    private ArchitectureRules() {
    }
}
