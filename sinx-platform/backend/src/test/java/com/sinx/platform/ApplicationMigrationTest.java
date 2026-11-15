package com.sinx.platform;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ApplicationMigrationTest {
 @Test void migrationPreservesEnabledMembersOnceAndNeverAutoGrantsNewMembers() throws Exception {
  String url="jdbc:h2:mem:application_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
  Flyway.configure().dataSource(url,"sa","").target("3").load().migrate();
  try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
   sql.executeUpdate("INSERT INTO platform_member(id,display_name,enabled) VALUES ('existing','既有成员',true),('disabled','停用成员',false)");
   Flyway.configure().dataSource(url,"sa","").load().migrate();
   try(var rows=sql.executeQuery("SELECT member_id FROM member_application_access WHERE granted=true")) {assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("existing");assertThat(rows.next()).isFalse();}
   sql.executeUpdate("INSERT INTO platform_member(id,display_name) VALUES ('new-member','新成员')");
   Flyway.configure().dataSource(url,"sa","").load().migrate();
   try(var rows=sql.executeQuery("SELECT COUNT(*) FROM member_application_access WHERE member_id='new-member'")) {rows.next();assertThat(rows.getInt(1)).isZero();}
  }
 }
}
