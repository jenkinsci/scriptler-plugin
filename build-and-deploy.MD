- We don't have the infrastructure for Java maven builds in Jenkins. 
And there is no reason to create it: this is a one time thing. 
- We could easily do it in drone but we do not anticipate any changes in this repo.
Therefore, the local IntelliJ builds should suffice. Also, the previosuly built artifacts are in
the github repo's target directory. And deployed into zerofox maven repo, which is s3://zerofox-maven-artifacts
Latest plugin build is
  s3://zf-maven-artifacts/scriptler-plugin/org/jenkins-ci/plugins/scriptler/fix-editing-permissions/scriptler-fix-editing-permissions.hpi

- Checkout this repo, set the project JRE to Java 11 (install if necessary) and
run maven's `install` target.

- This should create the hpi and jar files in 
  - your project `target` directory
  - your local maven repository
  
```bash
[INFO] --- install:3.1.1:install (default-install) @ scriptler ---
[INFO] Installing /Users/ynakshin/code/JAVA/scriptler-plugin/target/scriptler-fix-editing-permissions.pom to /Users/ynakshin/.m2/repository/org/jenkins-ci/plugins/scriptler/fix-editing-permissions/scriptler-fix-editing-permissions.pom
[INFO] Installing /Users/ynakshin/code/JAVA/scriptler-plugin/target/scriptler.hpi to /Users/ynakshin/.m2/repository/org/jenkins-ci/plugins/scriptler/fix-editing-permissions/scriptler-fix-editing-permissions.hpi
[INFO] Installing /Users/ynakshin/code/JAVA/scriptler-plugin/target/scriptler.jar to /Users/ynakshin/.m2/repository/org/jenkins-ci/plugins/scriptler/fix-editing-permissions/scriptler-fix-editing-permissions.jar
```
-
   - s3 zerofox maven repository 




- scp or aws cp the hpi file to Jenkins master node, put it into the $JENKINS_HOME/plugins directory
- reload Jenkins config from disk or restart Jenkins systemd service

https://nuvalence.io/insights/using-an-s3-bucket-as-a-maven-repository/
https://tech.asimio.net/2018/06/27/Using-an-AWS-S3-Bucket-as-your-Maven-Repository.html
https://github.com/riskive/ctc-osis/blob/f3a373d21cc9979e87afc1468142c0c07a334bba/build.gradle#L100
https://github.com/riskive/ctc-osis/blob/zf-release-master/.drone.yml#L6
https://stackoverflow.com/a/14013645