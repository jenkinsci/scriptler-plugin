package org.jenkinsci.plugins.scriptler.tokenmacro;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.ChannelClosedException;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptler.Messages;
import org.jenkinsci.plugins.scriptler.config.Script;
import org.jenkinsci.plugins.scriptler.util.ControllerGroovyScript;
import org.jenkinsci.plugins.scriptler.util.GroovyScript;
import org.jenkinsci.plugins.scriptler.util.ScriptHelper;
import org.jenkinsci.plugins.tokenmacro.DataBoundTokenMacro;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;

import java.io.IOException;
import java.util.Collections;

/**
 * TokenMacro that allows the execution of a scriptler script an any arbitrary location supporting TokenMacros e.g. <code>${SCRIPTLER, scriptId="superscript.groovy"}</code>
 *
 * @author Dominik Bartholdi (imod)
 */
@Extension
public class ScriptlerTokenMacro extends DataBoundTokenMacro {

    @Parameter
    public String scriptId;

    @Override
    public String evaluate(AbstractBuild<?, ?> context, TaskListener listener, String macroName) throws MacroEvaluationException, IOException, InterruptedException {

        final Script script = ScriptHelper.getScript(scriptId, true);
        if (script == null) {
            throw new MacroEvaluationException(Messages.tokenmacro_ScriptDoesNotExist(scriptId));
        } else if (!script.nonAdministerUsing) {
            listener.getLogger().println(Messages.tokenmacro_AdminScriptOnly(scriptId));
            throw new MacroEvaluationException(Messages.tokenmacro_AdminScriptOnly(scriptId));
        }

        Object output;
        if (script.onlyMaster || Jenkins.get().equals(context.getBuiltOn())) {
            output = FilePath.localChannel.call(new ControllerGroovyScript(script.script, Collections.emptyList(), true, listener, null, context));
        } else {
            FilePath remoteFilePath = context.getWorkspace();
            if (remoteFilePath == null) {
                // the remote node has apparently disconnected, so we can't run our script
                throw new ChannelClosedException((Channel) null, null);
            }
            output = remoteFilePath.getChannel().call(new GroovyScript(script.script, Collections.emptyList(), true, listener));
        }

        return output != null ? output.toString() : "";
    }

    @Override
    public boolean acceptsMacroName(String macroName) {
        return macroName.equals("SCRIPTLER");
    }

}
