package com.verduleria.gestion.security.dto;

import java.util.List;

public record SessionResponse(String username, List<String> roles) {}
