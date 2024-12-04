package org.jenkinsci.plugins.scriptler;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;

public final class ScriptlerPermissions {
    public static final PermissionGroup SCRIPTLER_PERMISSIONS =
            new PermissionGroup(ScriptlerManagement.class, Messages._permissons_title());

    public static final Permission CONFIGURE = new Permission(
            SCRIPTLER_PERMISSIONS,
            "Configure",
            Messages._permissons_configure_description(),
            Jenkins.ADMINISTER,
            PermissionScope.JENKINS);

    public static final Permission RUN_SCRIPTS = new Permission(
            SCRIPTLER_PERMISSIONS,
            "RunScripts",
            Messages._permissons_runScript_description(),
            CONFIGURE,
            PermissionScope.JENKINS);

    public static final Permission BYPASS_APPROVAL = Jenkins.ADMINISTER;

    private ScriptlerPermissions() {}

    @SuppressFBWarnings(
            value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT",
            justification = "getEnabled return value discarded")
    @Initializer(after = InitMilestone.PLUGINS_STARTED, before = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void ensurePermissionsRegistered() {
        CONFIGURE.getEnabled();
        RUN_SCRIPTS.getEnabled();
    }
}
