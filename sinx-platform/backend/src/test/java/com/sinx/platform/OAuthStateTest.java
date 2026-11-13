package com.sinx.platform;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import com.sinx.platform.auth.OAuthState;
import com.sinx.platform.auth.LoginFailure;
import static org.assertj.core.api.Assertions.*;
class OAuthStateTest {
 @Test void stateUsesUnpredictableAlphanumericValuesAcceptedBySilentAuthorization() {
  var states=new OAuthState(Clock.systemUTC());
  String first=states.issue(new MockHttpSession(),"wecom");
  String second=states.issue(new MockHttpSession(),"wecom");
  assertThat(first).matches("[0-9a-f]{64}");
  assertThat(second).matches("[0-9a-f]{64}").isNotEqualTo(first);
 }
 @Test void stateExpiresAtFiveMinutes() {
  var session=new MockHttpSession();var now=Instant.parse("2026-11-13T00:00:00Z");
  String state=new OAuthState(Clock.fixed(now,ZoneOffset.UTC)).issue(session,"feishu");
  assertThatThrownBy(()->new OAuthState(Clock.fixed(now.plusSeconds(300),ZoneOffset.UTC)).consume(session,"feishu",state)).isInstanceOf(LoginFailure.class);
 }
 @Test void stateCannotCrossProviders() {
  var states=new OAuthState(Clock.systemUTC());var session=new MockHttpSession();String state=states.issue(session,"feishu");
  assertThatThrownBy(()->states.consume(session,"wecom",state)).isInstanceOf(LoginFailure.class);
  assertThatThrownBy(()->states.consume(session,"feishu",state)).isInstanceOf(LoginFailure.class);
 }
 @Test void newAttemptInvalidatesPreviousAttempt() {
  var states=new OAuthState(Clock.systemUTC());var session=new MockHttpSession();String old=states.issue(session,"feishu");
  states.issue(session,"feishu");assertThatThrownBy(()->states.consume(session,"feishu",old)).isInstanceOf(LoginFailure.class);
 }
}
