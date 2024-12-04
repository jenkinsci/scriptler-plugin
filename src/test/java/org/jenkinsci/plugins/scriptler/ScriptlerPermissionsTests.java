package org.jenkinsci.plugins.scriptler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.security.SecurityRealm;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@WithJenkinsConfiguredWithCode
class ScriptlerPermissionsTests {
    @ConfiguredWithCode("/casc.yaml")
    @Test
    void permissionsAreAvailableOnStartup(JenkinsConfiguredWithCodeRule rule) throws Exception {
        SecurityRealm realm = rule.createDummySecurityRealm();
        rule.jenkins.setSecurityRealm(realm);

        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken("user", "user");
        Authentication a = realm.getSecurityComponents().manager2.authenticate(authRequest);
        assertTrue(rule.jenkins.hasPermission2(a, ScriptlerPermissions.CONFIGURE));
    }
}
