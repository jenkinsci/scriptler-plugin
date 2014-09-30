/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.scriptler.builder;
import org.jenkinsci.plugins.scriptler.config.Script;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.scriptler.ScriptlerManagment;
import hudson.model.User;
import org.acegisecurity.context.SecurityContextHolder;
import hudson.model.AbstractProject;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import org.jenkinsci.plugins.scriptler.ScritplerPluginImpl;
import jenkins.model.Jenkins;
import hudson.security.HudsonPrivateSecurityRealm;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;
/**
 *
 * @author Lucie Votypkova
 */
public class ScriptlerBuilderTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void testIsApplicable() throws Exception{
        ScriptlerManagment scriptler = Jenkins.getInstance().getExtensionList(ScriptlerManagment.class).get(0);
        ScriptlerBuilder.DescriptorImpl builder= (ScriptlerBuilder.DescriptorImpl) j.jenkins.getBuilder(ScriptlerBuilder.class.getSimpleName());
        assertTrue("Builder should be always available in mode everyone can do everything.",builder.isApplicable(AbstractProject.class));
        GlobalMatrixAuthorizationStrategy strategy = new GlobalMatrixAuthorizationStrategy();
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(true, true, null);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("user", "user");
        User admin = realm.createAccount("admin", "admin");
        strategy.add(Jenkins.ADMINISTER, "admin");
        strategy.add(Jenkins.READ, "user");
        j.jenkins.setAuthorizationStrategy(strategy);
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        scriptler.getConfiguration().setAllowRunScriptPermission(true);
        assertFalse("Builder should not be available for user which does not have execute script permission.",builder.isApplicable(AbstractProject.class));
        SecurityContextHolder.getContext().setAuthentication(admin.impersonate());
        assertTrue("Builder should be available for user with administer permission.",builder.isApplicable(AbstractProject.class));
        strategy.add(ScritplerPluginImpl.RUN_USER_SCRIPTS, "user");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        assertTrue("Builder should be available for user which has execute script permission.",builder.isApplicable(AbstractProject.class));
        scriptler.getConfiguration().setAllowRunScriptPermission(false);
        assertFalse("Builder should not be available for user with execute script permission, if this permission is not enabled for non admin scripts.",builder.isApplicable(AbstractProject.class));
        SecurityContextHolder.getContext().setAuthentication(admin.impersonate());
        assertTrue("Builder should be available for user which has execute script permission.",builder.isApplicable(AbstractProject.class));
        
    }
    
    @Test
    public void testPermissionNewInstance() throws Exception{
        ScriptlerManagment scriptler = j.jenkins.getExtensionList(ScriptlerManagment.class).get(0);
        scriptler.getConfiguration().addOrReplace(new Script("hello.groovy","hello","user using", null, "println 'hello world'", null, true, null, false));
        scriptler.getConfiguration().addOrReplace(new Script("helloAdminOnly.groovy","hello","admin using", null, "println 'hello world'", null, false, null, false));
        scriptler.getConfiguration().setAllowRunScriptPermission(true);
        scriptler.getConfiguration().save();
        ScriptlerBuilder.DescriptorImpl builder= (ScriptlerBuilder.DescriptorImpl) j.jenkins.getBuilder(ScriptlerBuilder.class.getSimpleName());
        JSONObject object = new JSONObject();
        object.put("scriptlerScriptId", "hello.groovy");
        object.put("propagateParams",false);
        assertNotNull("User scripts should be always available in mode everybody can do everything.", builder.newInstance(null, object).getScriptId());
        GlobalMatrixAuthorizationStrategy strategy = new GlobalMatrixAuthorizationStrategy();
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(true, true, null);
        j.jenkins.setSecurityRealm(realm);
        User user = realm.createAccount("user", "user");
        User admin = realm.createAccount("admin", "admin");
        strategy.add(Jenkins.ADMINISTER, "admin");
        strategy.add(Jenkins.READ, "user");
        j.jenkins.setAuthorizationStrategy(strategy);
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        assertNull("User without excute script permission should not add scriptler build step into job configuration.", builder.newInstance(null, object).getScriptId());
        SecurityContextHolder.getContext().setAuthentication(admin.impersonate());
        assertNotNull("User with admin permission should be able to add scriptler build step into job configuration.", builder.newInstance(null, object).getScriptId());
        strategy.add(ScritplerPluginImpl.RUN_USER_SCRIPTS, "user");
        SecurityContextHolder.getContext().setAuthentication(user.impersonate());
        assertNotNull("User with execute scripts permission should be able to add scriptler build step into job configuration", builder.newInstance(null, object).getScriptId());
        scriptler.getConfiguration().setAllowRunScriptPermission(false);
        assertNull("User with execute scripts permission should not be able to add scriptler build step into job configuration, if excute script permission is forbiden in scriptler configuration", builder.newInstance(null, object).getScriptId());
        scriptler.getConfiguration().setAllowRunScriptPermission(true);
        object.put("scriptlerScriptId", "helloAdminOnly.groovy");
        assertNull("User with execute scripts permission should not be able to add administer script into job configuration", builder.newInstance(null, object).getScriptId());
        SecurityContextHolder.getContext().setAuthentication(admin.impersonate());
        assertNotNull("User with administer permission should be able to add administer script into job configuration", object);
    }
}
