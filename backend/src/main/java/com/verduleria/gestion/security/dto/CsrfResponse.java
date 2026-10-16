package com.verduleria.gestion.security.dto;

public record CsrfResponse(String headerName, String parameterName, String token) {}
