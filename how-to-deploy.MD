- We don't have the infrastructure for Java maven builds. 
So, the local IntelliJ builds should suffice. Also, the previosuly built artifacts are in
the github repo target directory

- Checkout this repo, set the project JRE to Java 11 (install if necessary) and
run maven's `install` target.

- This should create the hpi and jar files in your local maven repository. Like so
```bash
[INFO] --- install:3.1.1:install (default-install) @ scriptler ---
[INFO] Installing /Users/ynakshin/code/JAVA/scriptler-plugin/target/scriptler-fix-editing-permissions.pom to /Users/ynakshin/.m2/repository/org/jenkins-ci/plugins/scriptler/fix-editing-permissions/scriptler-fix-editing-permissions.pom
[INFO] Installing /Users/ynakshin/code/JAVA/scriptler-plugin/target/scriptler.hpi to /Users/ynakshin/.m2/repository/org/jenkins-ci/plugins/scriptler/fix-editing-permissions/scriptler-fix-editing-permissions.hpi
[INFO] Installing /Users/ynakshin/code/JAVA/scriptler-plugin/target/scriptler.jar to /Users/ynakshin/.m2/repository/org/jenkins-ci/plugins/scriptler/fix-editing-permissions/scriptler-fix-editing-permissions.jar
```

- scp the hpi file to Jenkins master put it into the $JENKINS_HOME/plugins directory
- reload Jenkins config from disk or restart Jenkins systemd service
- 