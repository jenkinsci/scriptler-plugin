package org.jenkinsci.plugins.scriptler;

import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;

public class ScriptlerPermissions {
    public static final PermissionGroup SCRIPTLER_PERMISSIONS = new PermissionGroup(ScriptlerManagement.class, Messages._permissons_title());

    public static final Permission CONFIGURE = new Permission(
            SCRIPTLER_PERMISSIONS, "Configure",
            Messages._permissons_configure_description(), Jenkins.RUN_SCRIPTS,
            PermissionScope.JENKINS
    );

    public static final Permission RUN_SCRIPTS = new Permission(
            SCRIPTLER_PERMISSIONS, "RunScripts",
            Messages._permissons_runScript_description(), Jenkins.RUN_SCRIPTS,
            PermissionScope.JENKINS
    );
}
