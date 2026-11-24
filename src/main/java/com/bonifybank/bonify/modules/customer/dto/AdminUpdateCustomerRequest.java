package com.bonifybank.bonify.modules.customer.dto;

public record AdminUpdateCustomerRequest (
        String role,
        Boolean active
){
}
