package org.jenkinsci.plugins.scriptler.testharness;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.support.TypeBasedParameterResolver;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

/**
 * JUnit 5 extension providing {@link RestartableJenkinsRule} integration.
 */
public class RestartableJenkinsExtension extends TypeBasedParameterResolver<RestartableJenkinsRule>
        implements AfterEachCallback {

    private static final String KEY = "jenkins-instance";
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(RestartableJenkinsExtension.class);

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        final RestartableJenkinsRule rule = context.getStore(NAMESPACE).remove(KEY, RestartableJenkinsRule.class);
        if (rule == null) {
            return;
        }

        try {
            rule.apply(
                            new Statement() {
                                @Override
                                public void evaluate() {
                                    // body is already evaluated, nothing more to do here
                                }
                            },
                            new FrameworkMethod(context.getRequiredTestMethod()),
                            context.getRequiredTestInstance())
                    .evaluate();
        } catch (Exception | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new Exception(t);
        }
    }

    @Override
    public RestartableJenkinsRule resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return extensionContext
                .getStore(NAMESPACE)
                .getOrComputeIfAbsent(KEY, key -> new RestartableJenkinsRule(), RestartableJenkinsRule.class);
    }
}
