package com.sinx.platform.member;

import com.sinx.platform.member.MemberAdminModels.BootstrapInput;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AdministratorBootstrap implements ApplicationRunner {
 private final Environment environment;
 private final MemberManagementService management;
 public AdministratorBootstrap(Environment environment,MemberManagementService management) {this.environment=environment;this.management=management;}
 @Override public void run(ApplicationArguments args) {
  String provider=setting("provider");
  if(provider.isBlank()) return;
  management.bootstrap(new BootstrapInput(provider,setting("tenant-id"),setting("subject-id"),setting("display-name"),setting("member-id")));
 }
 private String setting(String key) {return environment.getProperty("sinx.bootstrap."+key,"");}
}
