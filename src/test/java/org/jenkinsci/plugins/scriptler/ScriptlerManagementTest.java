/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.scriptler;

import org.jenkinsci.plugins.scriptler.config.Parameter;
import java.io.IOException;
import java.io.Writer;
import java.io.FileWriter;
import java.io.File;
import org.jenkinsci.plugins.scriptler.config.Script;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.security.HudsonPrivateSecurityRealm;
import hudson.security.GlobalMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import java.net.URL;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.*;

/**
 *
 * @author Lucie Votypkova
 */
public class ScriptlerManagementTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void doRunScriptPermissionTest() throws Exception{
        ScriptlerManagment scriptler = j.jenkins.getExtensionList(ScriptlerManagment.class).get(0);
        addScript(scriptler, new Script("hello.groovy","hello","user using", null, "println 'hello world'", null, true, null, false), "println 'hello world'");
        addScript(scriptler, new Script("helloAdminOnly.groovy","hello","admin using", null, "println 'hello world'", null, false, null, false), "println 'hello world'");
        scriptler.getConfiguration().setAllowRunScriptPermission(true);
        JenkinsRule.WebClient client = j.createWebClient();
        client.setThrowExceptionOnFailingStatusCode(false);
        assertEquals("In mode everyone can do everything, run script shoudl be allowed ", 200, client.getPage(new URL(j.getURL(), "/scriptler/runScript?id=hello.groovy")).getWebResponse().getStatusCode());
        GlobalMatrixAuthorizationStrategy strategy = new GlobalMatrixAuthorizationStrategy();
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(true, true, null);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("user", "user");
        realm.createAccount("admin", "admin");
        strategy.add(Jenkins.ADMINISTER, "admin");
        strategy.add(ScritplerPluginImpl.RUN_USER_SCRIPTS, "user");
        strategy.add(Jenkins.READ, "user");
        j.jenkins.setAuthorizationStrategy(strategy);
        assertEquals("It should be allowed to run user script for user with execute script permission.", 200, client.login("admin", "admin").getPage(new URL(j.getURL(), "/scriptler/runScript?id=hello.groovy")).getWebResponse().getStatusCode());
        assertEquals(" It should not be allowed to run administer only script for user without administer permission.", 403, client.login("user", "user").getPage(new URL(j.getURL(), "/scriptler/runScript?id=helloAdminOnly.groovy")).getWebResponse().getStatusCode());     
        scriptler.getConfiguration().setAllowRunScriptPermission(false);
        assertEquals("User with execute script permission should not run user script if execution of user script under execute script permission is forbiden.", 403, client.login("user", "user").getPage(new URL(j.getURL(), "/scriptler/runScript?id=hello.groovy")).getWebResponse().getStatusCode());      
        
    }
    
    private void addScript(ScriptlerManagment scriptler, Script script, String content) throws IOException{
        scriptler.getConfiguration().addOrReplace(script);
        File newScriptFile = new File(scriptler.getScriptDirectory(), script.getId());
        Writer writer = new FileWriter(newScriptFile);
        writer.write(content);
        writer.close();
        scriptler.getConfiguration().save();
    }
    
    @Test
    public void doRunPermissionTest() throws Exception{
        ScriptlerManagment scriptler = j.jenkins.getExtensionList(ScriptlerManagment.class).get(0);
        addScript(scriptler, new Script("hello.groovy","hello","user using", null, "println 'hello world'", null, true, new Parameter[0], false), "println 'hello world'");
        addScript(scriptler, new Script("helloAdminOnly.groovy","hello","admin using", null, "println 'hello world'", null, false, new Parameter[0], false), "println 'hello world'");
        scriptler.getConfiguration().setAllowRunScriptPermission(true);
        JenkinsRule.WebClient client = j.createWebClient();
        client.setThrowExceptionOnFailingStatusCode(false);
        assertEquals("In mode everyone can do everything, run script shoudl be allowed.", 200, client.getPage(new URL(j.getURL(), "/scriptler/run/hello.groovy")).getWebResponse().getStatusCode());
        GlobalMatrixAuthorizationStrategy strategy = new GlobalMatrixAuthorizationStrategy();
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(true, true, null);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("user", "user");
        realm.createAccount("admin", "admin");
        strategy.add(Jenkins.ADMINISTER, "admin");
        strategy.add(ScritplerPluginImpl.RUN_USER_SCRIPTS, "user");
        strategy.add(Jenkins.READ, "user");
        j.jenkins.setAuthorizationStrategy(strategy);
        assertEquals("User with admin permission should be able to run any user script.", 200, client.login("admin", "admin").getPage(new URL(j.getURL(), "/scriptler/run/hello.groovy")).getWebResponse().getStatusCode());
        assertEquals("User with admin permission should be able to run any administer script.", 200, client.login("admin", "admin").getPage(new URL(j.getURL(), "/scriptler/run/helloAdminOnly.groovy")).getWebResponse().getStatusCode());     
        assertEquals("User with execute script permission should be able to run user script.", 200, client.login("user", "user").getPage(new URL(j.getURL(), "/scriptler/run/hello.groovy")).getWebResponse().getStatusCode());     
        assertEquals("User with only execute script permission shoud not be able to run administer script.", 403, client.login("user", "user").getPage(new URL(j.getURL(), "/scriptler/run/helloAdminOnly.groovy")).getWebResponse().getStatusCode());     
        scriptler.getConfiguration().setAllowRunScriptPermission(false);
        assertEquals("User with execute script permission should not able to run user script if it is forbiden.", 403, client.login("user", "user").getPage(new URL(j.getURL(), "/scriptler/run/hello.groovy")).getWebResponse().getStatusCode());      
     
    }
    
    @Test
    public void doTriggerScriptPermissionTest() throws Exception{
        ScriptlerManagment scriptler = j.jenkins.getExtensionList(ScriptlerManagment.class).get(0);
        addScript(scriptler, new Script("hello.groovy","hello","user using", null, "println 'hello world'", null, true, null, false), "println 'hello world'");
        addScript(scriptler, new Script("helloAdminOnly.groovy","hello","admin using", null, "println 'hello world'", null, false, null, false), "println 'hello world'");
        scriptler.getConfiguration().setAllowRunScriptPermission(true);
        JenkinsRule.WebClient client = j.createWebClient();
        client.setThrowExceptionOnFailingStatusCode(false);
        HtmlPage page = client.getPage(new URL(j.getURL(), "/scriptler/runScript?id=hello.groovy"));
        HtmlElement element = (HtmlElement) page.getByXPath("//span[@name='Submit']").get(0);
        assertEquals("In mode everyone can do everything, run script shoudl be allowed ", 200, element.click().getWebResponse().getStatusCode());
        GlobalMatrixAuthorizationStrategy strategy = new GlobalMatrixAuthorizationStrategy();
        HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(true, true, null);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("user", "user");
        realm.createAccount("admin", "admin");
        strategy.add(Jenkins.ADMINISTER, "admin");
        strategy.add(ScritplerPluginImpl.RUN_USER_SCRIPTS, "user");
        strategy.add(Jenkins.READ, "user");
        j.jenkins.setAuthorizationStrategy(strategy);
        
        page = client.login("admin", "admin").getPage(new URL(j.getURL(), "/scriptler/runScript?id=hello.groovy"));
        element = (HtmlElement) page.getByXPath("//span[@name='Submit']").get(0);
        assertEquals("User with admin permission should be able to trigger any user script.", 200, element.click().getWebResponse().getStatusCode());
        
        page = client.login("user", "user").getPage(new URL(j.getURL(), "/scriptler/runScript?id=hello.groovy"));
        element = setForm(page);
        assertEquals("User with execute script permission should be able to trigger user script.", 200, element.click().getWebResponse().getStatusCode());     
        
        scriptler.getConfiguration().setAllowRunScriptPermission(false);
        page = client.login("user", "user").getPage(new URL(j.getURL(), "/scriptler/runScript?id=hello.groovy"));
        element = setForm(page);
        assertEquals("User with execute script permission should not able to run user script if it is forbiden.", 403, element.click().getWebResponse().getStatusCode());      
        scriptler.getConfiguration().setAllowRunScriptPermission(true);

        page = client.login("user", "user").getPage(new URL(j.getURL(), "/scriptler/runScript?id=helloAdminOnly.groovy"));
        element = setForm(page);
        assertEquals("User with only execute script permission shoud not be able to run administer script.", 403, element.click().getWebResponse().getStatusCode());     
        
        page = client.login("admin", "admin").getPage(new URL(j.getURL(), "/scriptler/runScript?id=helloAdminOnly.groovy"));
        element = setForm(page);
        assertEquals("User with admin permission should be able to run any administer script.", 200, element.click().getWebResponse().getStatusCode());     
        
        
    }
    
    private HtmlElement setForm(HtmlPage page){
       HtmlElement form = page.createElement("form");
       form.setAttribute("action", "triggerScript");
       form.setAttribute("method", "post");
       HtmlElement input = form.appendChildIfNoneExists("input");
       input.setAttribute("type", "text");
       input.setAttribute("name", "script");
       input.setAttribute("value", "println 'hello world'"); 
       HtmlElement node = form.appendChildIfNoneExists("input");
       node.setAttribute("type", "text");
       node.setAttribute("name", "node");
       node.setAttribute("value", "(master)"); 
       HtmlElement submit = form.appendChildIfNoneExists("input");
       node.setAttribute("type", "submit");
       node.setAttribute("value", "Run"); 
       return submit;
    }
}
