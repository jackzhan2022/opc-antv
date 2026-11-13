package com.sinx.platform;

import com.sinx.platform.member.MemberManagementService;
import com.sinx.platform.member.MemberAdminModels.BootstrapInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:bootstrap;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
 "sinx.auth.feishu.tenant-id=company",
 "sinx.bootstrap.provider=feishu","sinx.bootstrap.tenant-id=company",
 "sinx.bootstrap.subject-id=bootstrap-admin","sinx.bootstrap.display-name=初始管理员"
})
class AdministratorBootstrapTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired MemberManagementService management;
 @Test void startupPrelinksAdministratorExactlyOnceAndNeverRegrantsAfterward() {
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_member WHERE platform_role='ADMIN'",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM external_identity WHERE provider='feishu' AND subject_id='bootstrap-admin'",Integer.class)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_audit WHERE action='ADMIN_BOOTSTRAPPED'",Integer.class)).isEqualTo(1);
  jdbc.update("UPDATE platform_member SET platform_role='MEMBER'");
  management.bootstrap(new BootstrapInput("feishu","company","bootstrap-admin","初始管理员",null));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_member WHERE platform_role='ADMIN'",Integer.class)).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM member_audit",Integer.class)).isEqualTo(1);
 }
}
