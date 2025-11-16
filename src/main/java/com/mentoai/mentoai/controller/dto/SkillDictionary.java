package com.mentoai.mentoai.controller.dto;

import java.util.List;
import java.util.Map;

public record SkillDictionary(
        Map<String, List<String>> aliases,
        Map<String, List<String>> groups
) {
}


