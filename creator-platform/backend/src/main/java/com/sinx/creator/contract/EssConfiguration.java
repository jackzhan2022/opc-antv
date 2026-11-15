package com.sinx.creator.contract;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(EssConfiguration.Settings.class)
public class EssConfiguration {
    @ConfigurationProperties("creator.ess")
    public record Settings(@DefaultValue("false") boolean enabled,@DefaultValue("test") String environment,
        @DefaultValue("") String secretId,@DefaultValue("") String secretKey,@DefaultValue("") String operatorId,
        @DefaultValue("") String companyName,@DefaultValue("") String signerId,
        @DefaultValue("") String callbackToken,@DefaultValue("") String callbackKey) {
        public boolean ready() {
            return enabled && (environment.equals("test")||environment.equals("online")) &&
                !secretId.isBlank()&&!secretKey.isBlank()&&!operatorId.isBlank()&&!companyName.isBlank()&&!signerId.isBlank()&&
                !callbackToken.isBlank() && callbackKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length==32;
        }
        void requireReady() { if(!ready()) throw new ContractFailure(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"ess_not_configured"); }
    }
}
