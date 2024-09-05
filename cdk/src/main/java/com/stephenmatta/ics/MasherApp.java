package com.stephenmatta.ics;

import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class MasherApp {

    public static void main(final String[] args) {
        software.amazon.awscdk.App app = new software.amazon.awscdk.App();

        new MasherStack(app, "MasherStack", StackProps.builder()
            .env(Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .build())
            .build());

        app.synth();
    }
}

