package com.sinx.creator.contract;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ContractWorker {
    private final ContractService service;
    public ContractWorker(ContractService service) { this.service=service; }
    @Scheduled(fixedDelayString="${creator.ess.poll-interval:15000}",initialDelay=15000)
    public void resume() {
        if(!service.settings.ready()) return;
        var ids=service.jdbc.query("SELECT id FROM creator_contract WHERE status IN ('PREPARING','SIGNING','FILLING') ORDER BY CASE WHEN status='PREPARING' THEN 0 ELSE 1 END,updated_at,id LIMIT 10",(rs,n)->rs.getString(1));
        for(String id:ids) try { service.sync(id); } catch(ContractFailure ignored) { /* Public error code is retained, never log credentials or payloads. */ }
    }
}
