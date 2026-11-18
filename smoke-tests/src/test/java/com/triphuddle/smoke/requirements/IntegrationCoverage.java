package com.triphuddle.smoke.requirements;

import com.triphuddle.smoke.support.CoveredBy;
import com.triphuddle.smoke.support.Requirement;

/**
 * The requirements integration tests prove, named so the traceability check accounts for them. A smoke test may
 * prove one again end to end; each entry stays as long as its integration test does.
 */
final class IntegrationCoverage {

    private IntegrationCoverage() {
    }

    @Requirement("CMN-1")
    @CoveredBy({"common:BaseRecordsTest", "api-platform:CountryApiIT"})
    void cmn1() {
    }

    @Requirement("CMN-2")
    @CoveredBy({"common:FirebaseJwtGrantedAuthoritiesConverterTest", "common:SecurityUtilsTest"})
    void cmn2() {
    }

    @Requirement("CMN-3")
    @CoveredBy({"common:GlobalExceptionHandlerTest"})
    void cmn3() {
    }

    @Requirement("CMN-4")
    @CoveredBy({"common:PaginationFilterTest", "common:PageCursorTest"})
    void cmn4() {
    }

    @Requirement("CMN-5")
    @CoveredBy({"api-platform:CountryApiIT", "api-group:GroupInvitationApiIT"})
    void cmn5() {
    }

    @Requirement("MQ-1")
    @CoveredBy({"amqp:MessageSerializationTest", "amqp:RabbitMQConfigTest"})
    void mq1() {
    }

    @Requirement("MQ-2")
    @CoveredBy({"amqp:RabbitMQConfigTest"})
    void mq2() {
    }

    @Requirement("MQ-3")
    @CoveredBy({"amqp:RabbitMQIT", "amqp:RabbitMQConfigTest"})
    void mq3() {
    }

    @Requirement("MQ-4")
    @CoveredBy({"amqp:RabbitMQIT", "notification:NotificationIT"})
    void mq4() {
    }

    @Requirement("MQ-5")
    @CoveredBy({"amqp:MessageSerializationTest"})
    void mq5() {
    }

    @Requirement("STR-1")
    @CoveredBy({"stripe:StripeWebhooksAndConfigTest", "api-payment:WebhookInboxIT"})
    void str1() {
    }

    @Requirement("STR-2")
    @CoveredBy({"stripe:StripeClientsTest"})
    void str2() {
    }

    @Requirement("STR-3")
    @CoveredBy({"stripe:StripeWebhooksAndConfigTest"})
    void str3() {
    }

    @Requirement("USR-1")
    @CoveredBy({"api-user:UserApiIT"})
    void usr1() {
    }

    @Requirement("USR-2")
    @CoveredBy({"api-user:UserServiceTest", "api-user:FirebaseRoleSyncJobTest"})
    void usr2() {
    }

    @Requirement("USR-3")
    @CoveredBy({"api-user:UserApiIT", "api-user:UserServiceTest"})
    void usr3() {
    }

    @Requirement("USR-4")
    @CoveredBy({"api-user:UserApiIT", "api-user:UserProfileApiIT"})
    void usr4() {
    }

    @Requirement("USR-5")
    @CoveredBy({"api-user:UserDeviceServiceTest", "api-user:OwnDataApiIT"})
    void usr5() {
    }

    @Requirement("USR-6")
    @CoveredBy({"api-user:UserTravelDocumentServiceTest", "api-user:OwnDataApiIT"})
    void usr6() {
    }

    @Requirement("USR-7")
    @CoveredBy({"api-user:UserServiceTest", "api-user:UserApiIT"})
    void usr7() {
    }

    @Requirement("USR-8")
    @CoveredBy({"api-user:EmailingListenersTest"})
    void usr8() {
    }

    @Requirement("GRP-1")
    @CoveredBy({"api-group:GroupApiIT", "api-group:GroupMembershipServiceTest"})
    void grp1() {
    }

    @Requirement("GRP-2")
    @CoveredBy({"api-group:GroupInvitationApiIT", "api-group:GroupMembershipApiIT"})
    void grp2() {
    }

    @Requirement("GRP-3")
    @CoveredBy({"api-group:GroupInvitationApiIT"})
    void grp3() {
    }

    @Requirement("GRP-4")
    @CoveredBy({"api-group:GroupInvitationApiIT", "api-group:GroupInvitationServiceTest"})
    void grp4() {
    }

    @Requirement("GRP-5")
    @CoveredBy({"api-group:GroupApiIT", "api-group:GroupMembershipApiIT"})
    void grp5() {
    }

    @Requirement("LIC-1")
    @CoveredBy({"api-license:LicenseCatalogIT"})
    void lic1() {
    }

    @Requirement("LIC-2")
    @CoveredBy({"api-license:SubscriptionIT", "api-license:LicenseCatalogIT"})
    void lic2() {
    }

    @Requirement("LIC-3")
    @CoveredBy({"api-license:PromotionIT", "api-license:PricingRulesTest", "api-license:PromotionRulesIT"})
    void lic3() {
    }

    @Requirement("LIC-4")
    @CoveredBy({"api-license:SubscriptionIT", "api-license:SubscriptionRulesIT"})
    void lic4() {
    }

    @Requirement("LIC-5")
    @CoveredBy({"api-license:SubscriptionIT", "api-license:LicensePoliciesTest", "api-license:SubscriptionRulesIT"})
    void lic5() {
    }

    @Requirement("LIC-6")
    @CoveredBy({"api-license:PaymentsAndJobsIT", "api-license:SubscriptionRulesIT",
            "api-license:SubscriptionServiceTest"})
    void lic6() {
    }

    @Requirement("LIC-7")
    @CoveredBy({"api-license:SubscriptionIT"})
    void lic7() {
    }

    @Requirement("LIC-8")
    @CoveredBy({"api-license:LicenseEventPublisherTest", "api-license:SubscriptionIT"})
    void lic8() {
    }

    @Requirement("PAY-1")
    @CoveredBy({"api-payment:WebhookInboxIT"})
    void pay1() {
    }

    @Requirement("PAY-2")
    @CoveredBy({"api-payment:CheckoutIT", "api-payment:PaymentRulesIT"})
    void pay2() {
    }

    @Requirement("PAY-3")
    @CoveredBy({"api-payment:BillingFlowsIT"})
    void pay3() {
    }

    @Requirement("PAY-4")
    @CoveredBy({"api-payment:BillingFlowsIT", "api-payment:PaymentRulesIT"})
    void pay4() {
    }

    @Requirement("PAY-5")
    @CoveredBy({"api-payment:InvoicesIT", "api-payment:PaymentRulesIT"})
    void pay5() {
    }

    @Requirement("PAY-6")
    @CoveredBy({"api-payment:ReconciliationAndCatalogIT", "api-payment:PaymentRulesIT"})
    void pay6() {
    }

    @Requirement("PAY-7")
    @CoveredBy({"api-payment:ReconciliationAndCatalogIT"})
    void pay7() {
    }

    @Requirement("PAY-8")
    @CoveredBy({"api-payment:PaymentListenersAndJobsTest"})
    void pay8() {
    }

    @Requirement("PLT-1")
    @CoveredBy({"api-platform:CountryApiIT", "api-platform:SeedMigrationIT"})
    void plt1() {
    }

    @Requirement("PLT-4")
    @CoveredBy({"api-platform:FeatureFlagIT"})
    void plt4() {
    }

    @Requirement("CHT-1")
    @CoveredBy({"api-chat:PrivateChatIT", "api-chat:GroupChatIT"})
    void cht1() {
    }

    @Requirement("CHT-2")
    @CoveredBy({"api-chat:PrivateChatIT", "api-chat:AttachmentsIT", "api-chat:ChatRulesIT"})
    void cht2() {
    }

    @Requirement("CHT-3")
    @CoveredBy({"api-chat:PrivateChatIT", "api-chat:GroupChatIT"})
    void cht3() {
    }

    @Requirement("CHT-4")
    @CoveredBy({"api-chat:GroupChatIT", "api-chat:ChatRulesIT"})
    void cht4() {
    }

    @Requirement("CHT-5")
    @CoveredBy({"api-chat:ModerationIT"})
    void cht5() {
    }

    @Requirement("CHT-6")
    @CoveredBy({"api-chat:SupportTicketIT", "api-chat:ChatRulesIT"})
    void cht6() {
    }

    @Requirement("CHT-7")
    @CoveredBy({"api-chat:SupportTicketIT"})
    void cht7() {
    }

    @Requirement("DST-1")
    @CoveredBy({"api-destination:PlacesIT"})
    void dst1() {
    }

    @Requirement("DST-2")
    @CoveredBy({"api-destination:PlacesIT"})
    void dst2() {
    }

    @Requirement("DST-3")
    @CoveredBy({"api-destination:ImportsAndCategoriesIT", "api-destination:WikidataClientTest",
            "api-destination:BucketAndFullImportIT"})
    void dst3() {
    }

    @Requirement("DST-4")
    @CoveredBy({"api-destination:PlacesIT"})
    void dst4() {
    }

    @Requirement("DST-5")
    @CoveredBy({"api-destination:ContentIT"})
    void dst5() {
    }

    @Requirement("DST-6")
    @CoveredBy({"api-destination:TripsIT"})
    void dst6() {
    }

    @Requirement("DST-7")
    @CoveredBy({"api-destination:VisasIT", "api-destination:BucketAndFullImportIT"})
    void dst7() {
    }

    @Requirement("DST-8")
    @CoveredBy({"api-destination:SocialAndSemanticIT"})
    void dst8() {
    }

    @Requirement("DST-9")
    @CoveredBy({"api-destination:SocialAndSemanticIT"})
    void dst9() {
    }

    @Requirement("DST-10")
    @CoveredBy({"api-destination:SocialAndSemanticIT", "api-destination:VisasIT"})
    void dst10() {
    }

    @Requirement("NTF-1")
    @CoveredBy({"notification:EmailServiceTest"})
    void ntf1() {
    }

    @Requirement("NTF-2")
    @CoveredBy({"notification:NotificationIT"})
    void ntf2() {
    }

    @Requirement("NTF-3")
    @CoveredBy({"notification:NotificationIT", "notification:PushServiceTest"})
    void ntf3() {
    }

    @Requirement("NTF-4")
    @CoveredBy({"notification:PushServiceTest"})
    void ntf4() {
    }

    @Requirement("NTF-5")
    @CoveredBy({"notification:NotificationIT"})
    void ntf5() {
    }

    @Requirement("AI-1")
    @CoveredBy({"openai:OpenAiApiClientTest"})
    void ai1() {
    }

    @Requirement("AI-2")
    @CoveredBy({"openai:AiJobRunnerTest"})
    void ai2() {
    }

    @Requirement("AI-3")
    @CoveredBy({"openai:AiJobRunnerTest", "openai:OpenAiApiClientTest"})
    void ai3() {
    }

    @Requirement("AI-4")
    @CoveredBy({"openai:AiJobRunnerTest", "openai:AiWorkerIT"})
    void ai4() {
    }

    @Requirement("AI-5")
    @CoveredBy({"openai:AiJobRunnerTest"})
    void ai5() {
    }

    @Requirement("AI-6")
    @CoveredBy({"openai:AiWorkerIT"})
    void ai6() {
    }

    @Requirement("GW-2")
    @CoveredBy({"api-gateway:GatewayIT"})
    void gw2() {
    }

    @Requirement("GW-3")
    @CoveredBy({"api-gateway:GatewayIT"})
    void gw3() {
    }

    @Requirement("GW-4")
    @CoveredBy({"api-gateway:GatewayIT"})
    void gw4() {
    }

    @Requirement("GW-5")
    @CoveredBy({"api-gateway:GatewayIT"})
    void gw5() {
    }

    @Requirement("EUR-1")
    @CoveredBy({"eureka-server:EurekaServerIT"})
    void eur1() {
    }

    @Requirement("EUR-2")
    @CoveredBy({"eureka-server:EurekaServerIT"})
    void eur2() {
    }

    @Requirement("NFR-4")
    @CoveredBy({"amqp:RabbitMQIT", "amqp:RabbitMQMessageProducerTest", "notification:NotificationIT"})
    void nfr4() {
    }

    @Requirement("NFR-5")
    @CoveredBy({"api-chat:SupportTicketIT", "api-destination:ImportsAndCategoriesIT"})
    void nfr5() {
    }

    @Requirement("NFR-6")
    @CoveredBy({"api-destination:ContentIT", "api-destination:SocialAndSemanticIT"})
    void nfr6() {
    }

    @Requirement("NFR-7")
    @CoveredBy({"api-platform:CountryApiIT", "common:BaseRecordsTest"})
    void nfr7() {
    }

    @Requirement("NFR-8")
    @CoveredBy({"api-destination:PlacesIT", "api-destination:VisasIT", "api-chat:GroupChatIT"})
    void nfr8() {
    }

    @Requirement("NFR-10")
    @CoveredBy({"api-destination:ContentIT", "api-destination:VisasIT"})
    void nfr10() {
    }

    @Requirement("NFR-11")
    @CoveredBy({"api-chat:PrivateChatIT", "api-chat:SupportTicketIT", "common:PageCursorTest"})
    void nfr11() {
    }
}
