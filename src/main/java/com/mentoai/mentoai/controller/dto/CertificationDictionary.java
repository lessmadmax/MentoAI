package com.mentoai.mentoai.controller.dto;

import java.util.List;

public record CertificationDictionary(
        List<Item> items
) {
    public record Item(String name, List<String> roles) {
    }
}




