# CHANGELOG

Attention: this is a fork from https://github.com/jenkinsci/scriptler-plugin
It fixes the problem with only Jenkins Admins being able to edit the jobs 
with a scriplter while regular users get an exception
```bash
java.lang.NullPointerException
	at java.base/java.util.ArrayList.<init>(ArrayList.java:179)
	at org.jenkinsci.plugins.scriptler.builder.ScriptlerBuilder.<init>(ScriptlerBuilder.java:89)
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
	at java.base/jdk.internal.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)
	at java.base/jdk.internal.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
	at java.base/java.lang.reflect.Constructor.newInstance(Constructor.java:490)
	at org.kohsuke.stapler.RequestImpl.invokeConstructor(RequestImpl.java:604)
	at org.kohsuke.stapler.RequestImpl.instantiate(RequestImpl.java:883)
	at org.kohsuke.stapler.RequestImpl$TypePair.convertJSON(RequestImpl.java:768)
	at org.kohsuke.stapler.RequestImpl.bindJSON(RequestImpl.java:551)
	at org.kohsuke.stapler.RequestImpl.instantiate(RequestImpl.java:877)
	at org.kohsuke.stapler.RequestImpl$TypePair.convertJSON(RequestImpl.java:768)
	at org.kohsuke.stapler.RequestImpl.bindJSON(RequestImpl.java:551)
	at org.kohsuke.stapler.RequestImpl.bindJSON(RequestImpl.java:546)
	at hudson.model.Descriptor.bindJSON(Descriptor.java:623)
	at hudson.model.Descriptor.newInstance(Descriptor.java:593)
Caused: java.lang.LinkageError: Failed to instantiate class org.biouno.unochoice.model.ScriptlerScript from {"value":"1","scriptlerBuilder":{"backupJobName":"repo","builderId":"1701112116349_2","scriptlerScriptId":"get-tags-heads.groovy","propagateParams":true,"defineParams":{"parameters":{"name":"repo","value":"git@github.com:riskive/datasource-sdk"}}},"isSandboxed":false,"stapler-class":"org.biouno.unochoice.model.ScriptlerScript","$class":"org.biouno.unochoice.model.ScriptlerScript"}
	at hudson.model.Descriptor.newInstance(Descriptor.java:596)

```

## Newer releases

See [GitHub Releases](https://github.com/jenkinsci/scriptler-plugin/releases)

## 3.1

Release date: _Sep. 27, 2019_

-   [PR #35](https://github.com/jenkinsci/scriptler-plugin/pull/35) Close
    `FileReader` resources when reading in script from disk (thanks
    [@pault1337](https://github.com/pault1337)!)

## 3.0-alpha 

Release date: _Oct. 10, 2018_

This release is available via the experimental update
center: <https://jenkins.io/doc/developer/publishing/releasing-experimental-updates/>

-   [JENKINS-44242](https://issues.jenkins-ci.org/browse/JENKINS-44242) Persistent
    cross-site scripting
-   [JENKINS-44243](https://issues.jenkins-ci.org/browse/JENKINS-44243) Script
    management vulnerable to Cross-Site Request Forgery attacks
-   [JENKINS-44245](https://issues.jenkins-ci.org/browse/JENKINS-44245) Scriptler
    Plugin allows any Scriptler script to be executed as build step  

## 2.9

Release date: _Oct. 28, 2015_

-   [JENKINS-29332](https://issues.jenkins-ci.org/browse/JENKINS-29332) disabled
    scritplerweb script catalog
-   fix NPE when uploading a script [PR
    \#22](https://github.com/jenkinsci/scriptler-plugin/pull/22)

## 2.7

Release date: _Feb 22, 2014_

-   fixed
    [JENKINS-19988](https://issues.jenkins-ci.org/browse/JENKINS-19988)
    Changes to script parameters in Run Script window affect permanent
    definitions
-   integrated [PR
    \#17](https://github.com/jenkinsci/scriptler-plugin/pull/17) Pass
    current build to SCRIPTLER token macro (thanks to Andreas Gudian)
-   integrated [PR
    \#16](https://github.com/jenkinsci/scriptler-plugin/pull/16) Add
    simple size-limited cache to avoid parsing of unchanged scripts
    (thanks to Andreas Gudian)
-   integrated [PR
    \#15](https://github.com/jenkinsci/scriptler-plugin/pull/15) [JENKINS-14964](https://issues.jenkins-ci.org/browse/JENKINS-14964)
    Allow running scripts using the REST API (thanks to Andreas Gudian)

## 2.6.1

Release date: _May 19, 2013_

-   Implement
    [JENKINS-17708](https://issues.jenkins-ci.org/browse/JENKINS-17708)
    Expose scriptler scripts via token macro token

## 2.6

Release date: _May 5, 2013_

-   fix
    [JENKINS-16047](https://issues.jenkins-ci.org/browse/JENKINS-16047)
    Scriptler plugin does not show Error/Exceptions anymore
-   fix
    [JENKINS-17259](https://issues.jenkins-ci.org/browse/JENKINS-17259)
    don't fail if parameters can't be expanded
-   integrate [pull request
    \#13](https://github.com/jenkinsci/scriptler-plugin/pull/13) Expose
    build, launcher, listener to groovy scripts when run on the master
    node
-   fix some image/icon references

## 2.5.1

Release date: _Nov 20, 2012_

-   fix dependency to git-server plugin - this is a mandatory
    depedendency now

## 2.5

Release date: _Nov 7, 2012_

-   implement
    [JENKINS-15276](https://issues.jenkins-ci.org/browse/JENKINS-15276)
    Store Groovy Scripts into a local VCS
-   implement
    [JENKINS-13468](https://issues.jenkins-ci.org/browse/JENKINS-13468)
    Would like to create/use a central "catalog" . . . 
-   Scriptler now understands the format for shared scripts also when
    first time pushed via git into Scriptler (Format description:
    <https://github.com/jenkinsci/jenkins-scripts/tree/master/scriptler#scriptler-scripts>
    )
-   now depends on the [Git Server
    Plugin](https://wiki.jenkins.io/display/JENKINS/Git+Server+Plugin)

## 2.4.1

Release date: _Oct 11, 2012_

-   fix issue when Jenkins is running with a different root context -
    details of scripts could not be opened

## 2.4

Release date: _Aug 31, 2012_

-   [Pull \#6](https://github.com/jenkinsci/scriptler-plugin/pull/6)
    Enable shortcut key
-   [Pull \#9](https://github.com/jenkinsci/scriptler-plugin/pull/9) Fix
    serialization issue and NPE

## 2.3

Release date: _June 24, 2012_

-   [Pull \#8](https://github.com/jenkinsci/scriptler-plugin/pull/8) /
    [JENKINS-13979](https://issues.jenkins-ci.org/browse/JENKINS-13979)
    allow build step to fail build by using boolean return values
-   allow to propagate job parameters into builder execution

## 2.2.1

Release date: _April 27, 2012_

-   [JENKINS-13518](https://issues.jenkins-ci.org/browse/JENKINS-13518)
    Wrong JSON syntax

## 2.2

Release date: _Mar. 9, 2012_

-   add a builder, to enable scheduling of scripts
-   add Japanese localization (thanks to ikikko!)
-   fix
    [JENKINS-10839](https://issues.jenkins-ci.org/browse/JENKINS-10839)
    support HTML output

## 2.1

Release date: _Feb. 21, 2012_

-   fix [JENKINS-12748](https://issues.jenkins-ci.org/browse/JENKINS-12748) -
    Scriptler remote catalog breaks when script name contains certain
    characters
-   fix [JENKINS-12750](https://issues.jenkins-ci.org/browse/JENKINS-12750) -
    Scriptler 2.0 breaks cc.xml-View for Anonymous User

## 2.0

Release date: _Jan. 29, 2012_

-   integrate [pull
    \#2](https://github.com/jenkinsci/scriptler-plugin/pull/2): allow
    users with permission "RunScripts" to run scripts in scriptler
    (thanks to lvotypko)
-   integrate [pull
    \#5](https://github.com/jenkinsci/scriptler-plugin/pull/5): add
    'all' and 'all slaves' to the options where to run the script
    (thanks to eciramella)
-   intergate [pull
    \#1](https://github.com/jenkinsci/scriptler-plugin/pull/1): ensure
    links open in new windows (thanks to bap2000)
-   enable script sharing with github:
    <https://github.com/jenkinsci/jenkins-scripts> (2. catalog)
-   enable passing parameters to scripts
-   make it configurable whether users with "RunScript" permission can
    change a script before execution

## 1.5

Release date: _April 16, 2011_

-   fix
    [JENKINS-9302](https://issues.jenkins-ci.org/browse/JENKINS-9302) -
    allow to disable remote script download functionality
-   fix
    [JENKINS-9130](https://issues.jenkins-ci.org/browse/JENKINS-9130) -
    Add a dynamic parser to colorize and indent groovy textareas

## 1.4

Release date: _Oct. 11, 2010_

-   enable script sharing with
    [http://scriptlerweb.appspot.com](http://scriptlerweb.appspot.com/)

## 1.2

Release date: _Sep 15, 2010_

-   fix [JENKINS-7424](http://issues.jenkins-ci.org/browse/JENKINS-7424)

## 1.0

-   Inital (with suport for a static catalog)
