package com.sinx.platform.auth;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
@Component
public class OAuthState {
 private static final String KEY=OAuthState.class.getName();
 private record Pending(String provider,String state,Instant expires) implements java.io.Serializable {}
 private final SecureRandom random=new SecureRandom(); private final Clock clock;
 public OAuthState(Clock clock) {this.clock=clock;}
 public String issue(HttpSession session,String provider) {
  byte[] bytes=new byte[32];random.nextBytes(bytes);
  String state=HexFormat.of().formatHex(bytes);
  synchronized(session) {session.setAttribute(KEY,new Pending(provider,state,clock.instant().plusSeconds(300)));}
  return state;
 }
 public void consume(HttpSession session,String provider,String state) {
  if(session==null) throw new LoginFailure("invalid_state");
  synchronized(session) {
   Object value=session.getAttribute(KEY);session.removeAttribute(KEY);
   if(!(value instanceof Pending p) || state==null || state.length()>128 || !p.provider().equals(provider)
    || !clock.instant().isBefore(p.expires()) || !MessageDigest.isEqual(p.state().getBytes(StandardCharsets.UTF_8),state.getBytes(StandardCharsets.UTF_8))) throw new LoginFailure("invalid_state");
  }
 }
}
