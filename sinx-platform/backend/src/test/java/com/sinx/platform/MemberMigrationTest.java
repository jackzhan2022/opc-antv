package com.sinx.platform;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MemberMigrationTest {
 @Test void upgradingAutoRegisteredAccountsPreservesMembersAndRequiresExplicitApproval() throws Exception {
  String url="jdbc:h2:mem:upgrade_policy;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
  Flyway.configure().dataSource(url,"sa","").target("1").load().migrate();
  try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
   sql.executeUpdate("INSERT INTO platform_member(id,display_name) VALUES ('legacy-member','既有成员')");
   sql.executeUpdate("INSERT INTO external_identity(provider,tenant_id,subject_id,member_id) VALUES ('wecom','corp','LegacyUser','legacy-member')");
   Flyway.configure().dataSource(url,"sa","").load().migrate();
   try(var rows=sql.executeQuery("SELECT COUNT(*) FROM platform_member WHERE id='legacy-member'")) {rows.next();assertThat(rows.getInt(1)).isEqualTo(1);}
   try(var rows=sql.executeQuery("SELECT COUNT(*) FROM external_identity")) {rows.next();assertThat(rows.getInt(1)).isZero();}
   try(var rows=sql.executeQuery("SELECT subject_id,display_name FROM pending_identity")) {assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("LegacyUser");assertThat(rows.getString(2)).isEqualTo("既有成员");}
  }
 }
}
