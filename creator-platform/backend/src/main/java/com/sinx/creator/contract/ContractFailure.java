package com.sinx.creator.contract;

import org.springframework.http.HttpStatus;

public class ContractFailure extends RuntimeException {
    final HttpStatus status;
    final String code;
    ContractFailure(HttpStatus status,String code) { super(code);this.status=status;this.code=code; }
    static ContractFailure invalid() { return new ContractFailure(HttpStatus.BAD_REQUEST,"contract_invalid"); }
    static ContractFailure conflict() { return new ContractFailure(HttpStatus.CONFLICT,"contract_conflict"); }
    static ContractFailure missing() { return new ContractFailure(HttpStatus.NOT_FOUND,"contract_not_found"); }
    static ContractFailure unavailable() { return new ContractFailure(HttpStatus.SERVICE_UNAVAILABLE,"ess_unavailable"); }
}
