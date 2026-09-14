package com.wolf.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wolf.backend.entity.AiCharacter;
import com.wolf.backend.mapper.AiCharacterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CharacterService {

    private final AiCharacterMapper characterMapper;

    /** 对应文档17节 GET /api/characters，支持按tier_key/status筛选 */
    public List<AiCharacter> list(String tierKey, Integer status) {
        LambdaQueryWrapper<AiCharacter> query = new LambdaQueryWrapper<>();
        if (tierKey != null && !tierKey.isBlank()) {
            query.eq(AiCharacter::getTierKey, tierKey);
        }
        query.eq(AiCharacter::getStatus, status != null ? status : 1);
        return characterMapper.selectList(query);
    }

    public AiCharacter get(Long id) {
        return characterMapper.selectById(id);
    }
}
