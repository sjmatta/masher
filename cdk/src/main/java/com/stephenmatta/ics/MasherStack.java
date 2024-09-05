package com.stephenmatta.ics;

import java.util.Map;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.DomainNameOptions;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.ApiGateway;
import software.constructs.Construct;

public class MasherStack extends Stack {

    private static final String LAMBDA_JAR_PATH = "../lambda/target/masher-1.0-SNAPSHOT.jar";
    private static final int LAMBDA_MEMORY_MB = 512;
    private static final int LAMBDA_TIMEOUT_SECONDS = 10;

    public MasherStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        String icsUrls = getContextValue("icsUrls");
        String calendarName = getContextValue("calendarName");
        String domainName = getContextValue("domainName");
        String subdomainName = getContextValue("subdomainName");

        Function combineIcsLambda = createCombineIcsLambda(icsUrls);
        RestApi api = createApiGateway(calendarName, combineIcsLambda);
        IHostedZone hostedZone = lookupHostedZone(domainName);
        Certificate certificate = createCertificate(hostedZone, subdomainName);

        addCustomDomainToApi(api, certificate, subdomainName);
        createCnameRecord(hostedZone, api);
    }

    private String getContextValue(String contextKey) {
        Object contextValue = this.getNode().tryGetContext(contextKey);
        if (!(contextValue instanceof String stringValue) || stringValue.isEmpty()) {
            throw new IllegalArgumentException(
                "Context '" + contextKey + "' is required but not provided.");
        }
        return stringValue;
    }

    private Function createCombineIcsLambda(String icsUrls) {
        return Function.Builder.create(this, "CombineICSFunction")
            .runtime(Runtime.JAVA_21)
            .handler("com.stephenmatta.ics.CombineICSFunction::handleRequest")
            .code(Code.fromAsset(LAMBDA_JAR_PATH))
            .memorySize(LAMBDA_MEMORY_MB)
            .timeout(Duration.seconds(LAMBDA_TIMEOUT_SECONDS))
            .environment(Map.of("ICS_URLS", icsUrls))
            .build();
    }

    private RestApi createApiGateway(String calendarName, Function lambdaFunction) {
        RestApi api = RestApi.Builder.create(this, "CombineICSApi")
            .restApiName("Combine ICS Service")
            .description("This service combines ICS files from multiple URLs into one.")
            .build();

        api.getRoot().addResource(calendarName + ".ics")
            .addMethod("GET", new LambdaIntegration(lambdaFunction));

        return api;
    }

    private IHostedZone lookupHostedZone(String domainName) {
        return software.amazon.awscdk.services.route53.HostedZone.fromLookup(this, "HostedZone",
            HostedZoneProviderProps.builder()
                .domainName(domainName)
                .build());
    }

    private Certificate createCertificate(IHostedZone hostedZone, String domainName) {
        return Certificate.Builder.create(this, "ApiCertificate")
            .domainName(domainName)
            .validation(CertificateValidation.fromDns(hostedZone))
            .build();
    }

    private void addCustomDomainToApi(RestApi api, Certificate certificate, String domainName) {
        api.addDomainName("ApiCustomDomain",
            DomainNameOptions.builder()
                .domainName(domainName)
                .certificate(certificate)
                .build());
    }

    private void createCnameRecord(IHostedZone hostedZone, RestApi api) {
        ARecord.Builder.create(this, "ApiCnameRecord")
            .zone(hostedZone)
            .recordName("api")  // Creates CNAME for api.matta.family
            .target(RecordTarget.fromAlias(new ApiGateway(api)))  // Alias target to API Gateway
            .build();
    }
}
