package com.wolf.backend.controller;

import com.wolf.backend.entity.AiCharacter;
import com.wolf.backend.service.CharacterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 对应文档17节 GET /api/characters */
@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @GetMapping
    public List<AiCharacter> list(@RequestParam(required = false) String tierKey,
                                   @RequestParam(required = false) Integer status) {
        return characterService.list(tierKey, status);
    }
}
