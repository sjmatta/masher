package com.stephenmatta.ics;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

public class IntegrationTest {

    private App app;
    private Stack stack;

    @BeforeEach
    public void setup() {
        app = new App();

        app.getNode().setContext("icsUrls", "https://example.com/calendar.ics");
        app.getNode().setContext("calendarName", "my-calendar");
        app.getNode().setContext("domainName", "example.com");
        app.getNode().setContext("subdomainName", "api.example.com");

        Environment awsEnv = Environment.builder().account("123456789012").region("us-east-1")
            .build();

        stack = new MasherStack(app, "TestMasherStack", StackProps.builder().env(awsEnv).build());

        app.synth();
    }

    @Test
    public void testLambdaCreated() {
        Template template = Template.fromStack(stack);

        // Assert that a Lambda function with the correct properties exists
        template.hasResourceProperties("AWS::Lambda::Function",
            Map.of("Handler", "com.stephenmatta.ics.CombineICSFunction::handleRequest", "Runtime",
                "java21"));

        // Assert that the Lambda function has the expected environment variables
        template.hasResourceProperties("AWS::Lambda::Function", Match.objectLike(
            Map.of("Environment", Map.of("Variables", Map.of("ICS_URLS", Match.anyValue())))));
    }

    @Test
    public void testApiGatewayCreated() {
        Template template = Template.fromStack(stack);

        // Assert that an API Gateway with the correct properties exists
        template.hasResourceProperties("AWS::ApiGateway::RestApi",
            Map.of("Name", "Combine ICS Service", "Description",
                "This service combines ICS files from multiple URLs into one."));
    }

    @Test
    public void testRoute53RecordCreated() {
        Template template = Template.fromStack(stack);

        // Assert that a Route53 ARecord exists
        template.hasResourceProperties("AWS::Route53::RecordSet",
            Map.of("Name", "api.example.com.", "Type", "A"));
    }

    @Test
    public void testCertificateCreated() {
        Template template = Template.fromStack(stack);

        // Assert that a Certificate is created for the custom domain
        template.hasResourceProperties("AWS::CertificateManager::Certificate",
            Map.of("DomainName", "api.example.com"));
    }

    @AfterEach
    public void tearDown() {
        app = null;
        stack = null;
    }
}
